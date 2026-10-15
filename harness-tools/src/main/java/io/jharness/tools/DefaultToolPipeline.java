package io.jharness.tools;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serial preparation and settlement, with an explicitly separable read-only body. */
public final class DefaultToolPipeline implements ToolPipeline {
    private final JsonSchemaValidator schemas;
    private final DataSanitizer sanitizer;

    public DefaultToolPipeline(JsonSchemaValidator schemas, DataSanitizer sanitizer) {
        this.schemas = Objects.requireNonNull(schemas);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    @Override
    public ToolResult execute(ToolInvocation proposed, ToolPipelineContext context) {
        var call = prepare(proposed, context, () -> {}).orElseThrow();
        return call.finish(call.run());
    }

    @Override
    public Optional<PreparedToolCall> prepare(
            ToolInvocation proposed, ToolPipelineContext context, Runnable barrier) {
        var state = new Call(proposed, context);
        state.prepare(barrier);
        return Optional.of(state);
    }

    private final class Call implements PreparedToolCall {
        final ToolInvocation proposed;
        final ToolPipelineContext context;
        final long started = System.nanoTime();
        final AtomicBoolean entered = new AtomicBoolean(),
                dispatched = new AtomicBoolean(),
                finished = new AtomicBoolean();
        ToolInvocation call;
        AgentTool tool;
        SideEffectLevel effect = SideEffectLevel.SYSTEM_IMPACT;
        boolean parallel;
        ToolResult preliminary;
        volatile String executionFinishedAt;
        volatile ToolResult outcome;

        Call(ToolInvocation proposed, ToolPipelineContext context) {
            this.proposed = proposed;
            this.call = proposed;
            this.context = context;
        }

        void prepare(Runnable barrier) {
            try {
                context.cancellation().throwIfCancelled();
                tool = context.tools().require(proposed.tool(), context.scope());
                effect = tool.sideEffectLevel();
                schemas.validate(tool.inputSchema(), proposed.arguments());
                call =
                        context.events()
                                .invoke(
                                        StandardExtensionPoints.BEFORE_TOOL,
                                        proposed,
                                        value -> value);
                if (!call.callId().equals(proposed.callId())
                        || !call.tool().equals(proposed.tool()))
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
                var decision =
                        Objects.requireNonNull(
                                effectivePolicy.evaluate(call, toolContext(context)));
                if (decision == PolicyEngine.Decision.ASK || effect != SideEffectLevel.READ_ONLY)
                    barrier.run();
                context.cancellation().throwIfCancelled();
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
                                    decision == PolicyEngine.Decision.ALLOW,
                                    decision.name(),
                                    "policy");
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
                if (!approval.approved()) {
                    preliminary =
                            ToolResults.failure(
                                    call.callId(), ToolStatus.DENIED, "PERMISSION_DENIED");
                    return;
                }
                parallel =
                        effect == SideEffectLevel.READ_ONLY
                                && decision == PolicyEngine.Decision.ALLOW;
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
            } catch (Exception ex) {
                preliminary = failure(ex);
                parallel = false;
            }
        }

        @Override
        public boolean parallelAllowed() {
            return parallel && preliminary == null;
        }

        @Override
        public ToolResult run() {
            if (!dispatched.compareAndSet(false, true))
                throw new HarnessException(
                        "REPEATED_EXECUTION", "Prepared call already dispatched");
            if (preliminary != null) {
                outcome = preliminary;
                return preliminary;
            }
            try {
                context.cancellation().throwIfCancelled();
                ToolInvocation approved = call;
                ToolExecution guard =
                        (candidate, ignoredCancellation) -> {
                            context.cancellation().throwIfCancelled();
                            if (finished.get())
                                throw new HarnessException(
                                        "SCOPE_CLOSED", "Prepared call has been settled");
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
                var wrapped =
                        context.events()
                                .invoke(
                                        StandardExtensionPoints.TOOL_EXECUTION,
                                        guard,
                                        value -> value);
                var result = Objects.requireNonNull(wrapped.execute(call, context.cancellation()));
                if (result.status() == ToolStatus.SUCCESS && !entered.get())
                    throw new HarnessException(
                            "EXECUTION_BYPASSED", "Execution wrapper cannot fabricate success");
                validate(result, call, tool);
                var original = result;
                result =
                        context.events()
                                .invoke(StandardExtensionPoints.AFTER_TOOL, result, value -> value);
                if (result.status() != original.status())
                    throw new HarnessException(
                            "RESULT_STATUS_CHANGED",
                            "Postprocessor cannot rewrite execution status");
                validate(result, call, tool);
                outcome = result;
                return result;
            } catch (Exception ex) {
                var failed = failure(ex);
                outcome = failed;
                return failed;
            } finally {
                executionFinishedAt = Instant.now().toString();
            }
        }

        ToolResult failure(Exception ex) {
            String error = code(ex);
            ToolStatus status =
                    switch (error) {
                        case "CANCELLED" -> ToolStatus.CANCELLED;
                        case "TIMEOUT" -> ToolStatus.TIMEOUT;
                        case "PERMISSION_DENIED" -> ToolStatus.DENIED;
                        default -> ToolStatus.FAILED;
                    };
            if (entered.get() && effect != SideEffectLevel.READ_ONLY) status = ToolStatus.UNKNOWN;
            return ToolResults.failure(proposed.callId(), status, error);
        }

        @Override
        public ToolResult finish(ToolResult raw) {
            if (!finished.compareAndSet(false, true))
                throw new HarnessException("REPEATED_SETTLEMENT", "Prepared call already settled");
            if (!raw.callId().equals(proposed.callId()))
                throw new HarnessException(
                        "RESULT_CALL_MISMATCH", "Scheduler returned another callId");
            if (raw.status() == ToolStatus.SUCCESS && (!entered.get() || !raw.equals(outcome)))
                throw new HarnessException(
                        "EXECUTION_BYPASSED", "Scheduler cannot fabricate body success");
            var result = ToolResults.sanitized(raw, sanitizer);
            var payload = new LinkedHashMap<>(ToolResults.event(result));
            payload.put("tool", proposed.tool());
            payload.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            payload.put("bodyEntered", entered.get());
            payload.put("sideEffectLevel", effect.name());
            if (executionFinishedAt != null)
                payload.put("executionFinishedAt", executionFinishedAt);
            event(context, proposed, "tool.completed", payload);
            return result;
        }
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
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "CANCELLED";
        }
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
