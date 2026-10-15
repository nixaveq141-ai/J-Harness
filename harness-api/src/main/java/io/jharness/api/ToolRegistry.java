package io.jharness.api;

import java.util.*;

public interface ToolRegistry {
    default AgentTool require(String name, PluginScope scope) {
        return resolve(name, scope)
                .orElseThrow(
                        () -> new HarnessException("UNKNOWN_TOOL", "Tool is unavailable: " + name));
    }

    Registration register(AgentTool tool, PluginScope scope);

    Optional<AgentTool> resolve(String name, PluginScope scope);

    List<AgentTool> visibleTools(PluginScope scope);
}
