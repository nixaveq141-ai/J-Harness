package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.process.LocalProcessRunner;
import io.jharness.process.ProcessLimits;
import io.jharness.workspace.LocalWorkspace;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** JSON worker runs inside the selected container, not in the host filesystem. */
public final class EnvironmentWorker {
    private EnvironmentWorker() {}

    public static void main(String[] argv) throws Exception {
        if (argv.length == 1 && argv[0].equals("--idle")) {
            while (true) Thread.sleep(Duration.ofHours(1));
        }
        if (argv.length != 1) throw new IllegalArgumentException("Workspace root required");
        Map<String, Object> response;
        WorkspaceSearchFilter acknowledged = null;
        boolean dispatched = false;
        try {
            var request = BoundedJson.decode(System.in.readNBytes(BoundedJson.LIMIT + 1));
            var filter = WorkspaceSearchFilter.from(request.getOrDefault("searchFilter", Map.of()));
            acknowledged = filter;
            validate(request);
            dispatched = true;
            response =
                    new LinkedHashMap<>(
                            dispatch(new LocalWorkspace(Path.of(argv[0]), filter), request));
        } catch (Exception ex) {
            response =
                    Map.of(
                            "error",
                            ex instanceof HarnessException h ? h.code() : "WORKER_IO",
                            "executionRejected",
                            !dispatched);
        }
        if (acknowledged != null) {
            response = new LinkedHashMap<>(response);
            response.put("searchFilter", acknowledged.configuration());
        }
        System.out.write(BoundedJson.encode(response));
        System.out.flush();
    }

    public static Map<String, Object> dispatch(WorkspaceFiles files, Map<String, Object> request)
            throws IOException {
        validate(request);
        String op = (String) request.get("op");
        var path = new ExecPath((String) request.getOrDefault("path", "."));
        Map<String, Object> value =
                switch (op) {
                    case "filter-info" -> Map.of();
                    case "read" -> {
                        var snapshot =
                                files.readSnapshot(path, number(request, "maxBytes", 1024 * 1024));
                        yield Map.of("text", snapshot.text(), "version", snapshot.version());
                    }
                    case "version" -> Map.of("version", files.versionOf(path));
                    case "page" ->
                            files.readPage(
                                    path,
                                    number(request, "start", 1),
                                    number(request, "end", 200),
                                    CancellationToken.NONE);
                    case "list" ->
                            files.list(
                                    path,
                                    number(request, "depth", 1),
                                    number(request, "limit", 100),
                                    CancellationToken.NONE);
                    case "search" ->
                            files.search(
                                    (String) request.get("glob"),
                                    number(request, "limit", 100),
                                    CancellationToken.NONE);
                    case "grep" ->
                            files.grep(
                                    (String) request.get("literal"),
                                    ((List<?>) request.get("paths"))
                                            .stream()
                                                    .map(String.class::cast)
                                                    .map(ExecPath::new)
                                                    .toList(),
                                    number(request, "limit", 100),
                                    CancellationToken.NONE);
                    case "write" ->
                            change(
                                    files.write(
                                            path,
                                            (String) request.get("content"),
                                            Boolean.TRUE.equals(request.get("overwrite")),
                                            (String) request.get("expectedVersion"),
                                            CancellationToken.NONE));
                    case "edit" ->
                            change(
                                    files.edit(
                                            path,
                                            (String) request.get("oldText"),
                                            (String) request.get("newText"),
                                            number(request, "occurrences", 1),
                                            (String) request.get("expectedVersion"),
                                            CancellationToken.NONE));
                    case "patch" -> {
                        Map<String, String> versions = new LinkedHashMap<>();
                        Values.object(request.get("versions"))
                                .forEach((key, valueText) -> versions.put(key, (String) valueText));
                        yield files.applyPatch(
                                (String) request.get("patch"), versions, CancellationToken.NONE);
                    }
                    case "diff" ->
                            Map.of(
                                    "diff",
                                    files.gitDiff(
                                            (String) request.get("scope"), CancellationToken.NONE));
                    case "command" -> {
                        if (!(files instanceof LocalWorkspace local))
                            throw new HarnessException(
                                    "UNSUPPORTED_ENVIRONMENT_OPERATION", "Local worker required");
                        try (var runner =
                                new LocalProcessRunner(
                                        local,
                                        ProcessLimits.defaults(),
                                        LocalProcessRunner.buildEnvironment(),
                                        List.of())) {
                            var command =
                                    new CommandRequest(
                                            (String) request.get("callId"),
                                            CommandRequest.Mode.valueOf(
                                                    (String) request.get("mode")),
                                            ((List<?>) request.get("argv"))
                                                    .stream().map(String.class::cast).toList(),
                                            path,
                                            Duration.ofMillis(
                                                    Values.integer(
                                                            request.get("timeoutMs"),
                                                            "WORKER_INVALID_REQUEST")));
                            var result = runner.execute(command, CancellationToken.NONE);
                            yield Map.of(
                                    "status",
                                    result.status().name(),
                                    "value",
                                    result.value(),
                                    "modelContent",
                                    result.modelContent(),
                                    "display",
                                    result.display());
                        }
                    }
                    default ->
                            throw new HarnessException(
                                    "UNSUPPORTED_ENVIRONMENT_OPERATION",
                                    "Unknown worker operation");
                };
        return Map.of("value", value);
    }

