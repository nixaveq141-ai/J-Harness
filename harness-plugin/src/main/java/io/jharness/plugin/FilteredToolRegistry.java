package io.jharness.plugin;

import io.jharness.api.*;
import java.util.*;

/** Disabled tools cannot be resolved, even if a model guesses their names. */
public final class FilteredToolRegistry implements ToolRegistry {
    private final ToolRegistry delegate;
    private final Set<String> allowed;

    public FilteredToolRegistry(ToolRegistry delegate, Set<String> allowed) {
        this.delegate = Objects.requireNonNull(delegate);
        this.allowed = Set.copyOf(allowed);
    }

    public void validate(PluginScope scope) {
        for (String name : allowed)
            if (delegate.resolve(name, scope).isEmpty())
                throw new HarnessException(
                        "CONFIG_TOOL_UNAVAILABLE", "Configured tool is not registered: " + name);
    }

    @Override
    public Registration register(AgentTool tool, PluginScope scope) {
        return delegate.register(tool, scope);
    }

    @Override
    public Optional<AgentTool> resolve(String name, PluginScope scope) {
        return allowed.contains(name) ? delegate.resolve(name, scope) : Optional.empty();
    }

    @Override
    public List<AgentTool> visibleTools(PluginScope scope) {
        return delegate.visibleTools(scope).stream()
                .filter(t -> allowed.contains(t.name()))
                .toList();
    }
}
