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
        Objects.requireNonNull(scope);
        turnId = Values.required(turnId, "turnId");
        stepId = Values.required(stepId, "stepId");
        Objects.requireNonNull(tools);
        Objects.requireNonNull(session);
        Objects.requireNonNull(events);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(approvals);
        Objects.requireNonNull(checkpoints);
        Objects.requireNonNull(cancellation);
    }
}
