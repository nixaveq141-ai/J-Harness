package io.jharness.api;

import java.util.*;

public final class ToolResults {
    private ToolResults() {}

    public static ToolResult failure(String id, ToolStatus status, String code) {
        return new ToolResult(
                id,
                status,
                Map.of(),
                code,
                Map.of(),
                Optional.of(new ToolResult.ErrorInfo(code, code)));
    }

    public static Map<String, Object> event(ToolResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.status().name());
        out.put("value", result.value());
        out.put("content", result.modelContent());
        out.put("display", result.display());
        result.error()
                .ifPresent(
                        error ->
                                out.put(
                                        "error",
                                        Map.of("code", error.code(), "message", error.message())));
        return Values.snapshot(out);
    }

    public static ToolResult sanitized(ToolResult result, DataSanitizer sanitizer) {
        Map<String, Object> payload = sanitizer.sanitize(event(result));
        Optional<ToolResult.ErrorInfo> error = Optional.empty();
        if (payload.containsKey("error")) {
            Map<String, Object> e = Values.object(payload.get("error"));
            error =
                    Optional.of(
                            new ToolResult.ErrorInfo(
                                    (String) e.get("code"), (String) e.get("message")));
        }
        return new ToolResult(
                result.callId(),
                result.status(),
                Values.object(payload.get("value")),
                (String) payload.get("content"),
                Values.object(payload.get("display")),
                error);
    }
}
