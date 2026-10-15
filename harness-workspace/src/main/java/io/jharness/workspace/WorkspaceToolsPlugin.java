package io.jharness.workspace;

import io.jharness.api.*;
import java.io.IOException;
import java.util.*;

/** File tools are only facades over the injected execution-world filesystem service. */
public final class WorkspaceToolsPlugin implements HarnessPlugin {
    private final WorkspaceFiles files;
    private final ArtifactStore artifacts;
    private final ProcessRunner processes;
    private final TerminalSession terminal;
    private ExecutionEnvironment environment;

    public WorkspaceToolsPlugin(WorkspaceFiles files, ArtifactStore artifacts) {
        this(files, artifacts, null);
    }

    public WorkspaceToolsPlugin(
            WorkspaceFiles files, ArtifactStore artifacts, ProcessRunner processes) {
        this(files, artifacts, processes, null);
    }

    public WorkspaceToolsPlugin(
            WorkspaceFiles files,
            ArtifactStore artifacts,
            ProcessRunner processes,
            TerminalSession terminal) {
        this.terminal = terminal;
        this.files = Objects.requireNonNull(files);
        this.artifacts = Objects.requireNonNull(artifacts);
        this.processes = processes;
    }

    public WorkspaceToolsPlugin(ExecutionEnvironment environment, ArtifactStore artifacts) {
        this(
                requireWorkspace(environment),
                artifacts,
                environment.processes(),
                environment.terminal());
        this.environment = Objects.requireNonNull(environment);
    }

    private static WorkspaceFiles requireWorkspace(ExecutionEnvironment environment) {
        if (!(environment.files() instanceof WorkspaceFiles files))
            throw new HarnessException(
                    "UNSUPPORTED_WORKSPACE", "Environment must supply workspace file capabilities");
        return files;
    }

    @Override
    public String id() {
        return "workspace-local";
    }

    @Override
    public String version() {
        return "1.2";
    }

    @Override
    public void start(PluginContext context) {
        context.register(WorkspaceFiles.class, "default", files);
        context.register(FileSystemView.class, "default", files);
        context.register(
                ExecutionEnvironment.class,
                environment == null ? "local" : environment.id(),
                environment == null
                        ? new ExecutionEnvironment() {
                            @Override
                            public String id() {
                                return "local";
                            }

                            @Override
                            public FileSystemView files() {
                                return files;
                            }

                            @Override
                            public ProcessRunner processes() {
                                if (processes != null) return processes;
                                throw new HarnessException(
                                        "UNSUPPORTED", "No process provider configured");
                            }

                            @Override
                            public TerminalSession terminal() {
                                if (terminal != null) return terminal;
                                throw new HarnessException(
                                        "UNSUPPORTED_TERMINAL", "No terminal provider configured");
                            }
                        }
                        : environment);
        ToolRegistry registry =
                context.services().requireUnique(ToolRegistry.class, context.scope());
        for (String name :
                List.of(
                        "list_directory",
                        "read_file",
                        "search_files",
                        "grep",
                        "write_file",
                        "edit_file",
                        "apply_patch",
                        "git_diff",
                        "read_artifact"))
            context.own(registry.register(new FileTool(name, context.scope()), context.scope()));
    }

    @Override
    public void stop() {}

    private final class FileTool implements AgentTool {
        private final String name;
        private final PluginScope scope;

        FileTool(String name, PluginScope scope) {
            this.name = name;
            this.scope = scope;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return switch (name) {
                case "grep" ->
                        "Bounded literal-text search (not regex); default filtered project files.";
                case "write_file" ->
                        "Create or explicitly overwrite UTF-8 file; existing file requires read/version guard.";
                case "edit_file" ->
                        "Replace exact expected occurrences; requires prior read or expectedVersion.";
                case "read_artifact" ->
                        "Read a character page of a persisted artifact in this session.";
                default -> "Bounded workspace " + name + "; relative paths only.";
            };
        }

        @Override
        public SideEffectLevel sideEffectLevel() {
            return Set.of("write_file", "edit_file", "apply_patch").contains(name)
                    ? SideEffectLevel.WORKSPACE_WRITE
                    : SideEffectLevel.READ_ONLY;
        }

