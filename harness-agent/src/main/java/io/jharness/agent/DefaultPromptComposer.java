package io.jharness.agent;

import io.jharness.api.*;
import java.util.*;

/** Always reads current event projection and scoped declarations, never a parallel chat array. */
public final class DefaultPromptComposer implements PromptComposer {
    private final ToolRegistry tools;
    private final PluginScope scope;
    private final Map<String, Object> parameters;

    public DefaultPromptComposer(
            ToolRegistry tools, PluginScope scope, Map<String, Object> parameters) {
        this.tools = Objects.requireNonNull(tools);
        this.scope = Objects.requireNonNull(scope);
        this.parameters = Values.snapshot(parameters);
    }

    @Override
    public ChatRequest compose(SessionProjection projection) {
        List<Map<String, Object>> declarations =
                tools.visibleTools(scope).stream()
                        .map(
                                tool -> {
                                    try {
                                        new JsonSchema().checkSchema(tool.inputSchema());
                                        return Map.<String, Object>of(
                                                "type",
                                                "function",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        tool.name(),
                                                        "description",
                                                        tool.description(),
                                                        "parameters",
                                                        tool.inputSchema()));
                                    } catch (RuntimeException ex) {
                                        throw new HarnessException(
                                                "INVALID_TOOL_DECLARATION",
                                                "Invalid declaration for tool: " + tool.name(),
                                                ex);
                                    }
                                })
                        .toList();
        return new ChatRequest(projection.messages(), declarations, parameters);
    }
}
