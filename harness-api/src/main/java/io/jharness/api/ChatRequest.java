package io.jharness.api;

import java.util.*;

/** Full request envelope; no second authoritative history is stored here. */
public record ChatRequest(
        List<Map<String, Object>> messages,
        List<Map<String, Object>> tools,
        Map<String, Object> parameters) {
    public ChatRequest {
        messages = messages.stream().map(Values::snapshot).toList();
        tools = tools.stream().map(Values::snapshot).toList();
        parameters = Values.snapshot(parameters);
    }
}
