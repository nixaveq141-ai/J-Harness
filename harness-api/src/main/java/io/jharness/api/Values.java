package io.jharness.api;

import java.util.*;

/** Deep immutable, JSON-compatible snapshots. Arbitrary mutable objects are rejected. */
public final class Values {
    private Values() {}

    public static String required(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public static Map<String, Object> snapshot(Map<String, ?> value) {
        Objects.requireNonNull(value);
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, item) -> copy.put(required(key, "key"), freeze(item)));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map))
            throw new HarnessException("INVALID_JSON", "Expected a JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach(
                (key, item) -> {
                    if (!(key instanceof String text))
                        throw new HarnessException("INVALID_JSON", "Expected string key");
                    result.put(text, item);
                });
        return snapshot(result);
    }

    private static Object freeze(Object value) {
        return freeze(value, 0);
    }

    private static Object freeze(Object value, int depth) {
        if (depth > 64)
            throw new IllegalArgumentException(
                    "JSON nesting exceeds 64 levels or contains a cycle");
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Double d && Double.isFinite(d)) return d;
        if (value instanceof Float f && Float.isFinite(f)) return f;
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(freeze(item, depth + 1)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach(
                    (key, item) -> {
                        if (!(key instanceof String text))
                            throw new IllegalArgumentException("JSON key must be a string");
                        copy.put(required(text, "key"), freeze(item, depth + 1));
                    });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    }
}
