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
        Objects.requireNonNull(value, "value");
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) freeze(value, 0, new int[] {100000});
        return copy;
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
        try {
            return snapshot(result);
        } catch (IllegalArgumentException ex) {
            throw new HarnessException("INVALID_JSON", "Invalid bounded JSON object", ex);
        }
    }

    /** Exact integer conversion; fractional and overflowing numbers are rejected. */
    public static int integer(Object value, String code) {
        try {
            if (!(value instanceof Number)) throw new ArithmeticException();
            return new java.math.BigDecimal(value.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new HarnessException(code, "Expected bounded integer", ex);
        }
    }

    private static Object freeze(Object value, int depth, int[] remaining) {
        if (--remaining[0] < 0 || depth > 64)
            throw new IllegalArgumentException(
                    "JSON exceeds node/depth limits or contains a cycle");
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Double d) {
            if (!Double.isFinite(d))
                throw new IllegalArgumentException("JSON number must be finite");
            return d;
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f))
                throw new IllegalArgumentException("JSON number must be finite");
            return f;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(freeze(item, depth + 1, remaining)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach(
                    (key, item) -> {
                        if (!(key instanceof String text))
                            throw new IllegalArgumentException("JSON key must be a string");
                        copy.put(text, freeze(item, depth + 1, remaining));
                    });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    }
}
