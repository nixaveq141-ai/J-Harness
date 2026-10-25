package io.jharness.cli;

import io.jharness.api.*;
import io.jharness.web.HarnessWebServer;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Host-owned nonsecret settings. A successful save affects only subsequent SDK construction. */
final class WorkbenchSettings implements HarnessWebServer.Settings, DataSanitizer {
    private static final Set<String> EXTENSIONS =
            Set.of("mcp", "skills", "skillRoots", "subagents", "planning", "webSources");
    private static final Set<String> MODEL_OPTIONS =
            Set.of(
                    "protocol",
                    "reasoningEffort",
                    "contextTokens",
                    "maxOutputTokens",
                    "requestTimeoutSeconds",
                    "toolsSupported",
                    "streamSupported",
                    "streaming");
    private final CliConfig base;
    private final Map<String, String> environment;
    private final Metadata metadata;
    private final SystemCredentials vault;
    private final Path file;
    private final String namespace;
    private final Set<String> secrets = new LinkedHashSet<>();
    private volatile SecretRedactor redactor = new SecretRedactor(List.of());
    private final Set<String> pendingCleanup = new LinkedHashSet<>();
    private final Path cleanupFile;
    private final Map<String, String> credentialCache = new HashMap<>();
    private Map<String, Object> state;
    private boolean credentialCleanupPending;

