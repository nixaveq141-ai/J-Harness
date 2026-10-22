package io.jharness.agent;

import io.jharness.api.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serial, event-derived runtime. It never imports an LLM, storage or tool implementation. */
public final class DefaultAgentLoop implements AgentLoop {
    private final AgentLoopDependencies core;
    private final AgentRuntimeServices runtime;
    private final AtomicBoolean started = new AtomicBoolean();

    public DefaultAgentLoop(AgentLoopDependencies core, AgentRuntimeServices runtime) {
        this.core = Objects.requireNonNull(core);
        this.runtime = Objects.requireNonNull(runtime);
    }

    @Override
    public void run(CancellationToken requestedCancellation) {
        if (!started.compareAndSet(false, true))
            throw new HarnessException("LOOP_ALREADY_RUN", "Create a new loop for each turn");
        long start = System.nanoTime();
        long duration = core.budget().maxDuration().toNanos();
        CancellationToken cancellation =
                () ->
                        core.cancellation().isCancelled()
                                || requestedCancellation.isCancelled()
                                || System.nanoTime() - start >= duration;
        String turnId = UUID.randomUUID().toString();
        String stepId = null;
        boolean stepOpen = false;
        TaskStatus terminal = TaskStatus.LIMIT_REACHED;
        String reason = "STEP_LIMIT";
        Set<String> callIds = new HashSet<>();
        List<ToolInvocation> unresolved = new ArrayList<>();
        BigDecimal[] reservedCost = {BigDecimal.ZERO};
        String originalError = null;
        boolean deadlineExceeded = false;
        for (SessionEvent event : runtime.reader().events())
            if (event.callId() != null) callIds.add(event.callId());
        append("turn.started", EventLevel.TURN, turnId, null, null, Map.of());
        try {
            if (!project().uncertainCalls().isEmpty())
                throw new HarnessException(
                        "UNKNOWN_SIDE_EFFECT", "Manual verification required before another turn");
            for (int step = 0; step < core.budget().maxSteps(); step++) {
                cancellation.throwIfCancelled();
                stepId = UUID.randomUUID().toString();
                append(
                        "step.started",
                        EventLevel.STEP,
                        turnId,
                        stepId,
                        null,
                        Map.of("index", step));
                stepOpen = true;
                SessionProjection projection = project();
                ChatRequest request = core.promptComposer().compose(projection);
                request =
                        core.events()
                                .invoke(
                                        StandardExtensionPoints.BEFORE_MODEL,
                                        request,
                                        value -> value);
                if (!core.llm().capabilities().tools())
                    request = new ChatRequest(request.messages(), List.of(), request.parameters());
                request = clean(request);
                final ChatRequest modelRequest = request;
                final String modelTurn = turnId, modelStep = stepId;
                ModelExecution execution =
                        core.events()
                                .invoke(
                                        StandardExtensionPoints.MODEL_EXECUTION,
                                        ModelExecution.once(),
                                        value -> value);
                ChatResponse response =
                        execution.execute(
                                (singleSend, attemptCancellation) -> {
                                    attemptCancellation.throwIfCancelled();
                                    if (singleSend && !core.llm().supportsSingleAttempt())
                                        throw new HarnessException(
                                                "SINGLE_ATTEMPT_UNSUPPORTED",
                                                "Adapter has no single-send contract");
                                    ChatRequest attemptRequest =
                                            clean(core.llm().prepare(modelRequest).request());
                                    estimateContext(
                                            attemptRequest,
                                            core.llm().capabilities().maxContextTokens());
                                    BigDecimal bound = BigDecimal.ZERO;
                                    if (core.budget().maxApiCost().isPresent()) {
                                        if (!runtime.costs()
                                                .currency()
                                                .equals(core.budget().currency()))
                                            throw new HarnessException(
                                                    "COST_CURRENCY_MISMATCH",
                                                    "Cost guard and budget currency differ");
                                        bound =
                                                Objects.requireNonNull(
                                                        runtime.costs()
                                                                .maximumCost(attemptRequest));
                                        if (bound.signum() < 0)
                                            throw new HarnessException(
                                                    "COST_BUDGET_UNAVAILABLE",
                                                    "Negative cost bound");
                                        if (reservedCost[0]
                                                        .add(bound)
                                                        .compareTo(core.budget().maxApiCost().get())
                                                > 0)
                                            throw new HarnessException(
                                                    "COST_LIMIT",
                                                    "Next request exceeds remaining fee budget");
                                        reservedCost[0] = reservedCost[0].add(bound);
                                        append(
                                                "budget.reserved",
                                                EventLevel.STEP,
                                                modelTurn,
                                                modelStep,
                                                null,
                                                Map.of(
                                                        "currency",
                                                        core.budget().currency(),
                                                        "requestBound",
                                                        bound.toPlainString(),
                                                        "reserved",
                                                        reservedCost[0].toPlainString()));
                                    }
                                    attemptCancellation.throwIfCancelled();
                                    String attemptRequestId =
                                            runtime.requests()
                                                    .record(
                                                            attemptRequest,
                                                            runtime.scope().sessionId(),
                                                            modelTurn,
                                                            modelStep,
                                                            core.session(),
                                                            core.events());
                                    checkpoint(
                                            runtime.checkpoints()
                                                    .beforeEachModelRequest(
                                                            new SessionState(
                                                                    runtime.scope().sessionId(),
                                                                    TaskStatus.RUNNING)));
                                    attemptCancellation.throwIfCancelled();
                                    ChatRequest replay =
                                            runtime.requests()
                                                    .reconstruct(
                                                            runtime.reader().events(),
                                                            attemptRequestId);
                                    if (!attemptRequest.equals(replay))
                                        throw new HarnessException(
                                                "REQUEST_REPLAY_MISMATCH",
                                                "Journal did not reconstruct the prepared request");
                                    ChatResponse attemptResponse;
                                    try {
                                        attemptResponse =
                                                Objects.requireNonNull(
                                                        singleSend
                                                                ? core.llm()
                                                                        .completeSingleAttempt(
                                                                                replay,
                                                                                attemptCancellation)
                                                                : core.llm()
                                                                        .complete(
                                                                                replay,
                                                                                attemptCancellation));
                                    } catch (ModelRejectedException rejected) {
                                        append(
                                                "model.rejected",
                                                EventLevel.STEP,
                                                modelTurn,
                                                modelStep,
                                                null,
                                                Map.of(
                                                        "requestEventId",
                                                        attemptRequestId,
                                                        "reason",
                                                        "HTTP_429"));
                                        core.session().flush();
                                        if (core.budget().maxApiCost().isPresent())
                                            runtime.costs().requestRejected(replay);
                                        throw rejected;
                                    }
                                    if (core.budget().maxApiCost().isPresent()) {
                                        BigDecimal actual =
                                                Objects.requireNonNull(
                                                        runtime.costs()
                                                                .actualCost(
                                                                        replay, attemptResponse));
                                        if (actual.signum() < 0 || actual.compareTo(bound) > 0)
                                            throw new HarnessException(
                                                    "COST_ACCOUNTING_BREACH",
                                                    "Actual cost exceeds reservation");
                                        reservedCost[0] =
                                                reservedCost[0].subtract(bound).add(actual);
                                        append(
                                                "budget.settled",
                                                EventLevel.STEP,
                                                modelTurn,
                                                modelStep,
                                                null,
                                                Map.of(
                                                        "currency",
                                                        core.budget().currency(),
                                                        "chargedUpperBound",
                                                        actual.toPlainString(),
                                                        "reserved",
                                                        reservedCost[0].toPlainString(),
                                                        "usage",
                                                        runtime.sanitizer()
                                                                .sanitize(
                                                                        attemptResponse.usage())));
                                    }
                                    return attemptResponse;
                                },
                                cancellation);
                cancellation.throwIfCancelled();
                response =
                        core.events()
                                .invoke(
                                        StandardExtensionPoints.AFTER_MODEL,
                                        response,
                                        value -> value);
                response = clean(response);
                if (!core.llm().capabilities().tools() && !response.toolCalls().isEmpty())
                    throw new HarnessException(
                            "UNSUPPORTED_TOOLS",
                            "Model returned calls despite disabled capability");
                for (ToolInvocation call : response.toolCalls())
                    if (!callIds.add(call.callId()))
                        throw new HarnessException(
                                "DUPLICATE_CALL_ID", "Model reused a tool callId");
                Map<String, Object> assistant = new LinkedHashMap<>();
                assistant.put("role", "assistant");
                assistant.put("content", response.text());
                if (!response.providerState().isEmpty())
                    assistant.put("provider_state", response.providerState());
                if (!response.toolCalls().isEmpty())
                    assistant.put(
                            "tool_calls",
                            response.toolCalls().stream()
                                    .map(
                                            call ->
                                                    Map.<String, Object>of(
                                                            "id",
                                                            call.callId(),
                                                            "type",
                                                            "function",
                                                            "function",
                                                            Map.of(
                                                                    "name",
                                                                    call.tool(),
                                                                    "arguments",
                                                                    call.arguments())))
                                    .toList());
                append("assistant.message", EventLevel.STEP, turnId, stepId, null, assistant);
                unresolved.addAll(response.toolCalls());
                ToolPipelineContext context =
                        new ToolPipelineContext(
                                runtime.scope(),
                                turnId,
                                stepId,
                                core.tools(),
                                core.session(),
                                core.events(),
                                core.policy(),
                                runtime.approvals(),
                                runtime.checkpoints(),
                                cancellation);
                if (!response.toolCalls().isEmpty()) {
                    var batch =
                            core.events()
                                    .invoke(
                                            StandardExtensionPoints.TOOL_BATCH,
                                            ToolBatchExecution.serial(),
                                            value -> value);
                    var results =
                            Objects.requireNonNull(
                                    batch.execute(
                                            response.toolCalls(), context, runtime.pipeline()));
                    if (results.size() > response.toolCalls().size())
                        throw new HarnessException(
                                "INVALID_SCHEDULER_RESULT", "Too many tool results");
                    for (int index = 0; index < results.size(); index++) {
                        var result = results.get(index);
                        var call = response.toolCalls().get(index);
                        if (!call.callId().equals(result.callId()))
                            throw new HarnessException(
                                    "INVALID_SCHEDULER_RESULT",
                                    "Results must retain original call order");
                        unresolved.remove(call);
                        if (ToolBatchExecution.unknownOutcome(result))
                            throw new HarnessException(
                                    "UNKNOWN_SIDE_EFFECT",
                                    "Manual verification required; no automatic retry");
                        if (result.status() == ToolStatus.CANCELLED)
                            throw new HarnessException("CANCELLED", "Tool cancelled");
                        if (result.status() == ToolStatus.TIMEOUT)
                            throw new HarnessException("TIMEOUT", "Tool batch timed out");
                        if (ToolBatchExecution.stops(result))
                            throw new HarnessException(
                                    "CHECKPOINT_FAILED", "Batch stopped before later calls");
                    }
                    if (results.size() != response.toolCalls().size())
                        throw new HarnessException(
                                "INVALID_SCHEDULER_RESULT",
                                "Incomplete batch without stop outcome");
                }
                cancellation.throwIfCancelled();
                append("step.ended", EventLevel.STEP, turnId, stepId, null, Map.of());
                stepOpen = false;
                checkpoint(
                        runtime.checkpoints()
                                .atStepBoundary(
                                        new SessionState(
                                                runtime.scope().sessionId(), TaskStatus.RUNNING)));
                if (response.toolCalls().isEmpty()) {
                    CompletionEvaluator.CompletionDecision decision =
                            runtime.completion().evaluate(response, project());
                    terminal = decision.achieved() ? TaskStatus.COMPLETED : TaskStatus.FAILED;
                    reason = decision.reason();
                    break;
                }
            }
        } catch (RuntimeException ex) {
            String code = ex instanceof HarnessException h ? h.code() : "RUNTIME_FAILED";
            originalError = code;
            deadlineExceeded = System.nanoTime() - start >= duration;
            if (System.nanoTime() - start >= duration && code.equals("CANCELLED")) {
                terminal = TaskStatus.LIMIT_REACHED;
                reason = "TIME_LIMIT";
            } else if (code.equals("COST_LIMIT")) {
                terminal = TaskStatus.LIMIT_REACHED;
                reason = code;
            } else if (code.equals("CANCELLED")) {
                terminal = TaskStatus.CANCELLED;
                reason = code;
            } else {
                terminal = TaskStatus.FAILED;
                reason = code;
            }
            for (ToolInvocation call : unresolved) {
                boolean settled =
                        runtime.reader().events().stream()
                                .anyMatch(
                                        e ->
                                                call.callId().equals(e.callId())
                                                        && e.type().equals("tool.completed"));
                if (settled) continue;
                boolean bodyMayHaveStarted =
                        runtime.reader().events().stream()
                                .anyMatch(
                                        e ->
                                                call.callId().equals(e.callId())
                                                        && e.type().equals("tool.started"));
                SideEffectLevel effect =
                        core.tools()
                                .resolve(call.tool(), runtime.scope())
                                .map(AgentTool::sideEffectLevel)
                                .orElse(SideEffectLevel.SYSTEM_IMPACT);
                ToolStatus status =
                        bodyMayHaveStarted && effect != SideEffectLevel.READ_ONLY
                                ? ToolStatus.UNKNOWN
                                : ToolStatus.CANCELLED;
                append(
                        "tool.completed",
                        EventLevel.TOOL,
                        turnId,
                        stepId,
                        call.callId(),
                        ToolResults.event(ToolResults.failure(call.callId(), status, reason)));
            }
            if (stepOpen) {
                append(
                        "step.ended",
                        EventLevel.STEP,
                        turnId,
                        stepId,
                        null,
                        Map.of("error", reason));
                stepOpen = false;
            }
        }
        Map<String, Object> finalState =
                new LinkedHashMap<>(
                        Map.of(
                                "status",
                                terminal.name(),
                                "reason",
                                reason,
                                "reservedApiCost",
                                reservedCost[0].toPlainString(),
                                "currency",
                                core.budget().currency()));
        if (originalError != null) finalState.put("originalError", originalError);
        finalState.put("deadlineExceeded", deadlineExceeded);
        if (core.budget().currency().equals("USD"))
            finalState.put("reservedApiCostUsd", reservedCost[0].toPlainString());
        append(
                "turn.ended",
                EventLevel.TURN,
                turnId,
                null,
                null,
                runtime.sanitizer().sanitize(finalState));
    }

