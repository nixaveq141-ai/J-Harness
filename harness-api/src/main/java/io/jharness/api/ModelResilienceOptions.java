package io.jharness.api;

import java.util.*;

/** Host-only limits, milliseconds; disabled preserves the adapter's legacy behavior. */
public record ModelResilienceOptions(
        boolean enabled,
        int maxAttempts,
        int backoffMillis,
        int totalTimeoutMillis,
        int minIntervalMillis,
        int maxWaiters) {
    public ModelResilienceOptions {
        if (maxAttempts < 1
                || maxAttempts > 4
                || backoffMillis < 0
                || backoffMillis > 10000
                || totalTimeoutMillis < 50
                || totalTimeoutMillis > 300000
                || minIntervalMillis < 0
                || minIntervalMillis > 60000
                || maxWaiters < 0
                || maxWaiters > 32)
            throw new HarnessException(
                    "INVALID_MODEL_RESILIENCE", "Invalid model resilience limits");
    }

    public static ModelResilienceOptions disabled() {
        return new ModelResilienceOptions(false, 1, 100, 30000, 0, 0);
    }

    public static ModelResilienceOptions from(Object raw) {
        if (!(raw instanceof Map<?, ?> m)
                || m.keySet().stream().anyMatch(k -> !(k instanceof String))
                || !Set.of(
                                "enabled",
                                "maxAttempts",
                                "backoffMillis",
                                "totalTimeoutMillis",
                                "minIntervalMillis",
                                "maxWaiters")
                        .containsAll(m.keySet()))
            throw new HarnessException(
                    "INVALID_MODEL_RESILIENCE", "Invalid model resilience configuration");
        Object enabled = m.containsKey("enabled") ? m.get("enabled") : false;
        if (!(enabled instanceof Boolean flag))
            throw new HarnessException("INVALID_MODEL_RESILIENCE", "Expected boolean enabled");
        return new ModelResilienceOptions(
                flag,
                number(m, "maxAttempts", 1),
                number(m, "backoffMillis", 100),
                number(m, "totalTimeoutMillis", 30000),
                number(m, "minIntervalMillis", 0),
                number(m, "maxWaiters", 0));
    }

    public Map<String, Object> configuration() {
        return Map.of(
                "enabled",
                enabled,
                "maxAttempts",
                maxAttempts,
                "backoffMillis",
                backoffMillis,
                "totalTimeoutMillis",
                totalTimeoutMillis,
                "minIntervalMillis",
                minIntervalMillis,
                "maxWaiters",
                maxWaiters);
    }

    private static int number(Map<?, ?> m, String key, int fallback) {
        Object raw = m.containsKey(key) ? m.get(key) : fallback;
        return Values.integer(raw, "INVALID_MODEL_RESILIENCE");
    }
}
