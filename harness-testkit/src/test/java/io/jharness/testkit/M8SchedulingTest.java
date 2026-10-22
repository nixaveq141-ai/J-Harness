package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.plugin.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.tools.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M8SchedulingTest {
    @TempDir Path root;

    interface Body {
        ToolResult apply(ToolInvocation call, ToolContext context) throws Exception;
    }

    static ToolInvocation call(String id, String name) {
        return new ToolInvocation(id, name, Map.of(), SideEffectLevel.READ_ONLY);
    }

    static ToolResult success(ToolInvocation call) {
        return new ToolResult(
                call.callId(),
                ToolStatus.SUCCESS,
                Map.of("id", call.callId()),
                call.callId(),
                Map.of(),
                Optional.empty());
    }

    static ChatResponse response(ToolInvocation... calls) {
        return new ChatResponse("fixture", List.of(calls), Map.of());
    }

    static AgentTool tool(String name, SideEffectLevel effect, Body body) {
        return new AgentTool() {
            public String name() {
                return name;
            }

            public String description() {
                return "bounded scheduling fixture";
            }

            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "additionalProperties", false);
            }

            public Map<String, Object> outputSchema() {
                return Map.of("type", "object");
            }

            public SideEffectLevel sideEffectLevel() {
                return effect;
            }

            public ToolResult execute(ToolInvocation call, ToolContext context) throws Exception {
                return body.apply(call, context);
            }
        };
    }

    final class Fixture implements AutoCloseable {
        final JsonlSessionRepository disk;
        final FakeLlm model;
        final HarnessSdk sdk;
        final AtomicBoolean failFlush = new AtomicBoolean();
        final Set<Thread> journalThreads = ConcurrentHashMap.newKeySet();

        Fixture(
                String id,
                ToolSchedulingOptions options,
                List<AgentTool> tools,
                PolicyEngine policy,
                ApprovalService approvals,
                ToolInvocation... calls)
                throws Exception {
            Path base = Files.createDirectories(root.resolve(id));
            disk = new JsonlSessionRepository(base.resolve("sessions"));
            model = new FakeLlm(List.of(response(calls), response()), 32768);
            SessionRepository repo =
                    new SessionRepository() {
                        public SessionWriter openWriter(String session, WriteOwnership ownership) {
                            var delegate = disk.openWriter(session, ownership);
                            return new SessionWriter() {
                                boolean armed;

                                public boolean durable() {
                                    return true;
                                }

                                public String append(SessionEvent event) {
                                    if (event.type().startsWith("tool."))
                                        journalThreads.add(Thread.currentThread());
                                    if (event.type().equals("tool.intent") && failFlush.get())
                                        armed = true;
                                    return delegate.append(event);
                                }

                                public void flush() {
                                    if (armed && failFlush.compareAndSet(true, false)) {
                                        armed = false;
                                        throw new HarnessException(
                                                "CHECKPOINT_FAILED", "fixture flush failure");
                                    }
                                    delegate.flush();
                                }

                                public void close() {
                                    delegate.close();
                                }
                            };
                        }

                        public SessionReader openReader(String session) {
                            return disk.openReader(session);
                        }

                        public List<String> list() {
                            return disk.list();
                        }

                        public String fork(String session, String event) {
                            return disk.fork(session, event);
                        }
                    };
            var extension =
                    new HarnessPlugin() {
                        public String id() {
                            return "scheduling-fixture";
                        }

                        public String version() {
                            return "1.8";
                        }

                        public void start(PluginContext context) {
                            var registry =
                                    context.services()
                                            .requireUnique(ToolRegistry.class, context.scope());
                            tools.forEach(t -> context.own(registry.register(t, context.scope())));
                        }

                        public void stop() {}
                    };
            sdk =
                    new HarnessSdk(
                            "s",
                            "workspace",
                            repo,
                            new LocalExecutionEnvironment("local", base, List.of()),
                            model,
                            new LocalArtifactStore(base.resolve("artifacts"), new SecretRedactor()),
                            new SecretRedactor(),
                            policy,
                            approvals,
                            new Budget(4, Duration.ofSeconds(15)),
                            (reply, projection) ->
                                    new CompletionEvaluator.CompletionDecision(
                                            projection.toolHistory().stream()
                                                            .anyMatch(
                                                                    e ->
                                                                            e.type()
                                                                                    .equals(
                                                                                            "tool.completed"))
                                                    && projection.toolHistory().stream()
                                                            .filter(
                                                                    e ->
                                                                            e.type()
                                                                                    .equals(
                                                                                            "tool.completed"))
                                                            .allMatch(
                                                                    e ->
                                                                            "SUCCESS"
                                                                                    .equals(
                                                                                            e.payload()
                                                                                                    .get(
                                                                                                            "status"))),
                                            "tool facts"),
                            ApiCostGuard.unavailable(),
                            List.of(extension),
                            new SdkConfiguration("", null, Map.of(), options));
        }

        List<SessionEvent> events() {
            return disk.openReader("s").events();
        }

        void replay() {
            int i = 0;
            var events = events();
            for (int end = 0; end < events.size(); end++)
                if (events.get(end).type().equals("model.requested"))
                    assertEquals(
                            model.requests().get(i++),
                            new EventRequestJournal()
                                    .reconstruct(
                                            events.subList(0, end + 1), events.get(end).eventId()));
            assertEquals(model.requests().size(), i);
        }

        public void close() {
            sdk.close();
        }
    }

    static ToolSchedulingOptions parallel(String... tools) {
        return new ToolSchedulingOptions(2, Set.of(tools), Duration.ofSeconds(3));
    }

    @Test
    void boundedBodiesOverlapButJournalAndModelResultsRetainCallOrder() throws Exception {
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var firstTwo = new CountDownLatch(2);
        var actualOrder = Collections.synchronizedList(new ArrayList<String>());
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            int count = active.incrementAndGet();
                            peak.accumulateAndGet(count, Math::max);
                            try {
                                if (Set.of("a", "b").contains(call.callId())) {
                                    firstTwo.countDown();
                                    assertTrue(firstTwo.await(2, TimeUnit.SECONDS));
                                }
                                if (call.callId().equals("a")) Thread.sleep(120);
                                actualOrder.add(call.callId());
                                return success(call);
                            } finally {
                                active.decrementAndGet();
                            }
                        });
        try (var f =
                new Fixture(
                        "overlap",
                        parallel("probe"),
                        List.of(read),
                        new DefaultPolicyEngine(),
                        ApprovalService.rejectAll(),
                        call("a", "probe"),
                        call("b", "probe"),
                        call("c", "probe"),
                        call("d", "probe"))) {
            assertEquals(TaskStatus.COMPLETED, f.sdk.run("read", CancellationToken.NONE).status());
            assertEquals(2, peak.get());
            assertTrue(actualOrder.indexOf("b") < actualOrder.indexOf("a"));
            assertEquals(Set.of("a", "b", "c", "d"), new HashSet<>(actualOrder));
            assertEquals(4, actualOrder.size());
            assertEquals(
                    List.of("a", "b", "c", "d"),
                    f.events().stream()
                            .filter(e -> e.type().equals("tool.completed"))
                            .map(SessionEvent::callId)
                            .toList());
            assertEquals(Set.of(Thread.currentThread()), f.journalThreads);
            assertEquals(
                    List.of("a", "b", "c", "d"),
                    f.model.requests().get(1).messages().stream()
                            .filter(m -> "tool".equals(m.get("role")))
                            .map(m -> (String) m.get("tool_call_id"))
                            .toList());
            f.replay();
        }
    }

    @Test
    void writesUnlistedReadsAndAskAreBarriersDespiteModelReadonlyClaims() throws Exception {
        var active = new AtomicInteger();
        var approvals = new AtomicInteger();
        var owner = Thread.currentThread();
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            active.incrementAndGet();
                            try {
                                Thread.sleep(70);
                                return success(call);
                            } finally {
                                active.decrementAndGet();
                            }
                        });
        Body barrier =
                (call, context) -> {
                    assertEquals(0, active.get());
                    assertSame(owner, Thread.currentThread());
                    return success(call);
                };
        var tools =
                List.of(
                        read,
                        tool("ask", SideEffectLevel.READ_ONLY, barrier),
                        tool("unlisted", SideEffectLevel.READ_ONLY, barrier),
                        tool("write_probe", SideEffectLevel.SYSTEM_IMPACT, barrier));
        PolicyEngine policy =
                (call, context) ->
                        call.tool().equals("ask")
                                ? PolicyEngine.Decision.ASK
                                : new DefaultPolicyEngine().evaluate(call, context);
        ApprovalService approve =
                (call, context) -> {
                    assertEquals(0, active.get());
                    approvals.incrementAndGet();
                    return new ApprovalService.ApprovalDecision(true, "checked", "operator");
                };
        try (var f =
                new Fixture(
                        "barriers",
                        parallel("probe", "ask", "write_probe"),
                        tools,
                        policy,
                        approve,
                        call("a", "probe"),
                        call("b", "probe"),
                        call("ask", "ask"),
                        call("u", "unlisted"),
                        call("write", "write_probe"))) {
            assertEquals(
                    TaskStatus.COMPLETED, f.sdk.run("barriers", CancellationToken.NONE).status());
            assertEquals(2, approvals.get());
            f.replay();
            assertEquals(
                    "SYSTEM_IMPACT",
                    f.events().stream()
                            .filter(
                                    e ->
                                            "write".equals(e.callId())
                                                    && e.type().equals("tool.intent"))
                            .findFirst()
                            .orElseThrow()
                            .payload()
                            .get("sideEffectLevel"));
        }
    }

    @Test
    void denialNeverEntersBodyAndKnownReadFailureStillReturnsBothResults() throws Exception {
        var denied = new AtomicInteger();
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) ->
                                call.callId().equals("bad")
                                        ? ToolResults.failure(
                                                call.callId(), ToolStatus.FAILED, "READ_FAILED")
                                        : success(call));
        var secret =
                tool(
                        "denied",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            denied.incrementAndGet();
                            return success(call);
                        });
        try (var f =
                new Fixture(
                        "failure",
                        parallel("probe", "denied"),
                        List.of(read, secret),
                        (call, context) ->
                                call.tool().equals("denied")
                                        ? PolicyEngine.Decision.DENY
                                        : PolicyEngine.Decision.ALLOW,
                        ApprovalService.rejectAll(),
                        call("bad", "probe"),
                        call("good", "probe"),
                        call("denied", "denied"))) {
            assertEquals(TaskStatus.FAILED, f.sdk.run("failure", CancellationToken.NONE).status());
            assertEquals(0, denied.get());
            assertEquals(2, f.model.requests().size());
            assertEquals(
                    List.of("FAILED", "SUCCESS", "DENIED"),
                    f.events().stream()
                            .filter(e -> e.type().equals("tool.completed"))
                            .map(e -> e.payload().get("status"))
                            .toList());
            f.replay();
        }
    }

    @Test
    void cancellationDrainsCooperativeWorkersAndNeverStartsFollowingWrite() throws Exception {
        var entered = new CountDownLatch(2);
        var writes = new AtomicInteger();
        var cancel = new AtomicBoolean();
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            entered.countDown();
                            while (!context.cancellation().isCancelled()) Thread.sleep(10);
                            context.cancellation().throwIfCancelled();
                            return success(call);
                        });
        try (var f =
                        new Fixture(
                                "cancel",
                                parallel("probe"),
                                List.of(
                                        read,
                                        tool(
                                                "write_probe",
                                                SideEffectLevel.SYSTEM_IMPACT,
                                                (call, c) -> {
                                                    writes.incrementAndGet();
                                                    return success(call);
                                                })),
                                new DefaultPolicyEngine(),
                                M7EcosystemFixture.approve(),
                                call("a", "probe"),
                                call("b", "probe"),
                                call("w", "write_probe"));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> f.sdk.run("cancel", cancel::get));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            cancel.set(true);
            assertEquals(TaskStatus.CANCELLED, future.get(4, TimeUnit.SECONDS).status());
            assertEquals(0, writes.get());
            assertEquals(1, f.model.requests().size());
        }
    }

    @Test
    void noncooperativeWorkerIsUnknownNoReplayOrSecondSettlement() throws Exception {
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        var writes = new AtomicInteger();
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            worker.set(Thread.currentThread());
                            entered.countDown();
                            try {
                                while (release.getCount() != 0) {
                                    try {
                                        release.await();
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                                return success(call);
                            } finally {
                                exited.countDown();
                            }
                        });
        try (var f =
                new Fixture(
                        "unknown",
                        new ToolSchedulingOptions(2, Set.of("probe"), Duration.ofMillis(60)),
                        List.of(
                                read,
                                tool(
                                        "write_probe",
                                        SideEffectLevel.SYSTEM_IMPACT,
                                        (call, c) -> {
                                            writes.incrementAndGet();
                                            return success(call);
                                        })),
                        new DefaultPolicyEngine(),
                        M7EcosystemFixture.approve(),
                        call("a", "probe"),
                        call("w", "write_probe"))) {
            var snapshot = f.sdk.run("unknown", CancellationToken.NONE);
            assertEquals(0, entered.getCount());
            assertEquals(TaskStatus.FAILED, snapshot.status());
            assertEquals(List.of("a"), new EventSessionProjection(f.events()).uncertainCalls());
            assertEquals(0, writes.get());
            assertEquals(1, f.model.requests().size());
            long completed =
                    f.events().stream().filter(e -> e.type().equals("tool.completed")).count();
            release.countDown();
            assertTrue(exited.await(2, TimeUnit.SECONDS));
            worker.get().join(2000);
            assertFalse(worker.get().isAlive());
            assertEquals(
                    completed,
                    f.events().stream().filter(e -> e.type().equals("tool.completed")).count());
            assertThrows(HarnessException.class, () -> f.sdk.run("retry", CancellationToken.NONE));
        } finally {
            release.countDown();
            assertTrue(exited.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedCheckpointPreventsBodyAndLaterWrite() throws Exception {
        var bodies = new AtomicInteger();
        Body body =
                (call, context) -> {
                    bodies.incrementAndGet();
                    return success(call);
                };
        try (var f =
                new Fixture(
                        "flush",
                        parallel("probe"),
                        List.of(
                                tool("probe", SideEffectLevel.READ_ONLY, body),
                                tool("write_probe", SideEffectLevel.SYSTEM_IMPACT, body)),
                        new DefaultPolicyEngine(),
                        M7EcosystemFixture.approve(),
                        call("a", "probe"),
                        call("w", "write_probe"))) {
            f.failFlush.set(true);
            assertEquals(TaskStatus.FAILED, f.sdk.run("flush", CancellationToken.NONE).status());
            assertEquals(0, bodies.get());
            assertEquals(1, f.model.requests().size());
        }
    }

    @Test
    void preparedSettlementCannotInventSuccessAndClosedSchedulerRejectsOldReferences()
            throws Exception {
        var bodies = new AtomicInteger();
        try (var f =
                new M7EcosystemFixture(
                        root.resolve("settlement"),
                        new ToolSchedulingPlugin(ToolSchedulingOptions.serial()))) {
            f.start();
            f.tools.register(
                    tool(
                            "probe",
                            SideEffectLevel.READ_ONLY,
                            (call, c) -> {
                                bodies.incrementAndGet();
                                return success(call);
                            }),
                    M7EcosystemFixture.SCOPE);
            var context =
                    new ToolPipelineContext(
                            M7EcosystemFixture.SCOPE,
                            "t",
                            "step",
                            f.tools,
                            f.writer,
                            f.events,
                            new DefaultPolicyEngine(),
                            ApprovalService.rejectAll(),
                            new DefaultCheckpointPolicy(),
                            CancellationToken.NONE);
            var prepared =
                    f.pipeline.prepare(call("fake", "probe"), context, () -> {}).orElseThrow();
            assertEquals(
                    "EXECUTION_BYPASSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> prepared.finish(success(call("fake", "probe"))))
                            .code());
            var actual = prepared.run();
            assertEquals(ToolStatus.SUCCESS, prepared.finish(actual).status());
            assertEquals(1, bodies.get());
            var scheduler = new BoundedReadOnlyToolScheduler(parallel("probe"));
            scheduler.close();
            assertEquals(
                    "SCHEDULER_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            scheduler.execute(
                                                    List.of(call("a", "probe")),
                                                    context,
                                                    f.pipeline))
                            .code());
        }
    }

    @Test
    void serialIsDefaultAndTrustedSchedulerServiceCanBeReplacedAndRevoked() throws Exception {
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var read =
                tool(
                        "probe",
                        SideEffectLevel.READ_ONLY,
                        (call, context) -> {
                            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                            try {
                                Thread.sleep(25);
                                return success(call);
                            } finally {
                                active.decrementAndGet();
                            }
                        });
        try (var f =
                new Fixture(
                        "serial",
                        ToolSchedulingOptions.serial(),
                        List.of(read),
                        new DefaultPolicyEngine(),
                        ApprovalService.rejectAll(),
                        call("a", "probe"),
                        call("b", "probe"))) {
            assertEquals(
                    TaskStatus.COMPLETED, f.sdk.run("serial", CancellationToken.NONE).status());
            assertEquals(1, peak.get());
            assertTrue(
                    f.events().stream()
                            .filter(e -> e.type().equals("tool.scheduled"))
                            .allMatch(e -> e.payload().get("mode").equals("serial")));
        }
        var closed = new AtomicBoolean();
        var invoked = new AtomicInteger();
        var scope = new PluginScope("s", "default");
        var registry = new DefaultServiceRegistry();
        ToolScheduler replacement =
                new ToolScheduler() {
                    public List<ToolResult> execute(
                            List<ToolInvocation> calls, ToolPipelineContext c, ToolPipeline p) {
                        invoked.incrementAndGet();
                        return List.of();
                    }

                    public void close() {
                        closed.set(true);
                    }
                };
        var plugin = new ToolSchedulingPlugin(replacement);
        try (var f = new M7EcosystemFixture(root.resolve("replacement"), plugin)) {
            f.start();
            var context =
                    new ToolPipelineContext(
                            scope,
                            "t",
                            "step",
                            f.tools,
                            f.writer,
                            f.container.events(),
                            new DefaultPolicyEngine(),
                            ApprovalService.rejectAll(),
                            new DefaultCheckpointPolicy(),
                            CancellationToken.NONE);
            var batch =
                    f.container
                            .events()
                            .invoke(
                                    StandardExtensionPoints.TOOL_BATCH,
                                    ToolBatchExecution.serial(),
                                    v -> v);
            assertEquals(List.of(), batch.execute(List.of(), context, f.pipeline));
            assertEquals(1, invoked.get());
            var wrong =
                    new ToolPipelineContext(
                            new PluginScope("other", "default"),
                            "t",
                            "step",
                            f.tools,
                            f.writer,
                            f.container.events(),
                            new DefaultPolicyEngine(),
                            ApprovalService.rejectAll(),
                            new DefaultCheckpointPolicy(),
                            CancellationToken.NONE);
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> batch.execute(List.of(), wrong, f.pipeline))
                            .code());
            f.container.stop();
            assertTrue(closed.get());
            assertEquals(
                    "CONTEXT_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> batch.execute(List.of(), context, f.pipeline))
                            .code());
        }
    }
}
