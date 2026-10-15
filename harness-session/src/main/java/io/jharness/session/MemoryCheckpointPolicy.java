package io.jharness.session;

import io.jharness.api.*;

/** Isolated tests only; never authorize real local side effects with this policy. */
public final class MemoryCheckpointPolicy implements CheckpointPolicy {
    @Override
    public CheckpointDecision beforeEachModelRequest(SessionState state) {
        return CheckpointDecision.MEMORY_ONLY;
    }

    @Override
    public CheckpointDecision beforeTopLevelTool(ToolInvocation call) {
        return CheckpointDecision.MEMORY_ONLY;
    }

    @Override
    public CheckpointDecision atStepBoundary(SessionState state) {
        return CheckpointDecision.MEMORY_ONLY;
    }
}