    private static void validate(Map<String, Object> request) {
        if (!(request.get("op") instanceof String))
            throw BoundedJson.error("WORKER_INVALID_REQUEST");
        for (String key :
                List.of(
                        "path",
                        "glob",
                        "literal",
                        "content",
                        "expectedVersion",
                        "oldText",
                        "newText",
                        "scope",
                        "callId",
                        "mode",
                        "patch"))
            if (request.containsKey(key)
                    && !(key.equals("expectedVersion") && request.get(key) == null)
                    && !(request.get(key) instanceof String))
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
        for (String key :
                List.of("start", "end", "depth", "limit", "occurrences", "timeoutMs", "maxBytes"))
            if (request.containsKey(key))
                Values.integer(request.get(key), "WORKER_INVALID_REQUEST");
        for (String key : List.of("argv", "paths"))
            if (request.containsKey(key)
                    && (!(request.get(key) instanceof List<?> values)
                            || values.stream().anyMatch(v -> !(v instanceof String))))
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
        if (request.containsKey("maxBytes")) {
            int maxBytes = Values.integer(request.get("maxBytes"), "WORKER_INVALID_REQUEST");
            if (maxBytes < 1 || maxBytes > 1024 * 1024)
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
        }
        if (request.containsKey("overwrite") && !(request.get("overwrite") instanceof Boolean))
            throw BoundedJson.error("WORKER_INVALID_REQUEST");
        String op = (String) request.get("op");
        List<String> required =
                switch (op) {
                    case "search" -> List.of("glob");
                    case "grep" -> List.of("literal", "paths");
                    case "write" -> List.of("content");
                    case "edit" -> List.of("oldText", "newText");
                    case "patch" -> List.of("patch", "versions");
                    case "diff" -> List.of("scope");
                    case "command" -> List.of("callId", "mode", "argv", "timeoutMs");
                    default -> List.of();
                };
        if (!request.keySet().containsAll(required))
            throw BoundedJson.error("WORKER_INVALID_REQUEST");
        if (op.equals("command")) {
            try {
                new CommandRequest(
                        (String) request.get("callId"),
                        CommandRequest.Mode.valueOf((String) request.get("mode")),
                        ((List<?>) request.get("argv")).stream().map(String.class::cast).toList(),
                        new ExecPath((String) request.getOrDefault("path", ".")),
                        Duration.ofMillis(
                                Values.integer(
                                        request.get("timeoutMs"), "WORKER_INVALID_REQUEST")));
            } catch (IllegalArgumentException ex) {
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
            }
        }
        if (op.equals("patch")) {
            if (!(request.get("versions") instanceof Map<?, ?>))
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
            var versions = Values.object(request.get("versions"));
            if (versions.values().stream().anyMatch(v -> !(v instanceof String)))
                throw BoundedJson.error("WORKER_INVALID_REQUEST");
        }
    }

    static Map<String, Object> change(FileSystemView.FileChange change) {
        return Map.of(
                "path",
                change.path().value(),
                "beforeVersion",
                change.beforeVersion(),
                "afterVersion",
                change.afterVersion(),
                "diff",
                change.diff(),
                "metadata",
                change.metadata());
    }

    private static int number(Map<String, Object> map, String key, int fallback) {
        return Values.integer(map.getOrDefault(key, fallback), "WORKER_INVALID_REQUEST");
    }
}
