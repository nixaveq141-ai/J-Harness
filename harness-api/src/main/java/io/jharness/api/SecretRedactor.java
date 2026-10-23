package io.jharness.api;

import java.util.*;

/** Scrub before logging AND sending, keeping request replay equivalent to the wire request. */
public final class SecretRedactor implements DataSanitizer {
    private static final Set<String> KEYS =
            Set.of(
                    "apikey",
                    "xapikey",
                    "authorization",
                    "password",
                    "secret",
                    "accesstoken",
                    "refreshtoken",
                    "clientsecret",
                    "cookie",
                    "setcookie",
                    "token",
                    "credential",
                    "privatekey");
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
        String clean = Objects.requireNonNull(text, "text");
        for (String secret : secrets) clean = clean.replace(secret, "[REDACTED]");
        return clean;
    }

    @Override
    public String streamingText(String cumulative) {
        return streamingUpdate(cumulative).text();
    }

    public ModelDisplay.Update streamingUpdate(String cumulative) {
        Objects.requireNonNull(cumulative, "cumulative");
        boolean truncated = cumulative.length() > 65536;
        if (cumulative.length() > 65536) cumulative = cumulative.substring(0, 65536);
        int end = cumulative.length();
        // Hold only a suffix that could become a known secret on a later delta.
        for (String secret : secrets) {
            int max = Math.min(secret.length() - 1, cumulative.length());
            for (int size = max; size > 0; size--) {
                if (cumulative.regionMatches(cumulative.length() - size, secret, 0, size)) {
                    end = Math.min(end, cumulative.length() - size);
                    break;
                }
            }
        }
        // A known complete secret spanning the cut must be withheld in its entirety.
        boolean adjusted;
        do {
            adjusted = false;
            for (String secret : secrets) {
                for (int at = cumulative.indexOf(secret);
                        at >= 0 && at < end;
                        at = cumulative.indexOf(secret, at + 1)) {
                    if (at + secret.length() > end) {
                        end = at;
                        adjusted = true;
                        break;
                    }
                }
            }
        } while (adjusted);
        if (end > 0 && Character.isHighSurrogate(cumulative.charAt(end - 1))) end--;
        String clean = text(cumulative.substring(0, end));
        truncated |= clean.length() > 65536;
        int cut = Math.min(65536, clean.length());
        int marker = clean.lastIndexOf("[REDACTED]", Math.max(0, cut - 1));
        if (marker >= 0 && marker < cut && marker + "[REDACTED]".length() > cut) cut = marker;
        if (cut > 0 && Character.isHighSurrogate(clean.charAt(cut - 1))) cut--;
        return new ModelDisplay.Update(clean.substring(0, cut), truncated);
    }

    @Override
    public Map<String, Object> sanitize(Map<String, Object> data) {
        data = Values.snapshot(Objects.requireNonNull(data, "data"));
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach(
                (key, value) -> {
                    String safeKey = text(key);
                    if (result.containsKey(safeKey))
                        throw new HarnessException(
                                "REDACTION_KEY_COLLISION", "Redacted keys are ambiguous");
                    result.put(
                            safeKey,
                            KEYS.contains(
                                            key.toLowerCase(Locale.ROOT)
                                                    .replace("-", "")
                                                    .replace("_", ""))
                                    ? "[REDACTED]"
                                    : clean(value));
                });
        return Values.snapshot(result);
    }

    private Object clean(Object value) {
        if (value instanceof String text) return text(text);
        if (value instanceof Map<?, ?> map) return sanitize(Values.object(map));
        if (value instanceof List<?> list) return list.stream().map(this::clean).toList();
        return value;
    }
}
