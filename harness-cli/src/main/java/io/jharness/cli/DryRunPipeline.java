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
        context.cancellation().throwIfCancelled();
        AgentTool tool = context.tools().require(proposed.tool(), context.scope());
        if (tool.sideEffectLevel() == SideEffectLevel.READ_ONLY)
            return delegate.execute(proposed, context);
        new JsonSchemaValidator().validate(tool.inputSchema(), proposed.arguments());
        tool.validateArguments(proposed);
        ToolInvocation call =
                context.events()
                        .invoke(StandardExtensionPoints.BEFORE_TOOL, proposed, value -> value);
        if (!call.callId().equals(proposed.callId()) || !call.tool().equals(proposed.tool()))
            throw new HarnessException("CALL_IDENTITY_CHANGED", "Preview identity changed");
        new JsonSchemaValidator().validate(tool.inputSchema(), call.arguments());
        tool.validateArguments(call);
        if (!sanitizer.sanitize(call.arguments()).equals(call.arguments()))
            throw new HarnessException(
                    "SECRET_ARGUMENT", "Sensitive preview arguments require a secure channel");
        context.cancellation().throwIfCancelled();
        call =
                new ToolInvocation(
                        call.callId(), call.tool(), call.arguments(), tool.sideEffectLevel());
        var policy =
                context.events()
                        .invoke(PolicyExtensionPoints.EVALUATE, context.policy(), value -> value);
        var decision =
                policy.evaluate(call, new ToolContext(context.scope(), context.cancellation()));
        Map<String, Object> plan;
        try {
            plan =
                    decision == null || decision == PolicyEngine.Decision.DENY
                            ? Map.of(
                                    "previewable",
                                    false,
                                    "executed",
                                    false,
                                    "reason",
                                    "PERMISSION_DENIED")
                            : preview.preview(call, context.cancellation());
        } catch (HarnessException ex) {
            if (!Set.of(
                            "FILE_EXISTS",
                            "FILE_NOT_FOUND",
                            "FILE_CONFLICT",
                            "EDIT_MATCH_CONFLICT",
                            "FILE_LIMIT",
                            "PREVIEW_IO",
                            "INVALID_EDIT")
                    .contains(ex.code())) throw ex;
            plan = Map.of("previewable", false, "executed", false, "reason", ex.code());
        }
        plan = new LinkedHashMap<>(Objects.requireNonNull(plan, "preview result"));
        plan.put("policyDecision", decision == null ? "INVALID_PROVIDER_RESULT" : decision.name());
        plan.put("approvalRequired", decision == PolicyEngine.Decision.ASK);
        plan = sanitizer.sanitize(plan);
        var projection = new ReportProjection(16384, 1024, 8192);
        plan = new LinkedHashMap<>(Values.object(projection.project(plan)));
        plan.put("executed", false);
        if (projection.truncated()) plan.put("previewTruncated", true);
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
