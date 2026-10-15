package io.jharness.tools;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stateless pipeline; every invocation receives its exact session, policy and ownership context.
 */
public final class DefaultToolPipeline implements ToolPipeline {
    private final JsonSchemaValidator schemas;
    private final DataSanitizer sanitizer;

    public DefaultToolPipeline(JsonSchemaValidator schemas, DataSanitizer sanitizer) {
        this.schemas = Objects.requireNonNull(schemas);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    @Override
    public ToolResult execute(ToolInvocation proposed, ToolPipelineContext context) {
        long started = System.nanoTime();
        ToolInvocation call = proposed;
        AtomicBoolean entered = new AtomicBoolean();
        SideEffectLevel effect = SideEffectLevel.SYSTEM_IMPACT;
        ToolResult result;
        try {
            context.cancellation().throwIfCancelled();
            AgentTool tool = context.tools().require(proposed.tool(), context.scope());
            effect = tool.sideEffectLevel();
            schemas.validate(tool.inputSchema(), proposed.arguments());
            call =
                    context.events()
                            .invoke(StandardExtensionPoints.BEFORE_TOOL, proposed, value -> value);
            if (!call.callId().equals(proposed.callId()) || !call.tool().equals(proposed.tool()))
                throw new HarnessException(
                        "CALL_IDENTITY_CHANGED", "Interceptor cannot change call identity");
            schemas.validate(tool.inputSchema(), call.arguments());
            if (!sanitizer.sanitize(call.arguments()).equals(call.arguments()))
                throw new HarnessException(
                        "SECRET_ARGUMENT",
                        "Sensitive tool arguments require an explicit secure channel");
            call = new ToolInvocation(call.callId(), tool.name(), call.arguments(), effect);
            event(context, call, "tool.requested", invocation(call));
            PolicyEngine effectivePolicy =
                    context.events()
                            .invoke(
                                    PolicyExtensionPoints.EVALUATE,
                                    context.policy(),
                                    value -> value);
            PolicyEngine.Decision decision =
                    Objects.requireNonNull(effectivePolicy.evaluate(call, toolContext(context)));
            ApprovalService.ApprovalDecision approval;
            if (decision == PolicyEngine.Decision.ASK) {
                event(context, call, "approval.requested", invocation(call));
                try {
                    approval =
                            Objects.requireNonNull(
                                    context.approvals().decide(call, toolContext(context)));
                } catch (HarnessException ex) {
                    event(
                            context,
                            call,
                            "approval.decided",
                            Map.of("approved", false, "reason", ex.code(), "actor", "system"));
                    throw ex;
                }
            } else
                approval =
                        new ApprovalService.ApprovalDecision(
                                decision == PolicyEngine.Decision.ALLOW, decision.name(), "policy");
            event(
                    context,
                    call,
                    "approval.decided",
                    sanitizer.sanitize(
                            Map.of(
                                    "approved",
                                    approval.approved(),
                                    "reason",
                                    approval.reason(),
                                    "actor",
                                    approval.actor())));
            context.cancellation().throwIfCancelled();
            if (!approval.approved())
                result = ToolResults.failure(call.callId(), ToolStatus.DENIED, "PERMISSION_DENIED");
            else {
                event(context, call, "tool.intent", invocation(call));
                CheckpointDecision checkpoint = context.checkpoints().beforeTopLevelTool(call);
                if (effect != SideEffectLevel.READ_ONLY)
                    checkpoint = CheckpointDecision.DURABLE_FLUSH;
                try {
                    EventSupport.checkpoint(context.session(), checkpoint);
                } catch (RuntimeException ex) {
                    context.events()
                            .publish(
                                    SessionEvent.session(
                                            context.scope().sessionId(),
                                            "checkpoint.failed",
                                            Map.of("callId", call.callId(), "code", code(ex))));
                    throw ex;
                }
                event(context, call, "tool.started", invocation(call));
                ToolInvocation approved = call;
                ToolExecution guard =
                        (candidate, ignoredCancellation) -> {
                            context.cancellation().throwIfCancelled();
                            if (!approved.equals(candidate))
                                throw new HarnessException(
                                        "APPROVAL_MISMATCH",
                                        "Execution parameters differ from approved intent");
                            if (context.tools().require(approved.tool(), context.scope()) != tool)
                                throw new HarnessException(
                                        "TOOL_CHANGED", "Tool registration changed after approval");
                            if (!entered.compareAndSet(false, true))
                                throw new HarnessException(
                                        "REPEATED_EXECUTION",
                                        "A top-level call cannot enter its body twice");
                            return tool.execute(approved, toolContext(context));
                        };
                ToolExecution wrapped =
                        context.events()
                                .invoke(
                                        StandardExtensionPoints.TOOL_EXECUTION,
                                        guard,
                                        value -> value);
                result = Objects.requireNonNull(wrapped.execute(call, context.cancellation()));
                if (result.status() == ToolStatus.SUCCESS && !entered.get())
                    throw new HarnessException(
                            "EXECUTION_BYPASSED", "Execution wrapper cannot fabricate success");
                validate(result, call, tool);
                ToolResult original = result;
                result =
                        context.events()
                                .invoke(StandardExtensionPoints.AFTER_TOOL, result, value -> value);
                if (result.status() != original.status())
                    throw new HarnessException(
                            "RESULT_STATUS_CHANGED",
                            "Postprocessor cannot rewrite execution status");
                validate(result, call, tool);
            }
        } catch (Exception ex) {
            String code = code(ex);
            ToolStatus status =
                    switch (code) {
                        case "CANCELLED" -> ToolStatus.CANCELLED;
                        case "TIMEOUT" -> ToolStatus.TIMEOUT;
                        case "PERMISSION_DENIED" -> ToolStatus.DENIED;
                        default -> ToolStatus.FAILED;
                    };
            if (entered.get() && effect != SideEffectLevel.READ_ONLY) status = ToolStatus.UNKNOWN;
            result = ToolResults.failure(proposed.callId(), status, code);
        }
        result = ToolResults.sanitized(result, sanitizer);
        Map<String, Object> payload = new LinkedHashMap<>(ToolResults.event(result));
        payload.put("tool", proposed.tool());
        payload.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        payload.put("bodyEntered", entered.get());
        payload.put("sideEffectLevel", effect.name());
        event(context, proposed, "tool.completed", payload);
        return result;
    }

    private void validate(ToolResult result, ToolInvocation call, AgentTool tool) {
        if (!result.callId().equals(call.callId()))
            throw new HarnessException("RESULT_CALL_MISMATCH", "Tool returned another callId");
        if (result.status() == ToolStatus.SUCCESS) {
            try {
                schemas.validate(tool.outputSchema(), result.value());
            } catch (HarnessException ex) {
                throw new HarnessException(
                        "INVALID_TOOL_OUTPUT", "Output schema validation failed");
            }
        }
    }

    private static String code(Exception ex) {
        return ex instanceof HarnessException h ? h.code() : "TOOL_FAILED";
    }

    private static ToolContext toolContext(ToolPipelineContext c) {
        return new ToolContext(c.scope(), c.cancellation());
    }

    private static Map<String, Object> invocation(ToolInvocation call) {
        return Map.of(
                "tool",
                call.tool(),
                "arguments",
                call.arguments(),
                "sideEffectLevel",
                call.sideEffectLevel().name());
    }

    private static void event(
            ToolPipelineContext c, ToolInvocation call, String type, Map<String, Object> payload) {
        EventSupport.append(
                c.session(),
                c.events(),
                c.scope(),
                type,
                EventLevel.TOOL,
                c.turnId(),
                c.stepId(),
                call.callId(),
                payload);
    }
}
