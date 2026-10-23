package io.jharness.api;

import java.time.Duration;
import java.util.*;

public record ToolSchedulingOptions(int maxConcurrency, Set<String> tools, Duration timeout) {
    public ToolSchedulingOptions {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(timeout, "timeout");
        if (maxConcurrency < 1
                || maxConcurrency > 8
                || tools.size() > 32
                || tools.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_-]{1,128}"))
                || timeout.compareTo(Duration.ofMillis(50)) < 0
                || timeout.compareTo(Duration.ofSeconds(60)) > 0)
            throw new HarnessException(
                    "INVALID_TOOL_SCHEDULING", "Invalid bounded host tool scheduling options");
        tools = Set.copyOf(tools);
    }

    public static ToolSchedulingOptions serial() {
        return new ToolSchedulingOptions(1, Set.of(), Duration.ofSeconds(30));
    }

    public static ToolSchedulingOptions from(Object raw) {
        if (!(raw instanceof Map<?, ?> map)
                || map.keySet().stream().anyMatch(k -> !(k instanceof String))
                || !Set.of("mode", "maxConcurrency", "tools", "timeoutSeconds")
                        .containsAll(map.keySet()))
            throw new HarnessException(
                    "INVALID_TOOL_SCHEDULING", "Invalid scheduling configuration");
        Object mode = map.containsKey("mode") ? map.get("mode") : "serial";
        if ("serial".equals(mode) && (map.isEmpty() || map.size() == 1 && map.containsKey("mode")))
            return serial();
        if (!"readonly-parallel".equals(mode))
            throw new HarnessException(
                    "INVALID_TOOL_SCHEDULING", "Unknown or conflicting scheduling mode");
        int concurrency = number(map, "maxConcurrency", 4),
                seconds = number(map, "timeoutSeconds", 30);
        Object rawTools =
                map.containsKey("tools")
                        ? map.get("tools")
                        : List.of("list_directory", "read_file", "search_files", "grep");
        if (!(rawTools instanceof List<?> list)
                || list.size() > 32
                || list.stream().anyMatch(v -> !(v instanceof String)))
            throw new HarnessException("INVALID_TOOL_SCHEDULING", "Invalid trusted tool allowlist");
        if (seconds < 1) throw new HarnessException("INVALID_TOOL_SCHEDULING", "Invalid timeout");
        return new ToolSchedulingOptions(
                concurrency,
                new LinkedHashSet<>(list.stream().map(String.class::cast).toList()),
                Duration.ofSeconds(seconds));
    }

    private static int number(Map<?, ?> map, String key, int fallback) {
        Object raw = map.containsKey(key) ? map.get(key) : fallback;
        return Values.integer(raw, "INVALID_TOOL_SCHEDULING");
    }

    public Map<String, Object> configuration() {
        return Map.of(
                "mode",
                maxConcurrency == 1 ? "serial" : "readonly-parallel",
                "maxConcurrency",
                maxConcurrency,
                "tools",
                tools.stream().sorted().toList(),
                "timeoutMillis",
                timeout.toMillis());
    }
}
