package io.jharness.api;

import java.util.*;

/** Scrub before logging AND sending, keeping request replay equivalent to the wire request. */
public final class SecretRedactor implements DataSanitizer {
    private static final Set<String> KEYS =
            Set.of(
                    "apikey",
                    "api_key",
                    "authorization",
                    "password",
                    "secret",
                    "access_token",
                    "refresh_token");
    private final List<String> secrets;

    public SecretRedactor(List<String> secrets) {
        this.secrets =
                secrets.stream()
                        .filter(s -> s != null && !s.isEmpty())
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList();
    }

    public SecretRedactor() {
        this(List.of());
    }

    public String text(String text) {
        String clean = Objects.requireNonNull(text);
        for (String secret : secrets) clean = clean.replace(secret, "[REDACTED]");
        return clean;
    }

    @Override
    public String streamingText(String cumulative) {
        String clean = text(cumulative);
        int hold = secrets.stream().mapToInt(String::length).max().orElse(0),
                end = Math.max(0, clean.length() - hold);
        if (end > 0 && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        return clean.substring(0, end);
    }

    @Override
    public Map<String, Object> sanitize(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach(
                (key, value) ->
                        result.put(
                                key,
                                KEYS.contains(key.toLowerCase(Locale.ROOT))
                                        ? "[REDACTED]"
                                        : clean(value)));
        return Values.snapshot(result);
    }

    private Object clean(Object value) {
        if (value instanceof String text) return text(text);
        if (value instanceof Map<?, ?> map) return sanitize(Values.object(map));
        if (value instanceof List<?> list) return list.stream().map(this::clean).toList();
        return value;
    }
}
