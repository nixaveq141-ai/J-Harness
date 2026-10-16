package io.jharness.cli;

import io.jharness.api.*;
import io.jharness.web.HarnessWebServer;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Host-owned nonsecret settings. A successful save affects only subsequent SDK construction. */
final class WorkbenchSettings implements HarnessWebServer.Settings, DataSanitizer {
    private static final Set<String> EXTENSIONS =
            Set.of("mcp", "skills", "subagents", "planning", "webSources");
    private final CliConfig base;
    private final Map<String, String> environment;
    private final Metadata metadata;
    private final SystemCredentials vault;
    private final Path file;
    private final String namespace;
    private final List<String> secrets = new CopyOnWriteArrayList<>();
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
        namespace =
                "J-Harness/"
                        + Json.hash(
                                metadata.workspace().toString().getBytes(StandardCharsets.UTF_8));
        environment.forEach(
                (k, v) -> {
                    if (v != null
                            && !v.isBlank()
                            && (k.matches("(?i).*(_API_KEY|_TOKEN|_SECRET|_PASSWORD)$")
                                    || k.equals(base.string("apiKeyEnv", "JHARNESS_API_KEY"))))
                        secrets.add(v);
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
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) state = Json.read(file, 32768);
        validate(state);
        registerReferences(state);
        // Register all saved credentials before any data can be exposed by Web or SDK.
        for (var m : models(state)) key(m);
    }

    @Override
    public Map<String, Object> sanitize(Map<String, Object> value) {
        return new SecretRedactor(List.copyOf(secrets)).sanitize(value);
    }

    private String revision() {
        return Json.hash(Json.text(state).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized Map<String, Object> read() throws IOException {
        var visible = new ArrayList<Map<String, Object>>();
        for (var model : models(state)) {
            var v = new LinkedHashMap<>(model);
            v.remove("credentialRef");
            v.put("keyConfigured", !key(model).isBlank());
            visible.add(v);
        }
        return Map.of(
                "revision",
                revision(),
                "models",
                visible,
                "selected",
                state.get("selected"),
                "extensions",
                state.get("extensions"),
                "credentialStore",
                vault.supported() ? "Windows Credential Manager" : "unavailable",
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
            if (!Set.of(
                            "id",
                            "label",
                            "baseUrl",
                            "model",
                            "apiKeyEnv",
                            "keyConfigured",
                            "pricing",
                            "disableThinking")
                    .containsAll(raw.keySet())) throw error("INVALID_SETTINGS");
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
                secrets.add(secret);
                ref = Json.hash((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
                writes.put(ref, secret);
            }
            model.put("credentialRef", ref);
            next.add(model);
        }
        if (!next.stream().map(m -> m.get("id")).toList().containsAll(keys.keySet()))
            throw error("INVALID_SETTINGS");
        var candidate =
                Values.snapshot(
                        Map.of(
                                "models",
                                next,
                                "selected",
                                input.get("selected"),
                                "extensions",
                                input.get("extensions")));
        validate(candidate);
        registerReferences(candidate);
        if (!sanitize(candidate).equals(candidate)) throw error("SECRET_CONFIG");
        byte[] bytes = Json.text(candidate).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32768) throw error("INVALID_SETTINGS");
        var credentialWrites = new LinkedHashMap<String, String>();
        writes.forEach((ref, secret) -> credentialWrites.put(namespace + "/" + ref, secret));
        vault.update(credentialWrites, List.of());
        credentialCache.putAll(writes);
        // Recheck metadata and refuse linked/nonregular targets. Atomic replacement is mandatory.
        metadata.directory("settings");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw error("CONFIG_PATH_DENIED");
        Path temp = Files.createTempFile(file.getParent(), "settings-", ".tmp");
        try {
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
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
        var priorRefs =
                models(state).stream()
                        .map(m -> (String) m.get("credentialRef"))
                        .filter(s -> !s.isEmpty())
                        .toList();
        var nextRefs =
                models(candidate).stream().map(m -> (String) m.get("credentialRef")).toList();
        state = candidate;
        var obsolete = priorRefs.stream().filter(ref -> !nextRefs.contains(ref)).toList();
        try {
            vault.update(Map.of(), obsolete.stream().map(ref -> namespace + "/" + ref).toList());
            obsolete.forEach(credentialCache::remove);
        } catch (IOException ex) {
            credentialCleanupPending = true;
        }
        return read();
    }

    record RuntimeConfig(CliConfig config, Map<String, String> environment) {}

    @Override
    public void prepareTask() throws IOException {
        runtime();
    }

    synchronized RuntimeConfig runtime() throws IOException {
        var model =
                models(state).stream()
                        .filter(m -> m.get("id").equals(state.get("selected")))
                        .findFirst()
                        .orElseThrow();
        var values = configuration(state, model);
        var env = new HashMap<>(environment);
        String key = key(model);
        if (key.isBlank()) throw error("API_KEY_MISSING");
        values.put("apiKeyEnv", "JHARNESS_UI_API_KEY");
        env.put("JHARNESS_UI_API_KEY", key);
        return new RuntimeConfig(
                new CliConfig(
                        values,
                        Map.of("modelRoute", model.get("id"), "settingsRevision", revision())),
                Map.copyOf(env));
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
        return values;
    }

    private void validate(Map<String, Object> value) {
        if (!value.keySet().equals(Set.of("models", "selected", "extensions")))
            throw error("INVALID_SETTINGS");
        var ids = new HashSet<String>();
        for (var model : models(value)) {
            if (!model.keySet()
                    .equals(
                            Set.of(
                                    "id",
                                    "label",
                                    "baseUrl",
                                    "model",
                                    "credentialRef",
                                    "apiKeyEnv",
                                    "pricing",
                                    "disableThinking"))) throw error("INVALID_SETTINGS");
            String id = field(model, "id", 32);
            if (!id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw error("INVALID_SETTINGS");
            field(model, "label", 80);
            field(model, "model", 128);
            URI uri = URI.create(field(model, "baseUrl", 512));
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
        if (!secret.isEmpty() && !secrets.contains(secret)) secrets.add(secret);
        return secret;
    }

    private void registerReferences(Map<String, Object> value) {
        for (var m : models(value)) register((String) m.get("apiKeyEnv"));
        for (var raw : EcosystemConfig.entries(Values.object(value.get("extensions")), "mcp", 8)) {
            if (raw.get("tokenEnv") instanceof String name) register(name);
        }
    }

    private void register(String name) {
        String secret = environment.get(name);
        if (secret != null && !secret.isBlank() && !secrets.contains(secret)) secrets.add(secret);
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
