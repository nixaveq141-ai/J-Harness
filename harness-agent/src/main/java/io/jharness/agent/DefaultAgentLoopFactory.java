package io.jharness.agent;

import io.jharness.api.*;

public final class DefaultAgentLoopFactory implements AgentLoopFactory {
    private final AgentRuntimeServices services;

    public DefaultAgentLoopFactory(AgentRuntimeServices services) {
        this.services = java.util.Objects.requireNonNull(services);
    }

    @Override
    public AgentLoop create(AgentLoopDependencies dependencies) {
        return new DefaultAgentLoop(dependencies, services);
    }
}
