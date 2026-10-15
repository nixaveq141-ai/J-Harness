package io.jharness.session;

import io.jharness.api.*;

/** A policy decision is not a persistence implementation; callers must enforce it. */
public final class DefaultCheckpointPolicy implements CheckpointPolicy {
    @Override
    public CheckpointDecision beforeEachModelRequest(SessionState state) {
        return CheckpointDecision.DURABLE_FLUSH;
    }

    @Override
    public CheckpointDecision beforeTopLevelTool(ToolInvocation call) {
        return call.sideEffectLevel() == SideEffectLevel.READ_ONLY
                ? CheckpointDecision.FLUSH
                : CheckpointDecision.DURABLE_FLUSH;
    }

    @Override
    public CheckpointDecision atStepBoundary(SessionState state) {
        return CheckpointDecision.DURABLE_FLUSH;
    }
}
