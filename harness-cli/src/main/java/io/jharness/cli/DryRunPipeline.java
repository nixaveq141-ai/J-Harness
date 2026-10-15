package io.jharness.cli;

import io.jharness.api.*;
import io.jharness.tools.JsonSchemaValidator;
import java.util.*;

final class DryRunPipeline implements ToolPipeline {
    private final ToolPipeline delegate;
    private final PlanPreviewer preview;
    private final DataSanitizer sanitizer;

    DryRunPipeline(ToolPipeline delegate, PlanPreviewer preview, DataSanitizer sanitizer) {
        this.delegate = delegate;
        this.preview = preview;
        this.sanitizer = sanitizer;
    }

    @Override
    public ToolResult execute(ToolInvocation proposed, ToolPipelineContext context) {
        AgentTool tool = context.tools().require(proposed.tool(), context.scope());
        if (tool.sideEffectLevel() == SideEffectLevel.READ_ONLY)
            return delegate.execute(proposed, context);
        ToolInvocation call =
                context.events()
                        .invoke(StandardExtensionPoints.BEFORE_TOOL, proposed, value -> value);
        if (!call.callId().equals(proposed.callId()) || !call.tool().equals(proposed.tool()))
            throw new HarnessException("CALL_IDENTITY_CHANGED", "Preview identity changed");
        new JsonSchemaValidator().validate(tool.inputSchema(), call.arguments());
        context.cancellation().throwIfCancelled();
        call =
                new ToolInvocation(
                        call.callId(), call.tool(), call.arguments(), tool.sideEffectLevel());
        var policy =
                context.events()
                        .invoke(PolicyExtensionPoints.EVALUATE, context.policy(), value -> value);
        Map<String, Object> plan =
                policy.evaluate(call, new ToolContext(context.scope(), context.cancellation()))
                                == PolicyEngine.Decision.DENY
                        ? Map.of(
                                "previewable",
                                false,
                                "executed",
                                false,
                                "reason",
                                "PERMISSION_DENIED")
                        : preview.preview(call, context.cancellation());
        plan = sanitizer.sanitize(plan);
        EventSupport.append(
                context.session(),
                context.events(),
                context.scope(),
                "tool.preview",
                EventLevel.TOOL,
                context.turnId(),
                context.stepId(),
                call.callId(),
                plan);
        var result =
                new ToolResult(
                        call.callId(),
                        ToolStatus.DENIED,
                        plan,
                        Json.text(plan),
                        Map.of(),
                        Optional.of(new ToolResult.ErrorInfo("DRY_RUN_NOT_EXECUTED", "Plan only")));
        var payload = new LinkedHashMap<>(ToolResults.event(result));
        payload.put("tool", call.tool());
        payload.put("bodyEntered", false);
        payload.put("sideEffectLevel", call.sideEffectLevel().name());
        EventSupport.append(
                context.session(),
                context.events(),
                context.scope(),
                "tool.completed",
                EventLevel.TOOL,
                context.turnId(),
                context.stepId(),
                call.callId(),
                payload);
        return result;
    }
}
