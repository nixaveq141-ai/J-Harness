package io.jharness.cli;

import io.jharness.agent.ConfiguredTokenCostGuard;
import io.jharness.api.*;
import io.jharness.llm.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class CliConfig {
    static final Set<String> PLUGINS =
            Set.of(
                    "registry",
                    "session-jsonl",
                    "workspace-local",
                    "process-local",
                    "policy",
                    "tools-pipeline",
                    "llm-provider",
                    "agent-default",
                    "context-compaction",
                    "terminal-local",
                    "tool-scheduling",
                    "model-resilience");
    static final Set<String> KEYS =
            Set.of(
                    "baseUrl",
                    "model",
                    "apiKeyEnv",
                    "maxSteps",
                    "durationSeconds",
                    "requestTimeoutSeconds",
                    "contextTokens",
                    "maxOutputTokens",
                    "inputTokenCeiling",
                    "maxCost",
                    "currency",
                    "inputPerMillion",
                    "outputPerMillion",
                    "disableThinking",
                    "protocol",
                    "reasoningEffort",
                    "enabledPlugins",
                    "streaming",
                    "compactContext",
                    "compactionCharacters",
                    "toolsSupported",
                    "streamSupported",
                    "policy",
                    "enabledTools",
                    "prompts",
                    "models",
                    "profiles",
                    "presets",
                    "bundles",
                    "defaultProfile",
                    "ecosystem",
                    "searchFilter",
                    "toolScheduling",
                    "modelResilience");
    final Map<String, Object> values;
    final Map<String, Object> selection;

    public CliConfig(Map<String, Object> values) {
        this(values, Map.of());
    }

    CliConfig(Map<String, Object> values, Map<String, Object> selection) {
        this.selection = Values.snapshot(selection);
        if (!KEYS.containsAll(values.keySet()))
            throw new HarnessException(
                    "INVALID_CONFIG",
                    "Unknown config key; API keys must be environment references");
        this.values = Values.snapshot(values);
        if (Json.text(this.values).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
            throw new HarnessException("INVALID_CONFIG", "Configuration exceeds 64 KiB");
        searchFilter();
        scheduling();
        resilience();
        if (resilience().enabled() && !enabled().contains("model-resilience"))
            throw new HarnessException(
                    "INVALID_MODEL_RESILIENCE", "Model resilience plugin disabled");
        if (values.containsKey("ecosystem"))
            EcosystemConfig.validate(Values.object(values.get("ecosystem")));
        for (String name : List.of("baseUrl", "model", "apiKeyEnv", "currency", "defaultProfile"))
            if (values.containsKey(name)) string(name, "");
        ModelProtocol protocol = ModelProtocol.fromId(string("protocol", "chat-completions"));
        if (values.containsKey("reasoningEffort")
                && (protocol != ModelProtocol.RESPONSES
                        || !Set.of("none", "low", "medium", "high")
                                .contains(string("reasoningEffort", "none"))))
            throw new HarnessException("INVALID_CONFIG", "Reasoning effort requires Responses");
        if (protocol == ModelProtocol.RESPONSES
                && Boolean.TRUE.equals(values.get("disableThinking")))
            throw new HarnessException("INVALID_CONFIG", "Use reasoningEffort with Responses");
        steps();
        duration();
        requestTimeout();
        contextTokens();
        maxOutputTokens();
        if (maxOutputTokens() > contextTokens())
            throw new HarnessException("INVALID_CONFIG", "Output limit exceeds context capacity");
        currency();
        enabled();
        if (scheduling().maxConcurrency() > 1 && !enabled().contains("tool-scheduling"))
            throw new HarnessException(
                    "INVALID_TOOL_SCHEDULING", "Parallel scheduling plugin is disabled");
        if (values.containsKey("baseUrl")) {
            try {
                new OpenAiCompatibleConfig(
                        URI.create(string("baseUrl", "")),
                        string("model", "unconfigured"),
                        "validation-only",
                        requestTimeout(),
                        2 * 1024 * 1024,
                        0,
                        new ModelCapabilities(
                                !Boolean.FALSE.equals(values.get("toolsSupported")),
                                Boolean.TRUE.equals(values.get("streamSupported")),
                                false,
                                false,
                                contextTokens()),
                        ModelProtocol.fromId(string("protocol", "chat-completions")));
            } catch (IllegalArgumentException ex) {
                throw new HarnessException("INVALID_CONFIG", "Invalid model configuration", ex);
            }
        }
        if (!string("apiKeyEnv", "JHARNESS_API_KEY").matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new HarnessException("INVALID_CONFIG", "Invalid environment variable name");
        for (String flag :
                List.of("streaming", "compactContext", "toolsSupported", "streamSupported"))
            if (values.containsKey(flag) && !(values.get(flag) instanceof Boolean))
                throw new HarnessException("INVALID_CONFIG", "Expected boolean " + flag);
        if (!Set.of("default", "read-only").contains(string("policy", "default")))
            throw new HarnessException("INVALID_CONFIG", "Unknown policy");
        if (Boolean.TRUE.equals(values.get("compactContext"))
                && !enabled().contains("context-compaction"))
            throw new HarnessException("INVALID_CONFIG", "Context compaction plugin disabled");
        integer("compactionCharacters", contextTokens(), 1024, contextTokens());
        for (String list : List.of("enabledTools", "prompts"))
            if (values.containsKey(list)
                    && (!(values.get(list) instanceof List<?> items)
                            || items.size() > 64
                            || items.stream().anyMatch(v -> !(v instanceof String))))
                throw new HarnessException("INVALID_CONFIG", "Invalid string list");
        for (String catalog : List.of("models", "profiles", "bundles", "presets"))
            if (values.containsKey(catalog)
                    && (!(values.get(catalog) instanceof Map<?, ?> entries)
                            || entries.size() > 64
                            || entries.entrySet().stream()
                                    .anyMatch(
                                            e ->
                                                    !(e.getKey() instanceof String)
                                                            || !(e.getValue()
                                                                    instanceof Map<?, ?>))))
                throw new HarnessException("INVALID_CONFIG", "Invalid configuration catalog");
        if (values.containsKey("models") && Values.object(values.get("models")).containsKey("base"))
            throw new HarnessException(
                    "INVALID_CONFIG", "base is the reserved default model route");
        if (values.containsKey("disableThinking")
                && !(values.get("disableThinking") instanceof Boolean))
            throw new HarnessException("INVALID_CONFIG", "disableThinking must be boolean");
        for (String price : List.of("inputPerMillion", "outputPerMillion"))
            if (values.containsKey(price) && decimal(price).signum() < 0)
                throw new HarnessException("INVALID_CONFIG", "Negative model price");
        if (values.containsKey("inputTokenCeiling")) integer("inputTokenCeiling", 0, 1, 1000000);
        if (values.containsKey("maxCost") && decimal("maxCost").signum() <= 0)
            throw new HarnessException("INVALID_CONFIG", "maxCost must be positive");
    }

    WorkspaceSearchFilter searchFilter() {
        return WorkspaceSearchFilter.from(values.getOrDefault("searchFilter", Map.of()));
    }

    ModelResilienceOptions resilience() {
        return ModelResilienceOptions.from(values.getOrDefault("modelResilience", Map.of()));
    }

    ToolSchedulingOptions scheduling() {
        return ToolSchedulingOptions.from(values.getOrDefault("toolScheduling", Map.of()));
    }

    String string(String key, String fallback) {
        Object value = values.getOrDefault(key, fallback);
        if (!(value instanceof String text) || text.isBlank())
            throw new HarnessException("INVALID_CONFIG", "Invalid config field " + key);
        return text;
    }

    int integer(String key, int fallback, int min, int max) {
        Object value = values.getOrDefault(key, fallback);
        if (!(value instanceof Number n)
                || n.doubleValue() != n.intValue()
                || n.intValue() < min
                || n.intValue() > max)
            throw new HarnessException("INVALID_CONFIG", "Invalid config field " + key);
        return n.intValue();
    }

    BigDecimal decimal(String key) {
        try {
            return new BigDecimal(String.valueOf(values.get(key)));
        } catch (NumberFormatException ex) {
            throw new HarnessException("INVALID_CONFIG", "Invalid price/budget field " + key);
        }
    }

    int steps() {
        return integer("maxSteps", 20, 1, 100);
    }

    Duration duration() {
        return Duration.ofSeconds(integer("durationSeconds", 600, 1, 3600));
    }

    Duration requestTimeout() {
        return Duration.ofSeconds(integer("requestTimeoutSeconds", 90, 1, 300));
    }

    int contextTokens() {
        return integer("contextTokens", 65536, 1024, 1000000);
    }

    int maxOutputTokens() {
        return integer("maxOutputTokens", Math.min(4096, contextTokens()), 1, 32768);
    }

    String currency() {
        String currency = string("currency", "USD");
        if (!Set.of("USD", "CNY").contains(currency))
            throw new HarnessException("INVALID_CONFIG", "Unsupported currency");
        return currency;
    }

    Set<String> enabled() {
        if (!values.containsKey("enabledPlugins")) return PLUGINS;
        if (!(values.get("enabledPlugins") instanceof List<?> names)
                || names.stream().anyMatch(n -> !(n instanceof String) || !PLUGINS.contains(n)))
            throw new HarnessException("INVALID_CONFIG", "Invalid enabledPlugins");
        return Set.copyOf(
                ((List<?>) values.get("enabledPlugins")).stream().map(String.class::cast).toList());
    }

    String key(Map<String, String> environment) {
        String key = environment.get(string("apiKeyEnv", "JHARNESS_API_KEY"));
        if (key == null || key.isBlank())
            throw new HarnessException(
                    "API_KEY_MISSING", "Configured API key environment variable is absent");
        return key;
    }

    OpenAiCompatibleConfig modelConfig(String key) {
        return new OpenAiCompatibleConfig(
                URI.create(string("baseUrl", "")),
                string("model", ""),
                key,
                requestTimeout(),
                2 * 1024 * 1024,
                0,
                new ModelCapabilities(
                        !Boolean.FALSE.equals(values.get("toolsSupported")),
                        Boolean.TRUE.equals(values.get("streamSupported")),
                        false,
                        false,
                        contextTokens()),
                ModelProtocol.fromId(string("protocol", "chat-completions")));
    }

    Budget budget() {
        return new Budget(
                steps(),
                duration(),
                values.containsKey("maxCost") ? Optional.of(decimal("maxCost")) : Optional.empty(),
                currency());
    }

    ApiCostGuard costs() {
        if (!values.containsKey("maxCost")) return ApiCostGuard.unavailable();
        if (!values.containsKey("inputPerMillion")
                || !values.containsKey("outputPerMillion")
                || !values.containsKey("inputTokenCeiling"))
            throw new HarnessException(
                    "COST_BUDGET_UNAVAILABLE",
                    "Configure verified model prices and inputTokenCeiling");
        return new ConfiguredTokenCostGuard(
                string("model", ""),
                integer("inputTokenCeiling", 0, 1, 1000000),
                maxOutputTokens(),
                decimal("inputPerMillion").movePointLeft(6),
                decimal("outputPerMillion").movePointLeft(6),
                currency());
    }

    Map<String, Object> parameters() {
        Map<String, Object> map = new LinkedHashMap<>(Map.of("max_tokens", maxOutputTokens()));
        if (Boolean.TRUE.equals(values.get("streaming"))
                && Boolean.TRUE.equals(values.get("streamSupported"))) {
            map.put("stream", true);
            map.put("stream_options", Map.of("include_usage", true));
        }
        map.put("protocol", string("protocol", "chat-completions"));
        if (values.containsKey("reasoningEffort"))
            map.put("reasoning_effort", string("reasoningEffort", "none"));
        if (Boolean.TRUE.equals(values.get("disableThinking")))
            map.put("thinking", Map.of("type", "disabled"));
        return map;
    }

    static CliConfig load(Path path) throws java.io.IOException {
        return new CliConfig(
                Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? Json.read(path, 65536) : Map.of());
    }
}
