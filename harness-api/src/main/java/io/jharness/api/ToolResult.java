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
        if (status == ToolStatus.SUCCESS && error.isPresent())
            throw new IllegalArgumentException("Success cannot carry a failure diagnostic");
        if (status != ToolStatus.SUCCESS && error.isEmpty())
            error = Optional.of(new ErrorInfo(status.name(), status.name()));
    }

    public record ErrorInfo(String code, String message) {
        public ErrorInfo {
            code = Values.required(code, "error code");
            message = Values.required(message, "error message");
        }
    }
}