        @Override
        public Map<String, Object> inputSchema() {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            switch (name) {
                case "list_directory" -> {
                    properties.put("path", string());
                    properties.put("depth", integer(1, 16));
                    properties.put("limit", integer(1, 5000));
                }
                case "read_file" -> {
                    properties.put("path", string());
                    required.add("path");
                    properties.put("startLine", integer(1, 10000000));
                    properties.put("endLine", integer(1, 10000000));
                }
                case "search_files" -> {
                    properties.put("glob", string());
                    properties.put("limit", integer(1, 5000));
                }
                case "grep" -> {
                    properties.put("pattern", string());
                    required.add("pattern");
                    properties.put(
                            "paths", Map.of("type", "array", "items", string(), "maxItems", 1000));
                    properties.put("limit", integer(1, 1000));
                }
                case "write_file" -> {
                    properties.put("path", string());
                    properties.put("content", string());
                    required.addAll(List.of("path", "content"));
                    properties.put("overwrite", Map.of("type", "boolean"));
                    properties.put("expectedVersion", string());
                }
                case "edit_file" -> {
                    properties.put("path", string());
                    properties.put("oldText", string());
                    properties.put("newText", string());
                    required.addAll(List.of("path", "oldText", "newText"));
                    properties.put("expectedOccurrences", integer(1, 100000));
                    properties.put("expectedVersion", string());
                }
                case "git_diff" ->
                        properties.put(
                                "scope",
                                Map.of("type", "string", "enum", List.of("working", "staged")));
                case "apply_patch" -> {
                    properties.put("patchText", string());
                    required.add("patchText");
                    properties.put("expectedVersions", Map.of("type", "object"));
                }
                case "read_artifact" -> {
                    properties.put("id", string());
                    required.add("id");
                    properties.put("offset", integer(0, Integer.MAX_VALUE));
                    properties.put("limit", integer(1, 8192));
                }
                default -> throw new IllegalStateException();
            }
            return Map.of(
                    "type",
                    "object",
                    "properties",
                    properties,
                    "required",
                    required,
                    "additionalProperties",
                    false);
        }

