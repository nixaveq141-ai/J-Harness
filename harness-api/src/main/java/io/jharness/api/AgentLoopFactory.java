package io.jharness.api;

public interface AgentLoopFactory {
    AgentLoop create(AgentLoopDependencies dependencies);
}
