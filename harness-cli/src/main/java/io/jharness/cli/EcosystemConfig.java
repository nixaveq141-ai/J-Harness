package io.jharness.cli;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.llm.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Strict, host-authored ecosystem configuration. Never consumes model-supplied connection data. */
final class EcosystemConfig {
    private EcosystemConfig() {}

    static HarnessException invalid() {
        return new HarnessException(
                "INVALID_ECOSYSTEM_CONFIG", "Invalid trusted ecosystem configuration");
    }

    static void keys(Map<String, Object> map, String... allowed) {
        if (!Set.of(allowed).containsAll(map.keySet())) throw invalid();
    }

    static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.getOrDefault(key, fallback);
        if (!(value instanceof String s) || s.isBlank() || s.length() > 8192) throw invalid();
        return s;
    }

    static int number(Map<String, Object> map, String key, int fallback, int max) {
        Object value = map.getOrDefault(key, fallback);
        if (!(value instanceof Number n)
                || n.doubleValue() != n.intValue()
                || n.intValue() < 0
                || n.intValue() > max) throw invalid();
        return n.intValue();
    }

    static List<Map<String, Object>> entries(Map<String, Object> map, String key, int max) {
        Object raw = map.getOrDefault(key, List.of());
        if (!(raw instanceof List<?> list) || list.size() > max) throw invalid();
        return list.stream().map(Values::object).toList();
    }

    static List<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)
                || list.size() > 64
                || list.stream().anyMatch(v -> !(v instanceof String))) throw invalid();
        return list.stream().map(String.class::cast).toList();
    }

    static void validate(Map<String, Object> config) {
        keys(
                config,
                "environment",
                "mcp",
                "skills",
                "skillRoots",
                "subagents",
                "planning",
                "web",
                "webSources");
        var env = Values.object(config.getOrDefault("environment", Map.of()));
        keys(env, "kind", "dockerExecutable", "image", "workerJar");
        String kind = text(env, "kind", "local");
        if (!Set.of("local", "docker").contains(kind)) throw invalid();
        if (kind.equals("docker")) {
            for (String field : List.of("dockerExecutable", "image", "workerJar"))
                text(env, field, null);
            if (!text(env, "image", null).matches("[A-Za-z0-9./:_-]+@sha256:[0-9a-f]{64}"))
                throw invalid();
        } else if (env.size() > 1) throw invalid();
        var ids = new HashSet<String>();
        for (var mcp : entries(config, "mcp", 8)) {
            keys(
                    mcp,
                    "id",
                    "transport",
                    "command",
                    "cwd",
                    "url",
                    "tokenEnv",
                    "timeoutSeconds",
                    "effects");
            if (!text(mcp, "id", null).matches("[A-Za-z0-9_-]{1,16}")
                    || !ids.add(text(mcp, "id", null))) throw invalid();
            String transport = text(mcp, "transport", null);
            int timeout = number(mcp, "timeoutSeconds", 15, 60);
            if (timeout < 1) throw invalid();
            if (transport.equals("stdio")) {
                if (strings(mcp.get("command")).isEmpty()
                        || mcp.containsKey("url")
                        || mcp.containsKey("tokenEnv")) throw invalid();
            } else if (transport.equals("http")) {
                var uri = URI.create(text(mcp, "url", null));
                if (mcp.containsKey("command")
                        || mcp.containsKey("cwd")
                        || uri.getUserInfo() != null
                        || uri.getFragment() != null
                        || !("https".equals(uri.getScheme())
                                || "http".equals(uri.getScheme())
                                        && Set.of("localhost", "127.0.0.1", "[::1]")
                                                .contains(uri.getHost()))) throw invalid();
                if (mcp.containsKey("tokenEnv")
                        && !text(mcp, "tokenEnv", null).matches("[A-Za-z_][A-Za-z0-9_]*"))
                    throw invalid();
            } else throw invalid();
            Values.object(mcp.getOrDefault("effects", Map.of()))
                    .values()
                    .forEach(v -> SideEffectLevel.valueOf((String) v));
        }
        ids.clear();
        for (var root : entries(config, "skillRoots", 8)) {
            keys(root, "id", "kind", "directory");
            String id = text(root, "id", null), rootKind = text(root, "kind", null);
            if (!id.matches("[A-Za-z0-9_-]{1,16}")
                    || !ids.add(id)
                    || !Set.of("project", "user").contains(rootKind)) throw invalid();
            Path directory = Path.of(text(root, "directory", null));
            if (!directory.normalize().equals(directory)
                    || directory.isAbsolute() != rootKind.equals("user")
                    || rootKind.equals("project")
                            && (directory.getRoot() != null
                                    || directory.toString().contains(":")
                                    || directory.toString().startsWith("\\"))
                    || directory.toString().replace('\\', '/').matches("(?:.*?/)?\\.\\.(?:/.*)?"))
                throw invalid();
        }
        ids.clear();
        for (var skill : entries(config, "skills", 8)) {
            keys(skill, "id", "directory", "resources", "scripts");
            SkillCatalog.checkName(text(skill, "id", null));
            if (!ids.add(text(skill, "id", null))) throw invalid();
            spec(skill);
        }
        var children = Values.object(config.getOrDefault("subagents", Map.of()));
        if (children.size() > 16) throw invalid();
        children.forEach(
                (name, raw) -> {
                    if (!name.matches("[A-Za-z0-9_-]{1,32}")) throw invalid();
                    var child = Values.object(raw);
                    keys(child, "prompt", "tools", "requireRead", "maxSteps", "durationSeconds");
                    text(child, "prompt", "Read-only delegated investigation");
                    if (!Set.of(
                                    "list_directory",
                                    "read_file",
                                    "grep",
                                    "search_files",
                                    "read_artifact")
                            .containsAll(
                                    strings(child.getOrDefault("tools", List.of("read_file")))))
                        throw invalid();
                    strings(child.getOrDefault("requireRead", List.of()));
                    if (number(child, "maxSteps", 4, 64) < 1
                            || number(child, "durationSeconds", 30, 300) < 1) throw invalid();
                });
        if (config.containsKey("planning") && !(config.get("planning") instanceof Boolean))
            throw invalid();
        var web = Values.object(config.getOrDefault("web", Map.of()));
        keys(web, "port", "tokenEnv", "catalogId", "approvalSeconds", "maxTasks");
        number(web, "port", 0, 65535);
        if (!text(web, "tokenEnv", "JHARNESS_WEB_TOKEN").matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw invalid();
        if (!text(web, "catalogId", "web-catalog").matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        if (number(web, "approvalSeconds", 30, 60) < 1 || number(web, "maxTasks", 32, 32) < 1)
            throw invalid();
        new WebFetchPlugin(
                Values.object(config.getOrDefault("webSources", Map.of())).entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> URI.create((String) e.getValue()))));
    }

    static SkillsPlugin.Spec spec(Map<String, Object> map) {
        var resources = new LinkedHashMap<String, ExecPath>();
        Values.object(map.getOrDefault("resources", Map.of()))
                .forEach((k, v) -> resources.put(k, new ExecPath((String) v)));
        var scripts = new LinkedHashMap<String, SkillsPlugin.Script>();
        Values.object(map.getOrDefault("scripts", Map.of()))
                .forEach(
                        (k, raw) -> {
                            var script = Values.object(raw);
                            keys(script, "path", "mode", "timeoutSeconds");
                            scripts.put(
                                    k,
                                    new SkillsPlugin.Script(
                                            new ExecPath(text(script, "path", null)),
                                            CommandRequest.Mode.valueOf(text(script, "mode", null)),
                                            Duration.ofSeconds(
                                                    number(script, "timeoutSeconds", 10, 60))));
                        });
        var checked =
                new SkillsPlugin.Spec(
                        text(map, "id", null),
                        new ExecPath(text(map, "directory", null)),
                        resources,
                        scripts);
        SkillsPlugin.validatePaths(checked);
        return checked;
    }

    static ExecutionEnvironment environment(
            Map<String, Object> config, Path root, List<String> secrets)
            throws java.io.IOException {
        return environment(config, root, secrets, WorkspaceSearchFilter.defaults());
    }

    static ExecutionEnvironment environment(
            Map<String, Object> config,
            Path root,
            List<String> secrets,
            WorkspaceSearchFilter searchFilter)
            throws java.io.IOException {
        var env = Values.object(config.getOrDefault("environment", Map.of()));
        if (text(env, "kind", "local").equals("local"))
            return new LocalExecutionEnvironment("cli-local", root, secrets, searchFilter);
        return new DockerExecutionEnvironment(
                text(env, "dockerExecutable", null),
                text(env, "image", null),
                root,
                Path.of(text(env, "workerJar", null)),
                new NativeCommandTransport(),
                searchFilter);
    }

    static List<HarnessPlugin> plugins(
            Map<String, Object> config, Path root, CliConfig cli, Map<String, String> environment) {
        var plugins = new ArrayList<HarnessPlugin>();
        for (var mcp : entries(config, "mcp", 8)) {
            var effects = new HashMap<String, SideEffectLevel>();
            Values.object(mcp.getOrDefault("effects", Map.of()))
                    .forEach((k, v) -> effects.put(k, SideEffectLevel.valueOf((String) v)));
            plugins.add(
                    new McpPlugin(
                            text(mcp, "id", null),
                            () -> {
                                if (text(mcp, "transport", null).equals("http")) {
                                    String token =
                                            mcp.containsKey("tokenEnv")
                                                    ? environment.get(text(mcp, "tokenEnv", null))
                                                    : null;
                                    if (mcp.containsKey("tokenEnv")
                                            && (token == null || token.isBlank()))
                                        throw new HarnessException(
                                                "MCP_CREDENTIAL_MISSING",
                                                "Configured MCP credential is unavailable");
                                    return new HttpMcpTransport(
                                            URI.create(text(mcp, "url", null)),
                                            Duration.ofSeconds(
                                                    number(mcp, "timeoutSeconds", 15, 60)),
                                            () -> token);
                                }
                                try {
                                    return new StdioMcpTransport(
                                            strings(mcp.get("command")),
                                            root.resolve(text(mcp, "cwd", ".")),
                                            Duration.ofSeconds(
                                                    number(mcp, "timeoutSeconds", 15, 60)));
                                } catch (java.io.IOException ex) {
                                    throw new HarnessException(
                                            "MCP_START_FAILED", "Cannot start configured peer");
                                }
                            },
                            effects));
        }
        entries(config, "skills", 8).forEach(skill -> plugins.add(new SkillsPlugin(spec(skill))));
        if (!entries(config, "skillRoots", 8).isEmpty()) {
            try {
                plugins.add(SkillCatalog.plugin(catalog(config, root)));
            } catch (java.io.IOException ex) {
                throw new HarnessException("SKILL_SCAN_FAILED", "Cannot read explicit skill roots");
            }
        }
        var children = new LinkedHashMap<String, SubagentPlugin.Profile>();
        Values.object(config.getOrDefault("subagents", Map.of()))
                .forEach(
                        (name, raw) -> {
                            var child = Values.object(raw);
                            var required = strings(child.getOrDefault("requireRead", List.of()));
                            children.put(
                                    name,
                                    new SubagentPlugin.Profile(
                                            text(
                                                    child,
                                                    "prompt",
                                                    "Read-only delegated investigation"),
                                            Set.copyOf(
                                                    strings(
                                                            child.getOrDefault(
                                                                    "tools",
                                                                    List.of("read_file")))),
                                            new Budget(
                                                    Math.min(
                                                            cli.steps(),
                                                            number(child, "maxSteps", 4, 64)),
                                                    Duration.ofSeconds(
                                                            Math.min(
                                                                    cli.duration().toSeconds(),
                                                                    number(
                                                                            child,
                                                                            "durationSeconds",
                                                                            30,
                                                                            300))),
                                                    cli.budget().maxApiCost(),
                                                    cli.currency()),
                                            () ->
                                                    new ParameterizedModel(
                                                            new OpenAiCompatibleLlmClient(
                                                                    cli.modelConfig(
                                                                            cli.key(environment))),
                                                            cli.parameters()),
                                            cli.costs(),
                                            (reply, projection) ->
                                                    new CompletionEvaluator.CompletionDecision(
                                                            !required.isEmpty()
                                                                    && required.stream()
                                                                            .allMatch(
                                                                                    path ->
                                                                                            projection
                                                                                                    .toolHistory()
                                                                                                    .stream()
                                                                                                    .anyMatch(
                                                                                                            e ->
                                                                                                                    e.type()
                                                                                                                                    .equals(
                                                                                                                                            "tool.completed")
                                                                                                                            && "SUCCESS"
                                                                                                                                    .equals(
                                                                                                                                            e.payload()
                                                                                                                                                    .get(
                                                                                                                                                            "status"))
                                                                                                                            && "read_file"
                                                                                                                                    .equals(
                                                                                                                                            e.payload()
                                                                                                                                                    .get(
                                                                                                                                                            "tool"))
                                                                                                                            && path
                                                                                                                                    .equals(
                                                                                                                                            Values
                                                                                                                                                    .object(
                                                                                                                                                            e.payload()
                                                                                                                                                                    .getOrDefault(
                                                                                                                                                                            "value",
                                                                                                                                                                            Map
                                                                                                                                                                                    .of()))
                                                                                                                                                    .get(
                                                                                                                                                            "path")))),
                                                            "Explicit child read evidence required")));
                        });
        if (!children.isEmpty()) plugins.add(new SubagentPlugin(children, 1, 16));
        if (Boolean.TRUE.equals(config.get("planning"))) plugins.add(new PlanningPlugin());
        var sources = new LinkedHashMap<String, URI>();
        Values.object(config.getOrDefault("webSources", Map.of()))
                .forEach((k, v) -> sources.put(k, URI.create((String) v)));
        if (!sources.isEmpty()) plugins.add(new WebFetchPlugin(sources));
        return plugins;
    }

    static List<SkillCatalog.Entry> catalog(Map<String, Object> config, Path workspace)
            throws java.io.IOException {
        var roots = new ArrayList<SkillCatalog.Root>();
        for (var item : entries(config, "skillRoots", 8)) {
            Path directory = Path.of(text(item, "directory", null));
            roots.add(
                    new SkillCatalog.Root(
                            text(item, "id", null),
                            text(item, "kind", null).equals("project")
                                    ? workspace.resolve(directory)
                                    : directory));
        }
        return SkillCatalog.scan(
                roots,
                entries(config, "skills", 8).stream()
                        .map(s -> text(s, "id", null))
                        .collect(java.util.stream.Collectors.toSet()),
                CancellationToken.NONE);
    }

    private record ParameterizedModel(LlmClient delegate, Map<String, Object> parameters)
            implements LlmClient {
        public ModelCapabilities capabilities() {
            return delegate.capabilities();
        }

        public PreparedRequest prepare(ChatRequest request) {
            var bounded = new LinkedHashMap<>(parameters);
            bounded.put("stream", false);
            bounded.remove("stream_options");
            return delegate.prepare(new ChatRequest(request.messages(), request.tools(), bounded));
        }

        public ChatResponse complete(ChatRequest request, CancellationToken token) {
            return delegate.complete(request, token);
        }

        public StreamHandle stream(ChatRequest request, CancellationToken token) {
            return delegate.stream(request, token);
        }

        public void close() {
            delegate.close();
        }
    }
}
