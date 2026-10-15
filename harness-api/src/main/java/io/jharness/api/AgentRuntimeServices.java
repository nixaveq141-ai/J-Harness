package io.jharness.api;

import java.util.Objects;

/** Explicit additional runtime services; no concrete plugin implementation leaks into the loop. */
public record AgentRuntimeServices(
        PluginScope scope,
        SessionReader reader,
        SessionProjector projector,
        CheckpointPolicy checkpoints,
        ApprovalService approvals,
        ToolPipeline pipeline,
        RequestJournal requests,
        DataSanitizer sanitizer,
        CompletionEvaluator completion,
        ApiCostGuard costs) {
    public AgentRuntimeServices(
            PluginScope scope,
            SessionReader reader,
            SessionProjector projector,
            CheckpointPolicy checkpoints,
            ApprovalService approvals,
            ToolPipeline pipeline,
            RequestJournal requests,
            DataSanitizer sanitizer,
            CompletionEvaluator completion) {
        this(
                scope,
                reader,
                projector,
                checkpoints,
                approvals,
                pipeline,
                requests,
                sanitizer,
                completion,
                ApiCostGuard.unavailable());
    }

    public AgentRuntimeServices {
        Objects.requireNonNull(costs);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(reader);
        Objects.requireNonNull(projector);
        Objects.requireNonNull(checkpoints);
        Objects.requireNonNull(approvals);
        Objects.requireNonNull(pipeline);
        Objects.requireNonNull(requests);
        Objects.requireNonNull(sanitizer);
        Objects.requireNonNull(completion);
    }
}
