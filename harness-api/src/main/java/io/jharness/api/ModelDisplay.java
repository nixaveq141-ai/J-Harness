package io.jharness.api;

import java.util.Map;

/** Ephemeral sanitized display, never an assistant-message or executable tool fact. */
public interface ModelDisplay {
    Map<String, Object> snapshot();
}
