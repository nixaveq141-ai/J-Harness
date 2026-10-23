package io.jharness.api;

import java.util.Objects;

public record ToolPipelineContext(
        PluginScope scope,
        String turnId,
        String stepId,
        ToolRegistry tools,
        SessionWriter session,
        EventBus events,
        PolicyEngine policy,
        ApprovalService approvals,
        CheckpointPolicy checkpoints,
        CancellationToken cancellation) {
    public ToolPipelineContext {
        Objects.requireNonNull(scope, "scope");
        turnId = Values.required(turnId, "turnId");
        stepId = Values.required(stepId, "stepId");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(approvals, "approvals");
        Objects.requireNonNull(checkpoints, "checkpoints");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
