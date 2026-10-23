package io.jharness.plugin;

import java.util.Objects;

/** A null key requests unique resolution and detects startup ambiguity. */
public record ServiceRequirement(Class<?> type, String key) {
    public ServiceRequirement {
        Objects.requireNonNull(type, "type");
        if (key != null) io.jharness.api.Values.required(key, "key");
    }

    public static ServiceRequirement unique(Class<?> type) {
        return new ServiceRequirement(type, null);
    }
}
