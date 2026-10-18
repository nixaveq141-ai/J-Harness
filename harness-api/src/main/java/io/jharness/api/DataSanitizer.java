package io.jharness.api;

import java.util.Map;

@FunctionalInterface
public interface DataSanitizer {
    Map<String, Object> sanitize(Map<String, Object> data);

    /**
     * Safe prefix of a cumulative display, withholding every known secret's trailing window.
     * Unknown implementations fail closed for provisional display; final facts still use sanitize.
     */
    default String streamingText(String cumulative) {
        return "";
    }

    static DataSanitizer chain(DataSanitizer first, DataSanitizer second) {
        java.util.Objects.requireNonNull(first);
        java.util.Objects.requireNonNull(second);
        return new DataSanitizer() {
            public Map<String, Object> sanitize(Map<String, Object> data) {
                return second.sanitize(first.sanitize(data));
            }

            public String streamingText(String cumulative) {
                return second.streamingText(first.streamingText(cumulative));
            }
        };
    }
}