    private SessionProjection project() {
        return runtime.projector().project(runtime.reader().events());
    }

    private void checkpoint(CheckpointDecision decision) {
        try {
            EventSupport.checkpoint(core.session(), decision);
        } catch (RuntimeException ex) {
            try {
                core.events()
                        .publish(
                                SessionEvent.session(
                                        runtime.scope().sessionId(),
                                        "checkpoint.failed",
                                        Map.of("code", "CHECKPOINT_FAILED")));
            } catch (RuntimeException notification) {
                ex.addSuppressed(notification);
            }
            throw ex;
        }
    }

    private void append(
            String type,
            EventLevel level,
            String turn,
            String step,
            String call,
            Map<String, Object> payload) {
        EventSupport.append(
                core.session(),
                core.events(),
                runtime.scope(),
                type,
                level,
                turn,
                step,
                call,
                payload);
    }

    private ChatRequest clean(ChatRequest request) {
        for (var message : request.messages())
            if (message.containsKey("provider_state")) {
                var state = Values.object(message.get("provider_state"));
                if (!state.equals(runtime.sanitizer().sanitize(state)))
                    throw new HarnessException(
                            "MODEL_STATE_UNSAFE", "Provider state cannot be redacted safely");
            }
        return new ChatRequest(
                request.messages().stream().map(runtime.sanitizer()::sanitize).toList(),
                request.tools().stream().map(runtime.sanitizer()::sanitize).toList(),
                runtime.sanitizer().sanitize(request.parameters()));
    }

