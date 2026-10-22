package io.jharness.api;

import java.util.Map;

/** Ephemeral sanitized display, never an assistant-message or executable tool fact. */
public interface ModelDisplay {
    /** Sanitized temporary text with truncation retained independently of its bounded length. */
    record Update(String text, boolean truncated) {
        public Update {
            java.util.Objects.requireNonNull(text);
        }
    }

    Map<String, Object> snapshot();
}
