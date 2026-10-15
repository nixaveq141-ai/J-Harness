package io.jharness.api;

import java.util.Map;

public interface AgentTool {
    String name();

    String description();

    Map<String, Object> inputSchema();

    Map<String, Object> outputSchema();

    SideEffectLevel sideEffectLevel();

    ToolResult execute(ToolInvocation args, ToolContext context) throws Exception;
}
