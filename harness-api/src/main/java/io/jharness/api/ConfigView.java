package io.jharness.api;

import java.util.*;

/** Read-only plugin-specific configuration; never render secrets into events. */
public record ConfigView(Map<String, Object> values) {
    public ConfigView {
        values = Values.snapshot(values);
    }

    public static ConfigView empty() {
        return new ConfigView(Map.of());
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
