package io.jharness.api;

public interface CheckpointPolicy {
    CheckpointDecision beforeEachModelRequest(SessionState state);

    CheckpointDecision beforeTopLevelTool(ToolInvocation call);

    CheckpointDecision atStepBoundary(SessionState state);
}
