package io.jharness.api;

import java.util.*;

public record ToolResult(
        String callId,
        ToolStatus status,
        Map<String, Object> value,
        String modelContent,
        Map<String, Object> display,
        Optional<ErrorInfo> error) {
    public ToolResult {
        callId = Values.required(callId, "callId");
        Objects.requireNonNull(status);
        value = Values.snapshot(value);
        Objects.requireNonNull(modelContent);
        display = Values.snapshot(display);
        Objects.requireNonNull(error);
    }

    public record ErrorInfo(String code, String message) {}
}
