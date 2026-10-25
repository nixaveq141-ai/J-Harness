package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.llm.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import io.jharness.tools.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class R1CoreLifecycleTest {
    static void stopSchedulerFromHost(ToolScheduler scheduler) {
        scheduler.close();
    }

    static final PluginScope SCOPE = new PluginScope("s", "a");

    static ToolResult ok(String id) {
        return new ToolResult(
                id, ToolStatus.SUCCESS, Map.of("answer", "ok"), "ok", Map.of(), Optional.empty());
    }

    static AgentTool tool(
            SideEffectLevel level, java.util.function.Function<ToolInvocation, ToolResult> body) {
        return new AgentTool() {
            public String name() {
                return "fixture";
            }

            public String description() {
                return "fixture";
            }

            public Map<String, Object> inputSchema() {
                return Map.of("type", "object");
            }

            public Map<String, Object> outputSchema() {
                return Map.of(
                        "type",
                        "object",
                        "required",
                        List.of("answer"),
                        "properties",
                        Map.of("answer", Map.of("type", "string")));
            }

            public SideEffectLevel sideEffectLevel() {
                return level;
            }

            public ToolResult execute(ToolInvocation call, ToolContext context) {
                return body.apply(call);
            }
        };
    }

    final class Fixture implements AutoCloseable {
        final InMemorySessionRepository repo = new InMemorySessionRepository();
        final SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
        final DefaultEventBus bus = new DefaultEventBus();
        final ScopedToolRegistry tools = new ScopedToolRegistry();
        final DefaultToolPipeline pipeline;

        Fixture() {
            this(new SecretRedactor());
        }

        Fixture(DataSanitizer sanitizer) {
            pipeline = new DefaultToolPipeline(new JsonSchemaValidator(), sanitizer);
        }

        ToolPipelineContext context() {
            SessionWriter durable =
                    new SessionWriter() {
                        public boolean durable() {
                            return true;
                        }

                        public String append(SessionEvent event) {
                            return writer.append(event);
                        }

                        public void flush() {
                            writer.flush();
                        }

                        public void close() {}
                    };
            return new ToolPipelineContext(
                    SCOPE,
                    "t",
                    "p",
                    tools,
                    durable,
                    bus,
                    (c, x) -> PolicyEngine.Decision.ALLOW,
                    ApprovalService.rejectAll(),
                    new MemoryCheckpointPolicy(),
                    CancellationToken.NONE);
        }

        PreparedToolCall prepare() {
            return pipeline.prepare(
                            new ToolInvocation(
                                    "id", "fixture", Map.of(), SideEffectLevel.READ_ONLY),
                            context(),
                            () -> {})
                    .orElseThrow();
        }

        List<SessionEvent> events() {
            return repo.openReader("s").events();
        }

        public void close() {
            writer.close();
            bus.close();
        }
    }

    @Test
    void settlementBeforeWrapperEntryPreventsBodyAndRecordsSingleCompletion() throws Exception {
        try (var f = new Fixture()) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var bodies = new AtomicInteger();
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            c -> {
                                bodies.incrementAndGet();
                                return ok(c.callId());
                            }),
                    SCOPE);
            f.bus.intercept(
                    StandardExtensionPoints.TOOL_EXECUTION,
                    (execution, next) ->
                            next.proceed(
                                    (call, cancel) -> {
                                        entered.countDown();
                                        assertTrue(release.await(5, TimeUnit.SECONDS));
                                        return execution.execute(call, cancel);
                                    }),
                    0);
            var prepared = f.prepare();
            var result = new AtomicReference<ToolResult>();
            Thread worker = Thread.ofVirtual().start(() -> result.set(prepared.run()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(
                    ToolStatus.UNKNOWN,
                    prepared.finish(ToolResults.failure("id", ToolStatus.UNKNOWN, "UNCONFIRMED"))
                            .status());
            release.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertEquals(0, bodies.get());
            assertEquals("SCOPE_CLOSED", result.get().error().orElseThrow().code());
            assertEquals(
                    1, f.events().stream().filter(e -> e.type().equals("tool.completed")).count());
            assertEquals(
                    "REPEATED_SETTLEMENT",
                    assertThrows(HarnessException.class, () -> prepared.finish(result.get()))
                            .code());
        }
    }

    @Test
    void wrongSettlementIdentityDoesNotConsumeValidCompletion() {
        try (var f = new Fixture()) {
            f.tools.register(tool(SideEffectLevel.READ_ONLY, c -> ok(c.callId())), SCOPE);
            var prepared = f.prepare();
            var result = prepared.run();
            assertEquals(
                    "RESULT_STATUS_MISMATCH",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            prepared.finish(
                                                    ToolResults.failure(
                                                            "id", ToolStatus.FAILED, "invented")))
                            .code());
            assertEquals(
                    "RESULT_CALL_MISMATCH",
                    assertThrows(HarnessException.class, () -> prepared.finish(ok("other")))
                            .code());
            assertEquals(ToolStatus.SUCCESS, prepared.finish(result).status());
            assertEquals(
                    1, f.events().stream().filter(e -> e.type().equals("tool.completed")).count());
        }
    }

    @Test
    void wrapperCannotTurnFailedWriteIntoKnownSuccess() {
        try (var f = new Fixture()) {
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            c ->
                                    ToolResults.failure(
                                            c.callId(), ToolStatus.FAILED, "WRITE_FAILED")),
                    SCOPE);
            f.bus.intercept(
                    StandardExtensionPoints.TOOL_EXECUTION,
                    (execution, next) ->
                            next.proceed(
                                    (call, cancel) -> {
                                        execution.execute(call, cancel);
                                        return ok(call.callId());
                                    }),
                    0);
            var prepared = f.prepare();
            var result = prepared.finish(prepared.run());
            assertEquals(ToolStatus.UNKNOWN, result.status());
            assertEquals(true, result.value().get("sideEffectUncertain"));
            assertFalse(new EventSessionProjection(f.events()).uncertainCalls().isEmpty());
        }
    }

    @Test
    void redactionCannotReportSuccessWithInvalidVisibleSchema() {
        try (var f =
                new Fixture(
                        data -> {
                            var value = new LinkedHashMap<>(data);
                            if (value.containsKey("value")) value.put("value", Map.of());
                            return value;
                        })) {
            f.tools.register(tool(SideEffectLevel.WORKSPACE_WRITE, c -> ok(c.callId())), SCOPE);
            var prepared = f.prepare();
            var result = prepared.finish(prepared.run());
            assertEquals(ToolStatus.UNKNOWN, result.status());
            assertEquals("REDACTED_TOOL_OUTPUT", result.error().orElseThrow().code());
        }
    }

    @Test
    void observerReceivesExactlyPersistedSanitizedPayloadAndUnsafeIdentityRejects() {
        var repo = new InMemorySessionRepository();
        var raw = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
        var redactor = new SecretRedactor(List.of("synthetic-secret"));
        try (var writer = new SanitizingSessionWriter(raw, redactor);
                var bus = new DefaultEventBus()) {
            var seen = new CopyOnWriteArrayList<SessionEvent>();
            bus.notify("*", seen::add);
            var event =
                    EventSupport.append(
                            writer,
                            bus,
                            SCOPE,
                            "fixture",
                            EventLevel.SESSION,
                            null,
                            null,
                            null,
                            Map.of("text", "synthetic-secret"));
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals(List.of(event), seen);
            assertEquals(event, repo.openReader("s").events().getFirst());
            assertFalse(seen.toString().contains("synthetic-secret"));
            assertEquals(
                    "UNSAFE_EVENT_IDENTITY",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            EventSupport.append(
                                                    writer,
                                                    bus,
                                                    SCOPE,
                                                    "synthetic-secret",
                                                    EventLevel.SESSION,
                                                    null,
                                                    null,
                                                    null,
                                                    Map.of()))
                            .code());
            assertEquals(1, repo.openReader("s").events().size());
        }
    }

    @Test
    void modelObservationFailurePreservesOriginalRejectionAndSecondaryCause() {
        var primary = new ModelRejectedException();
        var secondary = new HarnessException("WRITE_FAILED", "safe");
        LlmClient delegate =
                new LlmClient() {
                    public ChatResponse complete(
                            ChatRequest request, CancellationToken cancellation) {
                        throw primary;
                    }

                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(false, false, false, false, 1000);
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return new PreparedRequest(request);
                    }

                    public StreamHandle stream(
                            ChatRequest request, CancellationToken cancellation) {
                        throw new UnsupportedOperationException();
                    }
                };
        SessionWriter writer =
                new SessionWriter() {
                    public String append(SessionEvent e) {
                        throw secondary;
                    }

                    public void flush() {}

                    public void close() {}
                };
        var observed =
                new ObservedLlmClient(delegate, writer, () -> List.of(), new SecretRedactor(), "s");
        assertSame(
                primary,
                assertThrows(
                        ModelRejectedException.class,
                        () ->
                                observed.complete(
                                        new ChatRequest(List.of(), List.of(), Map.of()),
                                        CancellationToken.NONE)));
        assertEquals(List.of(secondary), List.of(primary.getSuppressed()));
    }

    @Test
    void costGuardChecksActualUnicodeToolAndMessageInputBeforeSending() {
        var guard =
                new ConfiguredTokenCostGuard(
                        "m", 1000, 20, new BigDecimal("0.01"), new BigDecimal("0.01"));
        var params = Map.<String, Object>of("model", "m", "max_tokens", 20);
        assertTrue(
                guard.maximumCost(
                                        new ChatRequest(
                                                List.of(Map.of("content", "ok")),
                                                List.of(),
                                                params))
                                .signum()
                        > 0);
        for (var request :
                List.of(
                        new ChatRequest(
                                List.of(Map.of("content", "密".repeat(200))), List.of(), params),
                        new ChatRequest(
                                List.of(),
                                List.of(Map.of("description", "x".repeat(1200))),
                                params)))
            assertEquals(
                    "COST_BUDGET_UNAVAILABLE",
                    assertThrows(HarnessException.class, () -> guard.maximumCost(request)).code());
    }

    @Test
    void actualResultValueDisplayAndModelContentAreRedactedBeforeCompletion() {
        try (var f = new Fixture(new SecretRedactor(List.of("sentinel-private")))) {
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            c ->
                                    new ToolResult(
                                            c.callId(),
                                            ToolStatus.SUCCESS,
                                            Map.of("answer", "sentinel-private"),
                                            "sentinel-private",
                                            Map.of("text", "sentinel-private"),
                                            Optional.empty())),
                    SCOPE);
            var prepared = f.prepare();
            var result = prepared.finish(prepared.run());
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertEquals("[REDACTED]", result.value().get("answer"));
            assertEquals("[REDACTED]", result.modelContent());
            assertEquals("[REDACTED]", result.display().get("text"));
            assertFalse(f.events().toString().contains("sentinel-private"));
        }
    }

    @Test
    void persistedEventFailureIsDistinguishedFromNotificationFailure() {
        var repo = new InMemorySessionRepository();
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            var bus = new DefaultEventBus();
            bus.close();
            var error =
                    assertThrows(
                            HarnessException.class,
                            () ->
                                    EventSupport.append(
                                            writer,
                                            bus,
                                            SCOPE,
                                            "fixture",
                                            EventLevel.SESSION,
                                            null,
                                            null,
                                            null,
                                            Map.of("text", "safe")));
            assertEquals("EVENT_NOTIFICATION_FAILED", error.code());
            assertEquals(1, repo.openReader("s").events().size());
        }
    }

    @Test
    void stoppedBarrierSettlesAlreadyPreparedCallWithoutEnteringItsBody() {
        try (var f = new Fixture();
                var scheduler =
                        new BoundedReadOnlyToolScheduler(
                                new ToolSchedulingOptions(
                                        2, Set.of("fixture"), Duration.ofSeconds(2)))) {
            var release = new CountDownLatch(1);
            var bodies = new AtomicInteger();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            c -> {
                                bodies.incrementAndGet();
                                try {
                                    assertTrue(release.await(3, TimeUnit.SECONDS));
                                } catch (InterruptedException error) {
                                    throw new RuntimeException(error);
                                }
                                return ToolResults.failure(
                                        c.callId(), ToolStatus.UNKNOWN, "READ_UNCONFIRMED");
                            }),
                    SCOPE);
            var base = f.context();
            var context =
                    new ToolPipelineContext(
                            SCOPE,
                            "t",
                            "p",
                            f.tools,
                            base.session(),
                            f.bus,
                            (call, c) -> {
                                if (call.callId().equals("second")) {
                                    release.countDown();
                                    return PolicyEngine.Decision.ASK;
                                }
                                return PolicyEngine.Decision.ALLOW;
                            },
                            ApprovalService.rejectAll(),
                            new MemoryCheckpointPolicy(),
                            CancellationToken.NONE);
            var calls =
                    List.of(
                            new ToolInvocation(
                                    "first", "fixture", Map.of(), SideEffectLevel.READ_ONLY),
                            new ToolInvocation(
                                    "second", "fixture", Map.of(), SideEffectLevel.READ_ONLY));
            assertEquals(
                    "UNKNOWN_SIDE_EFFECT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> scheduler.execute(calls, context, f.pipeline))
                            .code());
            assertEquals(1, bodies.get());
            assertEquals(
                    2, f.events().stream().filter(e -> e.type().equals("tool.requested")).count());
            assertEquals(
                    2, f.events().stream().filter(e -> e.type().equals("tool.completed")).count());
            assertEquals(
                    false,
                    f.events().stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && e.callId().equals("second"))
                            .findFirst()
                            .orElseThrow()
                            .payload()
                            .get("bodyEntered"));
        }
    }

    @Test
    void drainDoesNotRepeatFailedSettlementAndStillSettlesRemainingPreparedCall() {
        try (var f = new Fixture();
                var scheduler =
                        new BoundedReadOnlyToolScheduler(
                                new ToolSchedulingOptions(
                                        2, Set.of("fixture"), Duration.ofSeconds(2)))) {
            f.tools.register(tool(SideEffectLevel.READ_ONLY, c -> ok(c.callId())), SCOPE);
            var finishes = new ConcurrentHashMap<String, AtomicInteger>();
            ToolPipeline pipeline =
                    new ToolPipeline() {
                        public ToolResult execute(ToolInvocation c, ToolPipelineContext context) {
                            throw new AssertionError("prepared path required");
                        }

                        public Optional<PreparedToolCall> prepare(
                                ToolInvocation call,
                                ToolPipelineContext context,
                                Runnable barrier) {
                            return Optional.of(
                                    new PreparedToolCall() {
                                        public boolean parallelAllowed() {
                                            return true;
                                        }

                                        public ToolResult run() {
                                            return ok(call.callId());
                                        }

                                        public ToolResult finish(ToolResult result) {
                                            finishes.computeIfAbsent(
                                                            call.callId(), k -> new AtomicInteger())
                                                    .incrementAndGet();
                                            if (call.callId().equals("first"))
                                                throw new HarnessException(
                                                        "WRITE_FAILED", "safe fixture");
                                            return result;
                                        }
                                    });
                        }
                    };
            var calls =
                    List.of(
                            new ToolInvocation(
                                    "first", "fixture", Map.of(), SideEffectLevel.READ_ONLY),
                            new ToolInvocation(
                                    "second", "fixture", Map.of(), SideEffectLevel.READ_ONLY));
            assertEquals(
                    "WRITE_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> scheduler.execute(calls, f.context(), pipeline))
                            .code());
            assertEquals(1, finishes.get("first").get());
            assertEquals(1, finishes.get("second").get());
        }
    }

    @Test
    void modelFailureKeepsPrimaryWhenStopAuditAlsoFails() {
        var primary = new HarnessException("MODEL_TRANSPORT_UNKNOWN", "safe fixture");
        var secondary = new HarnessException("AUDIT_FAILED", "safe fixture");
        try (var execution =
                new BoundedModelExecution(
                        new ModelResilienceOptions(true, 1, 0, 2000, 0, 1),
                        (type, data) -> {
                            if (type.equals("model.execution.stopped")) throw secondary;
                        })) {
            assertSame(
                    primary,
                    assertThrows(
                            HarnessException.class,
                            () ->
                                    execution.execute(
                                            (single, cancel) -> {
                                                throw primary;
                                            },
                                            CancellationToken.NONE)));
            assertEquals(List.of(secondary), List.of(primary.getSuppressed()));
        }
    }

    @Test
    void retryAuditFailurePreservesRejectionAndPreventsSecondSend() {
        var rejection = new ModelRejectedException();
        var auditFailure = new HarnessException("RETRY_AUDIT_FAILED", "safe fixture");
        var sends = new AtomicInteger();
        try (var execution =
                new BoundedModelExecution(
                        new ModelResilienceOptions(true, 2, 0, 2000, 0, 0),
                        (type, data) -> {
                            if (type.equals("model.retry.scheduled")) throw auditFailure;
                        })) {
            assertSame(
                    rejection,
                    assertThrows(
                            ModelRejectedException.class,
                            () ->
                                    execution.execute(
                                            (single, cancellation) -> {
                                                sends.incrementAndGet();
                                                throw rejection;
                                            },
                                            CancellationToken.NONE)));
            assertEquals(1, sends.get());
            assertEquals(List.of(auditFailure), List.of(rejection.getSuppressed()));
            // A failed audit releases concurrency ownership for a later explicit call.
            assertEquals(
                    "next",
                    execution
                            .execute(
                                    (single, cancellation) ->
                                            new ChatResponse("next", List.of(), Map.of()),
                                    CancellationToken.NONE)
                            .text());
        }
    }

    @Test
    void requiredAttemptAuditFailurePreventsAnySend() {
        var auditFailure = new HarnessException("ATTEMPT_AUDIT_FAILED", "safe fixture");
        var sends = new AtomicInteger();
        try (var execution =
                new BoundedModelExecution(
                        new ModelResilienceOptions(true, 2, 0, 2000, 0, 0),
                        (type, data) -> {
                            if (type.equals("model.attempt.started")) throw auditFailure;
                        })) {
            assertSame(
                    auditFailure,
                    assertThrows(
                            HarnessException.class,
                            () ->
                                    execution.execute(
                                            (single, cancellation) -> {
                                                sends.incrementAndGet();
                                                return new ChatResponse(
                                                        "unexpected", List.of(), Map.of());
                                            },
                                            CancellationToken.NONE)));
            assertEquals(0, sends.get());
        }
    }

    @Test
    void waitingAuditDoesNotHoldMonitorNeededByActiveRequestExit() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstDone = new CountDownLatch(1);
        try (var execution =
                        new BoundedModelExecution(
                                new ModelResilienceOptions(true, 1, 0, 5000, 0, 1),
                                (type, data) -> {
                                    if (type.equals("model.rate.waiting")) {
                                        release.countDown();
                                        try {
                                            assertTrue(firstDone.await(2, TimeUnit.SECONDS));
                                        } catch (InterruptedException error) {
                                            throw new RuntimeException(error);
                                        }
                                    }
                                });
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    workers.submit(
                            () -> {
                                try {
                                    return execution.execute(
                                            (single, cancel) -> {
                                                entered.countDown();
                                                try {
                                                    assertTrue(release.await(3, TimeUnit.SECONDS));
                                                } catch (InterruptedException error) {
                                                    throw new RuntimeException(error);
                                                }
                                                return new ChatResponse("ok", List.of(), Map.of());
                                            },
                                            CancellationToken.NONE);
                                } finally {
                                    firstDone.countDown();
                                }
                            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second =
                    workers.submit(
                            () ->
                                    execution.execute(
                                            (single, cancel) ->
                                                    new ChatResponse("ok", List.of(), Map.of()),
                                            CancellationToken.NONE));
            assertEquals("ok", first.get(4, TimeUnit.SECONDS).text());
            assertEquals("ok", second.get(4, TimeUnit.SECONDS).text());
        } finally {
            release.countDown();
        }
    }

    @Test
    void schedulerCloseBeforeWorkerRegistrationPreventsLateBodyEntry() throws Exception {
        try (var f = new Fixture();
                var scheduler =
                        new BoundedReadOnlyToolScheduler(
                                new ToolSchedulingOptions(
                                        2, Set.of("fixture"), Duration.ofSeconds(2)));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            f.tools.register(tool(SideEffectLevel.READ_ONLY, c -> ok(c.callId())), SCOPE);
            var prepared = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var bodies = new AtomicInteger();
            var finishes = new AtomicInteger();
            ToolPipeline pipeline =
                    new ToolPipeline() {
                        public ToolResult execute(
                                ToolInvocation call, ToolPipelineContext context) {
                            throw new AssertionError("prepared path required");
                        }

                        public Optional<PreparedToolCall> prepare(
                                ToolInvocation call,
                                ToolPipelineContext context,
                                Runnable barrier) {
                            return Optional.of(
                                    new PreparedToolCall() {
                                        public boolean parallelAllowed() {
                                            prepared.countDown();
                                            try {
                                                assertTrue(release.await(3, TimeUnit.SECONDS));
                                            } catch (InterruptedException error) {
                                                throw new RuntimeException(error);
                                            }
                                            return true;
                                        }

                                        public ToolResult run() {
                                            bodies.incrementAndGet();
                                            return ok(call.callId());
                                        }

                                        public ToolResult finish(ToolResult result) {
                                            finishes.incrementAndGet();
                                            return result;
                                        }
                                    });
                        }
                    };
            var future =
                    executor.submit(
                            () ->
                                    scheduler.execute(
                                            List.of(
                                                    new ToolInvocation(
                                                            "id",
                                                            "fixture",
                                                            Map.of(),
                                                            SideEffectLevel.READ_ONLY)),
                                            f.context(),
                                            pipeline));
            assertTrue(prepared.await(2, TimeUnit.SECONDS));
            stopSchedulerFromHost(scheduler);
            release.countDown();
            var error =
                    assertThrows(ExecutionException.class, () -> future.get(4, TimeUnit.SECONDS));
            assertEquals(
                    "SCHEDULER_CLOSED",
                    assertInstanceOf(HarnessException.class, error.getCause()).code());
            assertEquals(0, bodies.get());
            assertEquals(1, finishes.get());
        }
    }

    @Test
    void routerCloseRetainsFailedRouteEvidenceWithoutRetryingResource() {
        var closes = new AtomicInteger();
        LlmClient client =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(false, false, false, false, 1000);
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return new PreparedRequest(request);
                    }

                    public ChatResponse complete(
                            ChatRequest request, CancellationToken cancellation) {
                        throw new AssertionError("no send");
                    }

                    public StreamHandle stream(
                            ChatRequest request, CancellationToken cancellation) {
                        throw new UnsupportedOperationException();
                    }

                    public void close() {
                        closes.incrementAndGet();
                        throw new HarnessException("CLOSE_UNCONFIRMED", "safe fixture");
                    }
                };
        var router = new LazyModelRouter(Map.of("base", () -> client));
        router.select("base");
        assertEquals(
                "CLOSE_UNCONFIRMED", assertThrows(HarnessException.class, router::close).code());
        assertEquals(Set.of("base"), router.unconfirmedClosures().keySet());
        router.close();
        assertEquals(1, closes.get());
        assertEquals(
                "MODEL_ROUTER_CLOSED",
                assertThrows(HarnessException.class, () -> router.select("base")).code());
    }

    @Test
    void operatorVerificationProjectionOmitsUnrecognizedPayloadFields() {
        var event =
                new SessionEvent(
                        "id",
                        "s",
                        "tool.verified",
                        java.time.Instant.now(),
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "call",
                        Map.of(
                                "outcome",
                                "verified",
                                "reason",
                                "checked",
                                "unexpected",
                                "sentinel-private"));
        var text = new EventSessionProjection(List.of(event)).messages().toString();
        assertTrue(text.contains("outcome=8:verified"));
        assertTrue(text.contains("reason=7:checked"));
        assertFalse(text.contains("sentinel-private"));
    }

    @Test
    void knownReturnedFailureDoesNotBecomeUnknownMerelyBecauseBodyEntered() {
        try (var f = new Fixture()) {
            f.tools.register(
                    tool(
                            SideEffectLevel.SYSTEM_IMPACT,
                            c ->
                                    new ToolResult(
                                            c.callId(),
                                            ToolStatus.FAILED,
                                            Map.of("sideEffectUncertain", false),
                                            "NONZERO_EXIT",
                                            Map.of(),
                                            Optional.of(
                                                    new ToolResult.ErrorInfo(
                                                            "NONZERO_EXIT", "known exit")))),
                    SCOPE);
            var call = f.prepare();
            var result = call.finish(call.run());
            assertEquals(ToolStatus.FAILED, result.status());
            assertFalse(ToolBatchExecution.stops(result));
            assertTrue(new EventSessionProjection(f.events()).uncertainCalls().isEmpty());
        }
    }

    @Test
    void conflictCodeWithoutProviderProofStillRequiresVerification() {
        try (var f = new Fixture()) {
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            call ->
                                    ToolResults.failure(
                                            call.callId(), ToolStatus.FAILED, "FILE_CONFLICT")),
                    SCOPE);
            var call = f.prepare();
            assertEquals(ToolStatus.UNKNOWN, call.finish(call.run()).status());
            assertFalse(new EventSessionProjection(f.events()).uncertainCalls().isEmpty());
        }
    }

    @Test
    void knownCancellationAndTimeoutPreserveReasonButBlockContinuationForPartialEffects() {
        for (var status : List.of(ToolStatus.CANCELLED, ToolStatus.TIMEOUT)) {
            try (var f = new Fixture()) {
                f.tools.register(
                        tool(
                                SideEffectLevel.SYSTEM_IMPACT,
                                c ->
                                        new ToolResult(
                                                c.callId(),
                                                status,
                                                Map.of(
                                                        "observedClean",
                                                        true,
                                                        "residualPids",
                                                        List.of(),
                                                        "sideEffectUncertain",
                                                        true),
                                                "partial",
                                                Map.of(),
                                                Optional.of(
                                                        new ToolResult.ErrorInfo(
                                                                status.name(), "partial")))),
                        SCOPE);
                var results =
                        ToolBatchExecution.serial()
                                .execute(
                                        List.of(
                                                new ToolInvocation(
                                                        "id",
                                                        "fixture",
                                                        Map.of(),
                                                        SideEffectLevel.SYSTEM_IMPACT),
                                                new ToolInvocation(
                                                        "next",
                                                        "fixture",
                                                        Map.of(),
                                                        SideEffectLevel.SYSTEM_IMPACT)),
                                        f.context(),
                                        f.pipeline);
                assertEquals(1, results.size());
                var result = results.getFirst();
                var stop =
                        f.events().stream()
                                .filter(e -> e.type().equals("tool.batch.stopped"))
                                .findFirst()
                                .orElseThrow();
                assertEquals(status.name(), stop.payload().get("reason"));
                assertEquals(1, stop.payload().get("pending"));
                assertFalse(f.events().stream().anyMatch(e -> "next".equals(e.callId())));
                assertEquals(status, result.status());
                assertTrue(ToolBatchExecution.stops(result));
                assertFalse(ToolBatchExecution.unknownOutcome(result));
                assertEquals(true, result.value().get("sideEffectUncertain"));
                assertFalse(new EventSessionProjection(f.events()).uncertainCalls().isEmpty());
            }
        }
    }

    @Test
    void residualProcessOrUnknownNeverDowngradesToKnownCancellation() {
        for (var status : List.of(ToolStatus.CANCELLED, ToolStatus.TIMEOUT, ToolStatus.UNKNOWN)) {
            try (var f = new Fixture()) {
                f.tools.register(
                        tool(
                                SideEffectLevel.SYSTEM_IMPACT,
                                c ->
                                        new ToolResult(
                                                c.callId(),
                                                status,
                                                Map.of(
                                                        "observedClean",
                                                        false,
                                                        "residualPids",
                                                        List.of(123),
                                                        "sideEffectUncertain",
                                                        true),
                                                "partial",
                                                Map.of(),
                                                Optional.of(
                                                        new ToolResult.ErrorInfo(
                                                                status.name(), "partial")))),
                        SCOPE);
                var call = f.prepare();
                var result = call.finish(call.run());
                assertEquals(ToolStatus.UNKNOWN, result.status());
                assertTrue(ToolBatchExecution.unknownOutcome(result));
                assertFalse(new EventSessionProjection(f.events()).uncertainCalls().isEmpty());
            }
        }
    }

    @Test
    void schedulerDoesNotRetryFailedSettlementAndStillSettlesOtherAcceptedCalls() {
        try (var f = new Fixture();
                var scheduler =
                        new BoundedReadOnlyToolScheduler(
                                new ToolSchedulingOptions(
                                        2, Set.of("fixture"), Duration.ofSeconds(1)))) {
            f.tools.register(tool(SideEffectLevel.READ_ONLY, c -> ok(c.callId())), SCOPE);
            var bodies = new AtomicInteger();
            var firstFinishes = new AtomicInteger();
            var secondFinishes = new AtomicInteger();
            ToolPipeline pipeline =
                    new ToolPipeline() {
                        public ToolResult execute(ToolInvocation call, ToolPipelineContext c) {
                            throw new AssertionError();
                        }

                        public Optional<PreparedToolCall> prepare(
                                ToolInvocation call, ToolPipelineContext c, Runnable barrier) {
                            return Optional.of(
                                    new PreparedToolCall() {
                                        public boolean parallelAllowed() {
                                            return true;
                                        }

                                        public ToolResult run() {
                                            bodies.incrementAndGet();
                                            return ok(call.callId());
                                        }

                                        public ToolResult finish(ToolResult result) {
                                            if (call.callId().equals("first")) {
                                                firstFinishes.incrementAndGet();
                                                throw new HarnessException(
                                                        "INJECTED_FINISH_FAILURE",
                                                        "fixture after completion may have persisted");
                                            }
                                            secondFinishes.incrementAndGet();
                                            return result;
                                        }
                                    });
                        }
                    };
            assertEquals(
                    "INJECTED_FINISH_FAILURE",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            scheduler.execute(
                                                    List.of(
                                                            new ToolInvocation(
                                                                    "first",
                                                                    "fixture",
                                                                    Map.of(),
                                                                    SideEffectLevel.READ_ONLY),
                                                            new ToolInvocation(
                                                                    "second",
                                                                    "fixture",
                                                                    Map.of(),
                                                                    SideEffectLevel.READ_ONLY)),
                                                    f.context(),
                                                    pipeline))
                            .code());
            assertEquals(2, bodies.get());
            assertEquals(1, firstFinishes.get());
            assertEquals(1, secondFinishes.get());
            var failures =
                    f.events().stream()
                            .filter(e -> e.type().equals("tool.settlement.failed"))
                            .toList();
            assertEquals(1, failures.size());
            assertEquals(false, failures.getFirst().payload().get("retryPermitted"));
        }
    }
}
