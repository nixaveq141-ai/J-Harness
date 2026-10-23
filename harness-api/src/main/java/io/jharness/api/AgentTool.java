package io.jharness.api;

import java.util.Map;

/**
 * Stable immutable metadata for one registration; declarations cannot be null or change over time.
 */
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

    /** Pure semantic validation, called before approval/intent/body. Must not acquire resources. */
    default void validateArguments(ToolInvocation call) {}

    ToolResult execute(ToolInvocation args, ToolContext context) throws Exception;
}