    private ChatResponse clean(ChatResponse response) {
        for (ToolInvocation call : response.toolCalls())
            if (!Map.of("id", call.callId(), "name", call.tool())
                    .equals(
                            runtime.sanitizer()
                                    .sanitize(Map.of("id", call.callId(), "name", call.tool()))))
                throw new HarnessException(
                        "UNSAFE_TOOL_IDENTITY", "Model tool identifiers contain sensitive text");
        Map<String, Object> text = runtime.sanitizer().sanitize(Map.of("text", response.text()));
        if (!response.providerState()
                .equals(runtime.sanitizer().sanitize(response.providerState())))
            throw new HarnessException(
                    "MODEL_STATE_UNSAFE", "Provider state cannot be redacted safely");
        return new ChatResponse(
                (String) text.get("text"),
                response.toolCalls().stream()
                        .map(
                                call ->
                                        new ToolInvocation(
                                                (String)
                                                        runtime.sanitizer()
                                                                .sanitize(
                                                                        Map.of("id", call.callId()))
                                                                .get("id"),
                                                (String)
                                                        runtime.sanitizer()
                                                                .sanitize(
                                                                        Map.of("name", call.tool()))
                                                                .get("name"),
                                                runtime.sanitizer().sanitize(call.arguments()),
                                                call.sideEffectLevel()))
                        .toList(),
                runtime.sanitizer().sanitize(response.usage()),
                response.providerState());
    }

    private static void estimateContext(ChatRequest request, int limit) {
        long estimate =
                request.messages().toString().length() + request.tools().toString().length();
        if (estimate > limit)
            throw new HarnessException(
                    "CONTEXT_LIMIT", "Conservative context estimate exceeded model capability");
    }
}
