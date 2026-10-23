package io.jharness.api;

import java.util.*;

public record ChatResponse(
        String text,
        List<ToolInvocation> toolCalls,
        Map<String, Object> usage,
        Map<String, Object> providerState) {
    public ChatResponse(String text, List<ToolInvocation> toolCalls, Map<String, Object> usage) {
        this(text, toolCalls, usage, Map.of());
    }

    public ChatResponse {
        Objects.requireNonNull(text, "text");
        toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls"));
        usage = Values.snapshot(Objects.requireNonNull(usage, "usage"));
        providerState = Values.snapshot(Objects.requireNonNull(providerState, "providerState"));
    }
}
