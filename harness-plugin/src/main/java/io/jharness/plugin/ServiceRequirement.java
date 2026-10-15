package io.jharness.plugin;

import java.util.Objects;

/** A null key requests unique resolution and detects startup ambiguity. */
public record ServiceRequirement(Class<?> type, String key) {
    public ServiceRequirement {
        Objects.requireNonNull(type);
    }

    public static ServiceRequirement unique(Class<?> type) {
        return new ServiceRequirement(type, null);
    }
}