        @Override
        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        @Override
        public ToolResult execute(ToolInvocation call, ToolContext context) throws IOException {
            if (!scope.equals(context.scope()))
                throw new HarnessException("SCOPE_DENIED", "File tool belongs to another agent");
            CancellationToken cancellation = context.cancellation();
            Map<String, Object> args = call.arguments();
            Map<String, Object> result;
            Map<String, Object> display = Map.of();
            try {
                switch (name) {
                    case "list_directory" ->
                            result =
                                    files.list(
                                            path(args, "."),
                                            number(args, "depth", 1),
                                            number(args, "limit", 1000),
                                            cancellation);
                    case "read_file" ->
                            result =
                                    files.readPage(
                                            path(args, null),
                                            number(args, "startLine", 1),
                                            number(
                                                    args,
                                                    "endLine",
                                                    number(args, "startLine", 1) + 199),
                                            cancellation);
                    case "search_files" ->
                            result =
                                    files.search(
                                            (String) args.getOrDefault("glob", "**/*"),
                                            number(args, "limit", 1000),
                                            cancellation);
                    case "grep" -> {
                        List<ExecPath> paths;
                        if (args.get("paths") instanceof List<?> list)
                            paths = list.stream().map(item -> new ExecPath((String) item)).toList();
                        else
                            paths =
                                    ((List<?>)
                                                    files.search("**/*", 1000, cancellation)
                                                            .get("paths"))
                                            .stream()
                                                    .map(item -> new ExecPath((String) item))
                                                    .toList();
                        result =
                                files.grep(
                                        (String) args.get("pattern"),
                                        paths,
                                        number(args, "limit", 100),
                                        cancellation);
                    }
                    case "write_file", "edit_file" -> {
                        FileSystemView.FileChange change =
                                name.equals("write_file")
                                        ? files.write(
                                                path(args, null),
                                                (String) args.get("content"),
                                                (Boolean) args.getOrDefault("overwrite", false),
                                                (String) args.get("expectedVersion"),
                                                cancellation)
                                        : files.edit(
                                                path(args, null),
                                                (String) args.get("oldText"),
                                                (String) args.get("newText"),
                                                number(args, "expectedOccurrences", 1),
                                                (String) args.get("expectedVersion"),
                                                cancellation);
                        result =
                                Map.of(
                                        "path",
                                        change.path().value(),
                                        "beforeVersion",
                                        change.beforeVersion(),
                                        "afterVersion",
                                        change.afterVersion(),
                                        "diff",
                                        change.diff());
                        display =
                                Map.of(
                                        "path",
                                        change.path().value(),
                                        "beforeVersion",
                                        change.beforeVersion(),
                                        "afterVersion",
                                        change.afterVersion());
                    }
                    case "git_diff" ->
                            result =
                                    Map.of(
                                            "diff",
                                            files.gitDiff(
                                                    (String) args.getOrDefault("scope", "working"),
                                                    cancellation),
                                            "source",
                                            "git");
                    case "apply_patch" -> {
                        Map<String, String> versions = new LinkedHashMap<>();
                        for (var entry :
                                Values.object(args.getOrDefault("expectedVersions", Map.of()))
                                        .entrySet()) {
                            if (!(entry.getValue() instanceof String text))
                                throw new HarnessException(
                                        "INVALID_PATCH", "Versions must be strings");
                            versions.put(entry.getKey(), text);
                        }
                        result =
                                files.applyPatch(
                                        (String) args.get("patchText"), versions, cancellation);
                        display =
                                Map.of(
                                        "changes",
                                        ((List<?>) result.get("changes"))
                                                .stream()
                                                        .map(
                                                                raw -> {
                                                                    var change = Values.object(raw);
                                                                    return Map.of(
                                                                            "path",
                                                                            change.get("path"),
                                                                            "beforeVersion",
                                                                            change.get(
                                                                                    "beforeVersion"),
                                                                            "afterVersion",
                                                                            change.get(
                                                                                    "afterVersion"));
                                                                })
                                                        .toList());
                        if (Boolean.TRUE.equals(result.get("partial")))
                            return new ToolResult(
                                    call.callId(),
                                    ToolStatus.UNKNOWN,
                                    result,
                                    result.toString(),
                                    display,
                                    Optional.of(
                                            new ToolResult.ErrorInfo(
                                                    "PATCH_PARTIAL",
                                                    "Some files committed; inspect changes before retry")));
                    }
                    case "read_artifact" ->
                            result =
                                    Map.of(
                                            "text",
                                            artifacts.read(
                                                    scope.sessionId(),
                                                    (String) args.get("id"),
                                                    number(args, "offset", 0),
                                                    number(args, "limit", 4096)));
                    default -> throw new IllegalStateException();
                }
            } catch (HarnessException ex) {
                if (!Set.of(
                                        "PATH_DENIED",
                                        "FILE_CONFLICT",
                                        "FILE_LIMIT",
                                        "FILE_EXISTS",
                                        "BINARY_FILE",
                                        "UNSUPPORTED_ENCODING",
                                        "EDIT_MATCH_CONFLICT",
                                        "INVALID_EDIT",
                                        "INVALID_RANGE",
                                        "NOT_TEXT_FILE",
                                        "FILE_NOT_FOUND")
                                .contains(ex.code())
                        && !Set.of(
                                        "INVALID_PATCH",
                                        "UNSUPPORTED_PATCH",
                                        "PATCH_CONFLICT",
                                        "PATCH_LIMIT")
                                .contains(ex.code())) throw ex;
                return ToolResults.failure(
                        call.callId(),
                        ex.code().equals("PATH_DENIED") ? ToolStatus.DENIED : ToolStatus.FAILED,
                        ex.code());
            }
            result = compact(result, scope.sessionId());
            return new ToolResult(
                    call.callId(),
                    ToolStatus.SUCCESS,
                    result,
                    result.toString(),
                    display,
                    Optional.empty());
        }
    }

    private Map<String, Object> compact(Map<String, Object> result, String sessionId)
            throws IOException {
        Map<String, Object> compact = new LinkedHashMap<>(result);
        for (String key : List.of("text", "diff"))
            if (compact.get(key) instanceof String text && text.length() > 8192) {
                ArtifactStore.Artifact artifact = artifacts.put(sessionId, text);
                compact.put(key, text.substring(0, 4096));
                compact.put("artifactRef", artifact.id());
                compact.put("artifactBytes", artifact.bytes());
                compact.put("artifactSha256", artifact.sha256());
                compact.put("outputTruncated", true);
                compact.put("readBackTool", "read_artifact");
            }
        String structured = compact.toString();
        if (structured.length() > 8192) {
            ArtifactStore.Artifact artifact = artifacts.put(sessionId, structured);
            return Map.of(
                    "preview",
                    structured.substring(0, 4096),
                    "artifactRef",
                    artifact.id(),
                    "artifactBytes",
                    artifact.bytes(),
                    "artifactSha256",
                    artifact.sha256(),
                    "outputTruncated",
                    true,
                    "readBackTool",
                    "read_artifact");
        }
        return Values.snapshot(compact);
    }

    private static ExecPath path(Map<String, Object> args, String fallback) {
        return new ExecPath((String) args.getOrDefault("path", fallback));
    }

    private static int number(Map<String, Object> args, String key, int fallback) {
        return ((Number) args.getOrDefault(key, fallback)).intValue();
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> integer(int min, int max) {
        return Map.of("type", "integer", "minimum", min, "maximum", max);
    }
}
