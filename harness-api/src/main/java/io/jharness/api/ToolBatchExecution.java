package io.jharness.api;

import java.util.*;

@FunctionalInterface
public interface ToolBatchExecution {
    List<ToolResult> execute(
            List<ToolInvocation> calls, ToolPipelineContext context, ToolPipeline pipeline);

    static boolean stops(ToolResult result) {
        return result.status() == ToolStatus.UNKNOWN
                || result.status() == ToolStatus.CANCELLED
                || result.status() == ToolStatus.TIMEOUT
                || Boolean.TRUE.equals(result.value().get("sideEffectUncertain"))
                || result.error().map(e -> e.code().equals("CHECKPOINT_FAILED")).orElse(false);
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
                var result = pipeline.execute(call, context);
                results.add(result);
                if (stops(result)) break;
            }
            return List.copyOf(results);
        };
    }
}
