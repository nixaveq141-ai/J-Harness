package io.jharness.api;

import java.util.*;

/** Full request envelope; no second authoritative history is stored here. */
public record ChatRequest(
        List<Map<String, Object>> messages,
        List<Map<String, Object>> tools,
        Map<String, Object> parameters) {
    public ChatRequest {
        messages =
                Objects.requireNonNull(messages, "messages").stream()
                        .map(
                                message ->
                                        message instanceof SourcedMessage
                                                ? message
                                                : Values.snapshot(message))
                        .toList();
        tools = Objects.requireNonNull(tools, "tools").stream().map(Values::snapshot).toList();
        parameters = Values.snapshot(Objects.requireNonNull(parameters, "parameters"));
    }
}
