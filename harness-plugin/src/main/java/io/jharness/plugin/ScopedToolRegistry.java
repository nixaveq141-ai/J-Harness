package io.jharness.plugin;

import io.jharness.api.*;
import java.util.*;

/** Registry infrastructure only: it never executes or approves a tool. */
public final class ScopedToolRegistry implements ToolRegistry {
    private final Map<Key, AgentTool> tools = new LinkedHashMap<>();

    @Override
    public synchronized Registration register(AgentTool tool, PluginScope scope) {
        Objects.requireNonNull(tool);
        Objects.requireNonNull(scope);
        Key key = new Key(Values.required(tool.name(), "tool name"), scope);
        if (tools.containsKey(key))
            throw new HarnessException("DUPLICATE_TOOL", "Duplicate tool: " + key.name);
        tools.put(key, tool);
        return new Registration() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (ScopedToolRegistry.this) {
                    if (!closed) {
                        closed = true;
                        tools.remove(key);
                    }
                }
            }
        };
    }

    @Override
    public synchronized Optional<AgentTool> resolve(String name, PluginScope scope) {
        return Optional.ofNullable(tools.get(new Key(name, scope)));
    }

    @Override
    public synchronized List<AgentTool> visibleTools(PluginScope scope) {
        return tools.entrySet().stream()
                .filter(e -> e.getKey().scope.equals(scope))
                .map(Map.Entry::getValue)
                .toList();
    }

    private record Key(String name, PluginScope scope) {}
}
