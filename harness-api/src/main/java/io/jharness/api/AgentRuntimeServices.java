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
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(checkpoints, "checkpoints");
        Objects.requireNonNull(approvals, "approvals");
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(sanitizer, "sanitizer");
        Objects.requireNonNull(completion, "completion");
    }
}
