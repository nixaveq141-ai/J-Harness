package io.jharness.api;

import java.util.*;

@FunctionalInterface
public interface ToolBatchExecution {
    List<ToolResult> execute(
            List<ToolInvocation> calls, ToolPipelineContext context, ToolPipeline pipeline);

    /**
     * Known cancellation/timeout with observed cleanup retains its terminal reason; partial effects
     * still block later work.
     */
    static boolean unknownOutcome(ToolResult result) {
        if (result.status() == ToolStatus.UNKNOWN) return true;
        if (!Boolean.TRUE.equals(result.value().get("sideEffectUncertain"))) return false;
        return !((result.status() == ToolStatus.CANCELLED || result.status() == ToolStatus.TIMEOUT)
                && Boolean.TRUE.equals(result.value().get("observedClean"))
                && result.value().get("residualPids") instanceof List<?> residual
                && residual.isEmpty());
    }

    static boolean stops(ToolResult result) {
        return result.status() == ToolStatus.UNKNOWN
                || result.status() == ToolStatus.CANCELLED
                || result.status() == ToolStatus.TIMEOUT
                || Boolean.TRUE.equals(result.value().get("sideEffectUncertain"))
                || result.error().map(e -> "CHECKPOINT_FAILED".equals(e.code())).orElse(false);
    }

    static ToolBatchExecution serial() {
        return (calls, context, pipeline) -> {
            var results = new ArrayList<ToolResult>();
            for (ToolInvocation call : calls) {
                context.cancellation().throwIfCancelled();
                EventSupport.append(
                        context.session(),
                        context.events(),
                        context.scope(),
                        "tool.scheduled",
                        EventLevel.TOOL,
                        context.turnId(),
                        context.stepId(),
                        call.callId(),
                        Map.of(
                                "mode",
                                "serial",
                                "reason",
                                "default serial policy",
                                "maxConcurrency",
                                1));
                context.cancellation().throwIfCancelled();
                var result = pipeline.execute(call, context);
                if (result == null)
                    throw new HarnessException(
                            "TOOL_OUTCOME_UNKNOWN", "Pipeline returned no execution outcome");
                results.add(result);
                if (stops(result)) {
                    EventSupport.append(
                            context.session(),
                            context.events(),
                            context.scope(),
                            "tool.batch.stopped",
                            EventLevel.STEP,
                            context.turnId(),
                            context.stepId(),
                            null,
                            Map.of(
                                    "reason",
                                    result.status().name(),
                                    "pending",
                                    calls.size() - results.size()));
                    break;
                }
            }
            return List.copyOf(results);
        };
    }
}
