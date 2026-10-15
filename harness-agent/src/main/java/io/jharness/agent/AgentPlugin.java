package io.jharness.agent;

import io.jharness.api.*;

public final class AgentPlugin implements HarnessPlugin {
    private final AgentRuntimeServices services;

    public AgentPlugin(AgentRuntimeServices services) {
        this.services = java.util.Objects.requireNonNull(services);
    }

    @Override
    public String id() {
        return "agent-default";
    }

    @Override
    public String version() {
        return "1.1";
    }

    @Override
    public void start(PluginContext context) {
        if (!context.scope().equals(services.scope()))
            throw new HarnessException("SCOPE_DENIED", "Agent services belong to another scope");
        context.register(AgentLoopFactory.class, "default", new DefaultAgentLoopFactory(services));
    }

    @Override
    public void stop() {}
}
