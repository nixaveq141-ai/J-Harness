package io.jharness.api;

import java.util.Map;

public interface AgentTool {
    String name();

    String description();

    Map<String, Object> inputSchema();

    Map<String, Object> outputSchema();

    SideEffectLevel sideEffectLevel();

    /**
     * Interactive/stateful tools can require owner-thread execution even on a read-only allowlist.
     */
    default boolean requiresSerialExecution() {
        return false;
    }

    ToolResult execute(ToolInvocation args, ToolContext context) throws Exception;
}
