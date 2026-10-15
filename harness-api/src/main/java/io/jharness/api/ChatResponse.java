package io.jharness.api;

import java.util.*;

public record ChatResponse(String text, List<ToolInvocation> toolCalls, Map<String, Object> usage) {
    public ChatResponse {
        Objects.requireNonNull(text);
        toolCalls = List.copyOf(toolCalls);
        usage = Values.snapshot(usage);
    }
}