    WorkbenchSettings(
            CliConfig base,
            Map<String, String> environment,
            Metadata metadata,
            SystemCredentials vault)
            throws IOException {
        this.base = base;
        this.environment = Map.copyOf(environment);
        this.metadata = metadata;
        this.vault = vault;
        file = metadata.directory("settings").resolve("workbench.json");
        cleanupFile = file.resolveSibling("credential-cleanup.json");
        namespace =
                "J-Harness/"
                        + Json.hash(
                                metadata.workspace().toString().getBytes(StandardCharsets.UTF_8));
        environment.forEach(
                (k, v) -> {
                    if (v != null
                            && !v.isBlank()
                            && (SecretRedactor.credentialEnvironmentName(k)
                                    || k.equals(base.string("apiKeyEnv", "JHARNESS_API_KEY"))))
                        registerSecret(v);
                });
        var extensions = new LinkedHashMap<String, Object>();
        Values.object(base.values.getOrDefault("ecosystem", Map.of()))
                .forEach(
                        (k, v) -> {
                            if (EXTENSIONS.contains(k)) extensions.put(k, v);
                        });
        extensions.put("compactContext", base.values.getOrDefault("compactContext", false));
        var pricing = new LinkedHashMap<String, Object>();
        for (String name :
                List.of(
                        "maxCost",
                        "inputPerMillion",
                        "outputPerMillion",
                        "inputTokenCeiling",
                        "currency"))
            if (base.values.containsKey(name)) pricing.put(name, base.values.get(name));
        state =
                Map.of(
                        "models",
                        List.of(
                                Map.of(
                                        "id",
                                        "default",
                                        "label",
                                        "默认连接",
                                        "baseUrl",
                                        base.string("baseUrl", "https://api.deepseek.com"),
                                        "model",
                                        base.string("model", "deepseek-chat"),
                                        "credentialRef",
                                        "",
                                        "apiKeyEnv",
                                        base.string("apiKeyEnv", "JHARNESS_API_KEY"),
                                        "pricing",
                                        pricing,
                                        "disableThinking",
                                        Boolean.TRUE.equals(base.values.get("disableThinking")))),
                        "selected",
                        "default",
                        "extensions",
                        extensions);
        var initialModel = new LinkedHashMap<>(models(state).getFirst());
        for (String option : MODEL_OPTIONS)
            if (base.values.containsKey(option)) initialModel.put(option, base.values.get(option));
        var initialState = new LinkedHashMap<>(state);
        initialState.put("models", List.of(initialModel));
        state = initialState;
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) state = Json.read(file, 32768);
        validate(state);
        registerReferences(state);
        // Register all saved credentials before any data can be exposed by Web or SDK.
        for (var m : models(state)) keyAvailable(m);
        if (Files.exists(cleanupFile, LinkOption.NOFOLLOW_LINKS)) {
            Map<String, Object> saved;
            try {
                saved = Json.read(cleanupFile, 32768);
            } catch (HarnessException invalid) {
                throw new HarnessException(
                        "CREDENTIAL_CLEANUP_INVALID",
                        "Credential cleanup journal requires offline inspection",
                        invalid);
            }
            if (!(saved.get("refs") instanceof List<?> refs) || refs.size() > 128)
                throw error("CREDENTIAL_CLEANUP_INVALID");
            for (var ref : refs) {
                if (!(ref instanceof String value) || !value.matches("[a-f0-9]{64}"))
                    throw error("CREDENTIAL_CLEANUP_INVALID");
                pendingCleanup.add(value);
            }
            cleanupCredentials();
        }
    }

    @Override
    public String streamingText(String cumulative) {
        return redactor.streamingText(cumulative);
    }

    @Override
    public Map<String, Object> sanitize(Map<String, Object> value) {
        return redactor.sanitize(value);
    }

    private String revision() {
        return Json.hash(Json.text(state).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized Map<String, Object> read() throws IOException {
        var visible = new ArrayList<Map<String, Object>>();
        for (var model : models(state)) {
            var v = new LinkedHashMap<>(model);
            var resolved = new CliConfig(configuration(state, model));
            v.put("protocol", resolved.string("protocol", "chat-completions"));
            v.put("contextTokens", resolved.contextTokens());
            v.put("maxOutputTokens", resolved.maxOutputTokens());
            v.put("requestTimeoutSeconds", resolved.requestTimeout().toSeconds());
            v.put("toolsSupported", !Boolean.FALSE.equals(resolved.values.get("toolsSupported")));
            v.put("streamSupported", Boolean.TRUE.equals(resolved.values.get("streamSupported")));
            v.put("streaming", Boolean.TRUE.equals(resolved.values.get("streaming")));
            v.remove("credentialRef");
            v.put("keyConfigured", keyAvailable(model));
            visible.add(v);
        }
        return Map.of(
                "revision",
                revision(),
                "models",
                visible,
                "selected",
                state.get("selected"),
                "policy",
                policy(state),
                "extensions",
                state.get("extensions"),
                "credentialStore",
                vault.description(),
                "corePlugins",
                CliConfig.PLUGINS.stream().sorted().toList(),
                "credentialCleanupPending",
                credentialCleanupPending);
    }

    @Override
    public synchronized Map<String, Object> save(Map<String, Object> input) throws IOException {
        if (!revision().equals(input.get("revision"))) throw error("SETTINGS_CONFLICT");
        var keys = Values.object(input.get("apiKeys"));
        if (keys.size() > 4) throw error("INVALID_SETTINGS");
        var previous = new HashMap<String, Map<String, Object>>();
        models(state).forEach(m -> previous.put((String) m.get("id"), m));
        var next = new ArrayList<Map<String, Object>>();
        var writes = new LinkedHashMap<String, String>();
        for (var raw : models(input)) {
            var allowed = new HashSet<>(MODEL_OPTIONS);
            allowed.addAll(
                    Set.of(
                            "id",
                            "label",
                            "baseUrl",
                            "model",
                            "apiKeyEnv",
                            "keyConfigured",
                            "pricing",
                            "disableThinking"));
            if (!allowed.containsAll(raw.keySet())) throw error("INVALID_SETTINGS");
            var model = new LinkedHashMap<>(raw);
            model.remove("keyConfigured");
            String id = field(model, "id", 32);
            String ref =
                    previous.containsKey(id) ? (String) previous.get(id).get("credentialRef") : "";
            if (keys.containsKey(id)) {
                Object value = keys.get(id);
                if (!(value instanceof String secret)
                        || secret.length() < 8
                        || secret.getBytes(StandardCharsets.UTF_8).length > 1024
                        || secret.chars().anyMatch(c -> c < 33 || c > 126))
                    throw error("INVALID_SETTINGS");
                registerSecret(secret);
                ref = Json.hash((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
                writes.put(ref, secret);
            }
            model.put("credentialRef", ref);
            next.add(model);
        }
        if (!next.stream().map(m -> m.get("id")).toList().containsAll(keys.keySet()))
            throw error("INVALID_SETTINGS");
        if (!(input.get("selected") instanceof String)
                || !(input.get("extensions") instanceof Map<?, ?>)) throw error("INVALID_SETTINGS");
        var candidate =
                Values.snapshot(
                        Map.of(
                                "models",
                                next,
                                "selected",
                                input.get("selected"),
                                "extensions",
                                input.get("extensions"),
                                "policy",
                                input.getOrDefault("policy", policy(state))));
        validate(candidate);
        registerReferences(candidate);
        if (!sanitize(candidate).equals(candidate)) throw error("SECRET_CONFIG");
        byte[] bytes = Json.text(candidate).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32768) throw error("INVALID_SETTINGS");
        var credentialWrites = new LinkedHashMap<String, String>();
        writes.forEach((ref, secret) -> credentialWrites.put(namespace + "/" + ref, secret));
        // Persist intent before touching the vault. Only newly introduced references may be
        // compensated.
        var live = models(state).stream().map(m -> (String) m.get("credentialRef")).toList();
        writes.keySet().stream().filter(ref -> !live.contains(ref)).forEach(pendingCleanup::add);
        var intendedRefs =
                models(candidate).stream().map(m -> (String) m.get("credentialRef")).toList();
        live.stream()
                .filter(ref -> !ref.isEmpty() && !intendedRefs.contains(ref))
                .forEach(pendingCleanup::add);
        persistCleanup();
        try {
            vault.update(credentialWrites, List.of());
            // Recheck metadata and refuse linked/nonregular targets. Atomic replacement is
            // mandatory.
            metadata.directory("settings");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw error("CONFIG_PATH_DENIED");
            Path temp = Files.createTempFile(file.getParent(), "settings-", ".tmp");
            try {
                try (var channel =
                        java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    var buffer = java.nio.ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException failure) {
            try {
                cleanupCredentials();
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        credentialCache.putAll(writes);
        var priorRefs =
                models(state).stream()
                        .map(m -> (String) m.get("credentialRef"))
                        .filter(s -> !s.isEmpty())
                        .toList();
        var nextRefs =
                models(candidate).stream().map(m -> (String) m.get("credentialRef")).toList();
        state = candidate;
        var obsolete = priorRefs.stream().filter(ref -> !nextRefs.contains(ref)).toList();
        pendingCleanup.addAll(obsolete);
        try {
            cleanupCredentials();
        } catch (IOException | RuntimeException cleanup) {
            // The settings and delete intents are already committed. Report cleanup separately.
            credentialCleanupPending = true;
        }
        return read();
    }

    private synchronized void registerSecret(String secret) {
        if (secrets.add(secret)) redactor = new SecretRedactor(List.copyOf(secrets));
    }

    private void persistCleanup() throws IOException {
        if (pendingCleanup.size() > 128) throw error("CREDENTIAL_CLEANUP_LIMIT");
        metadata.directory("settings");
        Json.write(
                cleanupFile,
                Json.text(Map.of("refs", List.copyOf(pendingCleanup)))
                        .getBytes(StandardCharsets.UTF_8));
        credentialCleanupPending = !pendingCleanup.isEmpty();
    }

    private void cleanupCredentials() throws IOException {
        var live = models(state).stream().map(m -> (String) m.get("credentialRef")).toList();
        pendingCleanup.removeAll(live);
        persistCleanup();
        for (String ref : List.copyOf(pendingCleanup)) {
            try {
                vault.delete(namespace + "/" + ref);
                credentialCache.remove(ref);
                pendingCleanup.remove(ref);
                persistCleanup();
            } catch (IOException | HarnessException unavailable) {
                credentialCleanupPending = true;
                return;
            }
        }
        credentialCleanupPending = false;
    }

    record RuntimeConfig(CliConfig config, Map<String, String> environment) {}

    @Override
    public void prepareTask() throws IOException {
        runtime();
    }

    @Override
    public void prepareSelection(Map<String, Object> selection) throws IOException {
        runtime(selection);
    }

    synchronized RuntimeConfig runtime() throws IOException {
        return runtime(capture(""));
    }

    @Override
    public synchronized Map<String, Object> capture(String modelId) throws IOException {
        String selected = modelId.isEmpty() ? (String) state.get("selected") : modelId;
        var model =
                models(state).stream()
                        .filter(m -> m.get("id").equals(selected))
                        .findFirst()
                        .orElseThrow(() -> error("UNKNOWN_MODEL"));
        if (key(model).isBlank()) throw error("API_KEY_MISSING");
        var resolved = configuration(state, model);
        checkMcpCredentials(resolved);
        new CliConfig(resolved).costs();
        var snapshot =
                Map.<String, Object>of(
                        "modelRoute",
                        selected,
                        "settingsRevision",
                        revision(),
                        "resolved",
                        resolved,
                        "credentialRef",
                        model.get("credentialRef"),
                        "apiKeyEnv",
                        model.get("apiKeyEnv"));
        if (!sanitize(snapshot).equals(snapshot)
                || Json.text(snapshot).getBytes(StandardCharsets.UTF_8).length > 32768)
            throw error("INVALID_SETTINGS");
        return Values.snapshot(snapshot);
    }

    synchronized RuntimeConfig runtime(Map<String, Object> snapshot) throws IOException {
        if (snapshot.isEmpty()) throw error("MODEL_SELECTION_REQUIRED");
        var values = new LinkedHashMap<>(Values.object(snapshot.get("resolved")));
        checkMcpCredentials(values);
        var env = new HashMap<>(environment);
        String key = key(snapshot);
        if (key.isBlank()) throw error("API_KEY_MISSING");
        values.put("apiKeyEnv", "JHARNESS_UI_API_KEY");
        env.put("JHARNESS_UI_API_KEY", key);
        return new RuntimeConfig(
                new CliConfig(
                        values,
                        Map.of(
                                "modelRoute",
                                snapshot.get("modelRoute"),
                                "settingsRevision",
                                snapshot.get("settingsRevision"))),
                Map.copyOf(env));
    }

    @Override
    public synchronized Map<String, Object> check(String modelId) throws IOException {
        var snapshot = capture(modelId);
        return Map.of(
                "valid",
                true,
                "keyConfigured",
                true,
                "modelId",
                snapshot.get("modelRoute"),
                "networkChecked",
                false,
                "requestSent",
                false);
    }

    private void checkMcpCredentials(Map<String, Object> config) {
        var ecosystem = Values.object(config.getOrDefault("ecosystem", Map.of()));
        for (var mcp : EcosystemConfig.entries(ecosystem, "mcp", 8)) {
            if (mcp.get("tokenEnv") instanceof String name
                    && environment.getOrDefault(name, "").isBlank())
                throw error("MCP_CREDENTIAL_MISSING");
        }
    }

    private Map<String, Object> configuration(
            Map<String, Object> config, Map<String, Object> model) {
        var values = new LinkedHashMap<>(base.values);
        for (String key :
                List.of(
                        "maxCost",
                        "inputPerMillion",
                        "outputPerMillion",
                        "inputTokenCeiling",
                        "currency")) values.remove(key);
        var pricing = Values.object(model.get("pricing"));
        if (!Set.of(
                        "maxCost",
                        "inputPerMillion",
                        "outputPerMillion",
                        "inputTokenCeiling",
                        "currency")
                .containsAll(pricing.keySet())) throw error("INVALID_SETTINGS");
        values.putAll(pricing);
        for (String option : MODEL_OPTIONS) {
            values.remove(option);
            if (model.containsKey(option)) values.put(option, model.get(option));
            else if (!Set.of("protocol", "reasoningEffort").contains(option)
                    && base.values.containsKey(option)) values.put(option, base.values.get(option));
        }
        values.put("baseUrl", model.get("baseUrl"));
        values.put("model", model.get("model"));
        values.put("disableThinking", model.get("disableThinking"));
        values.put("apiKeyEnv", model.get("apiKeyEnv"));
        var ext = Values.object(config.get("extensions"));
        var ecosystem =
                new LinkedHashMap<>(Values.object(base.values.getOrDefault("ecosystem", Map.of())));
        EXTENSIONS.forEach(ecosystem::remove);
        ext.forEach(
                (k, v) -> {
                    if (EXTENSIONS.contains(k)) ecosystem.put(k, v);
                });
        values.put("ecosystem", ecosystem);
        values.put("compactContext", ext.getOrDefault("compactContext", false));
        values.put("policy", policy(config));
        return values;
    }

    private void validate(Map<String, Object> value) {
        var keys = new HashSet<>(value.keySet());
        keys.remove("policy");
        if (!keys.equals(Set.of("models", "selected", "extensions")))
            throw error("INVALID_SETTINGS");
        if (!Set.of("default", "read-only").contains(policy(value)))
            throw error("INVALID_SETTINGS");
        var ids = new HashSet<String>();
        for (var model : models(value)) {
            var required =
                    Set.of(
                            "id",
                            "label",
                            "baseUrl",
                            "model",
                            "credentialRef",
                            "apiKeyEnv",
                            "pricing",
                            "disableThinking");
            var allowed = new HashSet<>(required);
            allowed.addAll(MODEL_OPTIONS);
            if (!model.keySet().containsAll(required) || !allowed.containsAll(model.keySet()))
                throw error("INVALID_SETTINGS");
            String id = field(model, "id", 32);
            if (!id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw error("INVALID_SETTINGS");
            field(model, "label", 80);
            field(model, "model", 128);
            URI uri;
            try {
                uri = URI.create(field(model, "baseUrl", 512));
            } catch (IllegalArgumentException ex) {
                throw error("INVALID_SETTINGS");
            }
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) throw error("INVALID_SETTINGS");
            if (!(model.get("credentialRef") instanceof String ref)
                    || !ref.matches("(?:[a-f0-9]{64})?")) throw error("INVALID_SETTINGS");
            if (!field(model, "apiKeyEnv", 128).matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw error("INVALID_SETTINGS");
            var checked = new CliConfig(configuration(value, model));
            if (Boolean.TRUE.equals(checked.values.get("streaming"))
                    && !Boolean.TRUE.equals(checked.values.get("streamSupported")))
                throw error("INVALID_SETTINGS");
            checked.budget();
            checked.costs();
        }
        if (!ids.contains(value.get("selected"))) throw error("INVALID_SETTINGS");
        var ext = Values.object(value.get("extensions"));
        var allowed = new HashSet<>(EXTENSIONS);
        allowed.add("compactContext");
        if (!allowed.containsAll(ext.keySet())) throw error("INVALID_SETTINGS");
    }

    private String key(Map<String, Object> model) throws IOException {
        String ref = (String) model.get("credentialRef");
        String secret;
        if (ref.isEmpty()) secret = environment.getOrDefault((String) model.get("apiKeyEnv"), "");
        else {
            if (!credentialCache.containsKey(ref))
                credentialCache.put(ref, vault.read(namespace + "/" + ref));
            secret = credentialCache.get(ref);
        }
        if (!secret.isEmpty() && !secrets.contains(secret)) registerSecret(secret);
        return secret;
    }

    private boolean keyAvailable(Map<String, Object> model) {
        try {
            return !key(model).isBlank();
        } catch (IOException | HarnessException unavailable) {
            // Preserve configuration and allow the settings page to repair unavailable references.
            // Task admission still uses key(), so no failed lookup can silently select another key.
            return false;
        }
    }

    @Override
    public synchronized Map<String, Object> skills(String workspace) throws IOException {
        var config = Values.object(state.get("extensions"));
        var entries = new ArrayList<Map<String, Object>>();
        for (var entry : EcosystemConfig.catalog(config, Path.of(workspace)))
            entries.add(entry.view());
        var files = new io.jharness.workspace.LocalWorkspace(Path.of(workspace));
        for (var skill : EcosystemConfig.entries(config, "skills", 8)) {
            String id = EcosystemConfig.text(skill, "id", null);
            var path = new ExecPath(EcosystemConfig.text(skill, "directory", null) + "/SKILL.md");
            String before = files.versionOf(path), text = files.readText(path);
            if (text.getBytes(StandardCharsets.UTF_8).length > 65536)
                throw error("SKILL_FILE_LIMIT");
            if (!before.equals(files.versionOf(path))) throw error("FILE_CONFLICT");
            entries.add(
                    Map.of(
                            "id",
                            id,
                            "description",
                            "显式配置的技能；资源/脚本按白名单加载",
                            "root",
                            "configured",
                            "directory",
                            EcosystemConfig.text(skill, "directory", null),
                            "text",
                            text,
                            "sha256",
                            Json.hash(text.getBytes(StandardCharsets.UTF_8)),
                            "tool",
                            "skill_" + id + "__load",
                            "untrusted",
                            true));
        }
        var result = Map.<String, Object>of("skills", entries, "snapshot", true);
        if (Json.text(result).getBytes(StandardCharsets.UTF_8).length > 480 * 1024)
            throw error("SKILL_TOTAL_LIMIT");
        return result;
    }

    private String policy(Map<String, Object> config) {
        Object policy = config.getOrDefault("policy", base.string("policy", "default"));
        if (!(policy instanceof String text) || !Set.of("default", "read-only").contains(text))
            throw error("INVALID_SETTINGS");
        return text;
    }

    private void registerReferences(Map<String, Object> value) {
        for (var m : models(value)) register((String) m.get("apiKeyEnv"));
        for (var raw : EcosystemConfig.entries(Values.object(value.get("extensions")), "mcp", 8)) {
            if (raw.get("tokenEnv") instanceof String name) register(name);
        }
    }

    private void register(String name) {
        String secret = environment.get(name);
        if (secret != null && !secret.isBlank() && !secrets.contains(secret))
            registerSecret(secret);
    }

    private static List<Map<String, Object>> models(Map<String, Object> value) {
        if (!(value.get("models") instanceof List<?> list) || list.isEmpty() || list.size() > 16)
            throw error("INVALID_SETTINGS");
        return list.stream().map(Values::object).toList();
    }

    private static String field(Map<String, Object> value, String key, int limit) {
        if (!(value.get(key) instanceof String text)
                || text.isBlank()
                || text.length() > limit
                || text.chars().anyMatch(c -> c < 32)) throw error("INVALID_SETTINGS");
        return text;
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "Workbench settings operation rejected");
    }
}
