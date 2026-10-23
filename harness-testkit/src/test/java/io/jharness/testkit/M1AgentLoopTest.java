package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;
import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.llm.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import io.jharness.tools.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class M1AgentLoopTest {
    @Test
    @org.junit.jupiter.api.Timeout(5)
    void expiredDeadlineDoesNotReplaceUnknownExecutionFact() {
        var llm =
                new FakeLlm(
                        List.of(
                                response("call", call("uncertain", "late")),
                                response("must not send")));
        try (Fixture f = new Fixture(llm)) {
            f.budget = new Budget(2, Duration.ofMillis(500));
            f.tool(
                    "uncertain",
                    call -> {
                        long start = System.nanoTime();
                        while (System.nanoTime() - start < Duration.ofMillis(700).toNanos())
                            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                        return ToolResults.failure(
                                call.callId(), ToolStatus.UNKNOWN, "UNKNOWN_SIDE_EFFECT");
                    });
            f.run();
            var finalState = f.events().getLast().payload();
            assertEquals(TaskStatus.FAILED, f.status());
            assertEquals("UNKNOWN_SIDE_EFFECT", finalState.get("reason"));
            assertEquals("UNKNOWN_SIDE_EFFECT", finalState.get("originalError"));
            assertEquals(true, finalState.get("deadlineExceeded"));
            assertEquals(1, llm.requests().size());
            assertEquals(List.of("late"), f.project().uncertainCalls());
        }
    }

    @Test
    void unknownSideEffectsStopTheTurnSettleOtherCallsAndPreventAutomaticNextTurn() {
        FakeLlm llm =
                new FakeLlm(
                        List.of(
                                response("calls", call("uncertain", "a"), call("later", "b")),
                                response("should not send")));
        try (Fixture f = new Fixture(llm)) {
            f.tool(
                    "uncertain",
                    c ->
                            ToolResults.failure(
                                    c.callId(), ToolStatus.UNKNOWN, "UNKNOWN_SIDE_EFFECT"));
            f.tool(
                    "later",
                    c -> {
                        fail("later call must not run");
                        return null;
                    });
            f.run();
            assertEquals(TaskStatus.FAILED, f.status());
            assertEquals(List.of("uncertain:a"), f.execution);
            assertEquals(1, llm.requests().size());
            f.run();
            assertEquals("UNKNOWN_SIDE_EFFECT", f.events().getLast().payload().get("reason"));
            assertEquals(1, llm.requests().size());
        }
    }

    private static final PluginScope SCOPE = new PluginScope("s", "agent");

    @Test
    void multistepTaskRunsReadEditTestInOrderAndEverySentRequestReplaysFromPrefix() {
        FakeLlm llm =
                new FakeLlm(
                        List.of(
                                response("read", call("read_file", "c1")),
                                response("edit", call("edit_file", "c2")),
                                response("test", call("execute_command", "c3")),
                                response("done")));
        try (Fixture f = new Fixture(llm)) {
            f.tool("read_file", c -> ok(c, false));
            f.tool("edit_file", c -> ok(c, false));
            f.tool("execute_command", c -> ok(c, true));
            f.run();
            assertEquals(TaskStatus.COMPLETED, f.status());
            assertEquals(
                    List.of("read_file:c1", "edit_file:c2", "execute_command:c3"), f.execution);
            assertEquals(4, llm.requests().size());
            assertReplay(f, llm.requests());
            assertEquals(
                    List.of("c1", "c2"),
                    llm.requests().get(2).messages().stream()
                            .filter(m -> "tool".equals(m.get("role")))
                            .map(m -> m.get("tool_call_id"))
                            .toList());
        }
    }

    @Test
    void multipleCallsInOneResponseExecuteSeriallyAndPreserveIdentifiers() {
        FakeLlm llm =
                new FakeLlm(
                        List.of(
                                response("calls", call("first", "a"), call("second", "b")),
                                response("done")));
        try (Fixture f = new Fixture(llm)) {
            f.tool("first", c -> ok(c, false));
            f.tool("second", c -> ok(c, true));
            f.run();
            assertEquals(List.of("first:a", "second:b"), f.execution);
            assertEquals(TaskStatus.COMPLETED, f.status());
            assertReplay(f, llm.requests());
        }
    }

    @Test
    void failedToolResultReachesModelAndARecordedSuccessfulRepairCanFinish() {
        FakeLlm llm =
                new FakeLlm(
                        List.of(
                                response("try", call("build", "c1")),
                                response("retry", call("build", "c2")),
                                response("fixed")));
        try (Fixture f = new Fixture(llm)) {
            AtomicInteger attempts = new AtomicInteger();
            f.tool(
                    "build",
                    c ->
                            attempts.incrementAndGet() == 1
                                    ? ToolResults.failure(
                                            c.callId(), ToolStatus.FAILED, "COMPILE_FAILED")
                                    : ok(c, true));
            f.run();
            assertEquals(TaskStatus.COMPLETED, f.status());
            assertTrue(llm.requests().get(1).messages().toString().contains("COMPILE_FAILED"));
            assertEquals(2, attempts.get());
            assertReplay(f, llm.requests());
        }
    }

    @Test
    void pureModelSuccessClaimCannotOverrideMissingGoalEvidence() {
        try (Fixture f = new Fixture(new FakeLlm(List.of(response("I fixed everything"))))) {
            f.run();
            assertEquals(TaskStatus.FAILED, f.status());
            assertEquals("GOAL_NOT_VERIFIED", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void missingToolAndSchemaErrorsArePairedAndReturnedBeforeFinalEvidenceEvaluation() {
        FakeLlm llm =
                new FakeLlm(
                        List.of(
                                response("unknown", call("missing", "a")),
                                response(
                                        "bad",
                                        new ToolInvocation(
                                                "b",
                                                "echo",
                                                Map.of("text", 7),
                                                SideEffectLevel.READ_ONLY)),
                                response("done")));
        try (Fixture f = new Fixture(llm)) {
            f.tool("echo", c -> ok(c, true));
            f.run();
            assertEquals(TaskStatus.FAILED, f.status());
            assertTrue(f.execution.isEmpty());
            assertTrue(llm.requests().get(1).messages().toString().contains("UNKNOWN_TOOL"));
            assertTrue(llm.requests().get(2).messages().toString().contains("SCHEMA_MISMATCH"));
            assertReplay(f, llm.requests());
        }
    }

    @Test
    void duplicateCallIdsAndModelFailureEndTheTurnExplicitly() {
        try (Fixture f =
                new Fixture(
                        new FakeLlm(
                                List.of(
                                        response(
                                                "bad",
                                                call("echo", "same"),
                                                call("echo", "same")))))) {
            f.tool("echo", c -> ok(c, true));
            f.run();
            assertEquals("DUPLICATE_CALL_ID", f.events().getLast().payload().get("reason"));
            assertEquals(TaskStatus.FAILED, f.status());
            assertEquals(
                    1, f.events().stream().filter(e -> e.type().equals("model.requested")).count());
            assertTrue(f.execution.isEmpty());
        }
        try (Fixture f = new Fixture(new FakeLlm(List.of()))) {
            f.run();
            assertEquals(TaskStatus.FAILED, f.status());
            assertEquals("FAKE_EXHAUSTED", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void stepAndTimeBudgetsTerminateWithoutClaimingCompletion() {
        try (Fixture f = new Fixture(new FakeLlm(List.of(response("call", call("echo", "a")))))) {
            f.tool("echo", c -> ok(c, true));
            f.budget = new Budget(1, Duration.ofSeconds(3));
            f.run();
            assertEquals(TaskStatus.LIMIT_REACHED, f.status());
            assertEquals("STEP_LIMIT", f.events().getLast().payload().get("reason"));
        }
        FakeLlm llm = new FakeLlm(List.of(response("late")));
        try (Fixture f = new Fixture(llm)) {
            f.budget = new Budget(2, Duration.ofNanos(1));
            f.run();
            assertEquals(TaskStatus.LIMIT_REACHED, f.status());
            assertTrue(llm.requests().isEmpty());
            assertEquals("TIME_LIMIT", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void cancellationSettlesUnexecutedCallsAndNoSecondBodyRuns() {
        AtomicBoolean cancelled = new AtomicBoolean();
        FakeLlm llm =
                new FakeLlm(List.of(response("two", call("first", "a"), call("second", "b"))));
        try (Fixture f = new Fixture(llm)) {
            f.cancellation = cancelled::get;
            f.tool(
                    "first",
                    c -> {
                        cancelled.set(true);
                        return ok(c, false);
                    });
            f.tool(
                    "second",
                    c -> {
                        fail("second tool executed after cancellation");
                        return null;
                    });
            f.run();
            assertEquals(TaskStatus.CANCELLED, f.status());
            assertEquals(List.of("first:a"), f.execution);
            SessionEvent second =
                    f.events().stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "b".equals(e.callId()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("CANCELLED", second.payload().get("status"));
            assertEquals(
                    2,
                    f.project().messages().stream()
                            .filter(m -> "tool".equals(m.get("role")))
                            .count());
        }
    }

    @Test
    void cancellationDuringPrepareSendsNothingAndNoRequestWasCommitted() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean sent = new AtomicBoolean();
        LlmClient llm =
                new LlmClient() {
                    @Override
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(true, false, false, false, 8192);
                    }

                    @Override
                    public PreparedRequest prepare(ChatRequest r) {
                        cancelled.set(true);
                        return new PreparedRequest(r);
                    }

                    @Override
                    public ChatResponse complete(ChatRequest r, CancellationToken c) {
                        sent.set(true);
                        return response("bad");
                    }

                    @Override
                    public StreamHandle stream(ChatRequest r, CancellationToken c) {
                        throw new UnsupportedOperationException();
                    }
                };
        try (Fixture f = new Fixture(llm)) {
            f.cancellation = cancelled::get;
            f.run();
            assertEquals(TaskStatus.CANCELLED, f.status());
            assertFalse(sent.get());
            assertTrue(f.events().stream().noneMatch(e -> e.type().equals("model.requested")));
        }
    }

    @Test
    void memoryStoreCannotSatisfyDurableModelCheckpointAndNothingIsSent() {
        FakeLlm llm = new FakeLlm(List.of(response("no")));
        try (Fixture f = new Fixture(llm)) {
            f.checkpoints = new DefaultCheckpointPolicy();
            f.run();
            assertEquals(TaskStatus.FAILED, f.status());
            assertTrue(llm.requests().isEmpty());
            assertEquals("DURABILITY_REQUIRED", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void requestsRecordInterceptorAndPreparationChangesAndScrubInputAndOutputSecrets() {
        FakeLlm llm = new FakeLlm(List.of(response("private-sentinel")));
        try (Fixture f = new Fixture(llm)) {
            f.writer.append(
                    SessionEvent.session(
                            "s",
                            "context.injected",
                            Map.of("role", "user", "content", "private-sentinel")));
            f.bus.intercept(
                    StandardExtensionPoints.BEFORE_MODEL,
                    (request, next) ->
                            next.proceed(
                                    new ChatRequest(
                                            request.messages(),
                                            request.tools(),
                                            Map.of("temperature", 0.5))),
                    0);
            f.run();
            assertReplay(f, llm.requests());
            assertEquals(Map.of("temperature", 0.5), llm.requests().getFirst().parameters());
            assertFalse(f.events().toString().contains("private-sentinel"));
            assertFalse(llm.requests().toString().contains("private-sentinel"));
        }
    }

    @Test
    void capabilityDiscoveryRemovesToolDeclarationsAndContextLimitPreventsSending() {
        FakeLlm fake = new FakeLlm(List.of(response("text")));
        LlmClient textOnly =
                new LlmClient() {
                    @Override
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(false, false, false, false, 8192);
                    }

                    @Override
                    public PreparedRequest prepare(ChatRequest r) {
                        return new PreparedRequest(r);
                    }

                    @Override
                    public ChatResponse complete(ChatRequest r, CancellationToken c) {
                        return fake.complete(r, c);
                    }

                    @Override
                    public StreamHandle stream(ChatRequest r, CancellationToken c) {
                        throw new UnsupportedOperationException();
                    }
                };
        try (Fixture f = new Fixture(textOnly)) {
            f.tool("echo", c -> ok(c, true));
            f.run();
            assertTrue(fake.requests().getFirst().tools().isEmpty());
        }
        FakeLlm llm = new FakeLlm(List.of(response("too much")));
        try (Fixture f = new Fixture(llm)) {
            f.writer.append(
                    SessionEvent.session(
                            "s",
                            "user.message",
                            Map.of("role", "user", "content", "x".repeat(9000))));
            f.run();
            assertEquals("CONTEXT_LIMIT", f.events().getLast().payload().get("reason"));
            assertTrue(llm.requests().isEmpty());
        }
    }

    @Test
    void pluginCompositionRewritesParametersAndSkipsReadOnlyApprovalAndRevokesEverything() {
        FakeLlm llm = new FakeLlm(List.of(response("tool", call("echo", "a")), response("done")));
        try (Fixture f = new Fixture(llm)) {
            f.tool(
                    "echo",
                    c -> {
                        assertEquals("rewritten", c.arguments().get("text"));
                        return ok(c, true);
                    });
            f.policy = (call, c) -> PolicyEngine.Decision.ASK;
            AtomicInteger asks = new AtomicInteger();
            f.approvals =
                    (call, c) -> {
                        asks.incrementAndGet();
                        return new ApprovalService.ApprovalDecision(false, "no", "user");
                    };
            DefaultServiceRegistry registry = new DefaultServiceRegistry();
            ToolPipeline proxy =
                    (call, c) -> registry.requireUnique(ToolPipeline.class, SCOPE).execute(call, c);
            HarnessPlugin extension =
                    new HarnessPlugin() {
                        @Override
                        public String id() {
                            return "readonly-extension";
                        }

                        @Override
                        public String version() {
                            return "1.1";
                        }

                        @Override
                        public void start(PluginContext c) {
                            c.events()
                                    .intercept(
                                            StandardExtensionPoints.BEFORE_TOOL,
                                            (call, next) ->
                                                    next.proceed(
                                                            new ToolInvocation(
                                                                    call.callId(),
                                                                    call.tool(),
                                                                    Map.of("text", "rewritten"),
                                                                    call.sideEffectLevel())),
                                            0);
                            c.events()
                                    .intercept(
                                            PolicyExtensionPoints.EVALUATE,
                                            (policy, next) ->
                                                    next.proceed(
                                                            (call, context) ->
                                                                    call.sideEffectLevel()
                                                                                    == SideEffectLevel
                                                                                            .READ_ONLY
                                                                            ? PolicyEngine.Decision
                                                                                    .ALLOW
                                                                            : policy.evaluate(
                                                                                    call, context)),
                                            0);
                        }

                        @Override
                        public void stop() {}
                    };
            List<HarnessPlugin> plugins =
                    List.of(
                            new ToolsPlugin(f.sanitizer),
                            new LlmPlugin(llm),
                            new AgentPlugin(f.runtime(proxy)),
                            extension);
            try (PluginContainer container =
                    new PluginContainer(
                            SCOPE,
                            registry,
                            plugins,
                            Set.of(
                                    "tools-pipeline",
                                    "llm-provider",
                                    "agent-default",
                                    "readonly-extension"),
                            Map.of(),
                            List.of(
                                    ServiceRequirement.unique(AgentLoopFactory.class),
                                    ServiceRequirement.unique(LlmClient.class),
                                    ServiceRequirement.unique(ToolPipeline.class)))) {
                container.start();
                registry.requireUnique(AgentLoopFactory.class, SCOPE)
                        .create(
                                f.core(
                                        container.events(),
                                        registry.requireUnique(LlmClient.class, SCOPE)))
                        .run(CancellationToken.NONE);
                assertEquals(TaskStatus.COMPLETED, f.status());
                assertEquals(0, asks.get());
                assertReplay(f, llm.requests());
                container.stop();
                assertTrue(registry.find(ToolPipeline.class, "default", SCOPE).isEmpty());
                assertEquals(
                        f.policy,
                        container
                                .events()
                                .invoke(PolicyExtensionPoints.EVALUATE, f.policy, value -> value));
            }
        }
    }

    @Test
    void localHttpAdapterAndAlternateStorageRunTheSameRuntimeAndReplayContract() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    String body =
                            requests.getAndIncrement() == 0
                                    ? "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"http-call\",\"type\":\"function\",\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"text\\\":\\\"input\\\"}\"}}]}}]}"
                                    : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}";
                    byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        try (OpenAiCompatibleLlmClient llm =
                        new OpenAiCompatibleLlmClient(
                                new OpenAiCompatibleConfig(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + server.getAddress().getPort()
                                                        + "/v1"),
                                        "test-model",
                                        "mock-key",
                                        Duration.ofSeconds(3),
                                        4096,
                                        0,
                                        new ModelCapabilities(true, false, false, false, 8192)));
                Fixture f = new Fixture(llm, new AlternateStorage())) {
            f.tool("echo", c -> ok(c, true));
            f.run();
            assertEquals(TaskStatus.COMPLETED, f.status());
            assertEquals(2, requests.get());
            for (SessionEvent event : f.events())
                if (event.type().equals("model.requested"))
                    assertEquals(
                            "test-model",
                            new EventRequestJournal()
                                    .reconstruct(f.events(), event.eventId())
                                    .parameters()
                                    .get("model"));
        } finally {
            server.stop(0);
            workers.shutdownNow();
        }
    }

    private static void assertReplay(Fixture f, List<ChatRequest> captured) {
        List<SessionEvent> events = f.events();
        int requestIndex = 0;
        for (int index = 0; index < events.size(); index++)
            if (events.get(index).type().equals("model.requested")) {
                assertEquals(
                        captured.get(requestIndex++),
                        new EventRequestJournal()
                                .reconstruct(
                                        events.subList(0, index + 1), events.get(index).eventId()));
            }
        assertEquals(captured.size(), requestIndex);
    }

    private static ToolInvocation call(String name, String id) {
        return new ToolInvocation(id, name, Map.of("text", "input"), SideEffectLevel.SYSTEM_IMPACT);
    }

    private static ChatResponse response(String text, ToolInvocation... calls) {
        return new ChatResponse(text, List.of(calls), Map.of());
    }

    private static ToolResult ok(ToolInvocation c, boolean verified) {
        return new ToolResult(
                c.callId(),
                ToolStatus.SUCCESS,
                Map.of("verified", verified),
                "recorded-result",
                Map.of(),
                Optional.empty());
    }

    private static final class AlternateStorage implements SessionRepository {
        private final InMemorySessionRepository delegate = new InMemorySessionRepository();

        @Override
        public SessionWriter openWriter(String id, WriteOwnership ownership) {
            SessionWriter writer = delegate.openWriter(id, ownership);
            return new SessionWriter() {
                @Override
                public boolean durable() {
                    return writer.durable();
                }

                @Override
                public String append(SessionEvent e) {
                    return writer.append(e);
                }

                @Override
                public void flush() {
                    writer.flush();
                }

                @Override
                public void close() {
                    writer.close();
                }
            };
        }

        @Override
        public SessionReader openReader(String id) {
            return delegate.openReader(id);
        }

        @Override
        public List<String> list() {
            return delegate.list();
        }

        @Override
        public String fork(String id, String event) {
            return delegate.fork(id, event);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SessionRepository repo;
        final SessionWriter writer;
        final SessionReader reader;
        final SecretRedactor sanitizer = new SecretRedactor(List.of("private-sentinel"));
        final DefaultEventBus bus = new DefaultEventBus();
        final ScopedToolRegistry tools = new ScopedToolRegistry();
        final List<String> execution = new ArrayList<>();
        final LlmClient llm;
        Budget budget = new Budget(8, Duration.ofSeconds(5));
        CancellationToken cancellation = CancellationToken.NONE;
        PolicyEngine policy = (call, c) -> PolicyEngine.Decision.ALLOW;
        ApprovalService approvals = ApprovalService.rejectAll();
        CheckpointPolicy checkpoints = new MemoryCheckpointPolicy();

        Fixture(LlmClient llm) {
            this(llm, new InMemorySessionRepository());
        }

        Fixture(LlmClient llm, SessionRepository repository) {
            this.llm = llm;
            repo = repository;
            writer =
                    new SanitizingSessionWriter(
                            repo.openWriter("s", WriteOwnership.EXCLUSIVE), sanitizer);
            reader = repo.openReader("s");
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
            writer.append(
                    SessionEvent.session(
                            "s",
                            "system.message",
                            Map.of("role", "system", "content", "Be precise")));
            writer.append(
                    SessionEvent.session(
                            "s",
                            "user.message",
                            Map.of("role", "user", "content", "Perform the task")));
        }

        SessionProjection project() {
            return new EventSessionProjection(reader.events());
        }

        List<SessionEvent> events() {
            return reader.events();
        }

        TaskStatus status() {
            return project().taskStatus();
        }

        AgentRuntimeServices runtime(ToolPipeline pipeline) {
            CompletionEvaluator completion =
                    (response, projection) -> {
                        Optional<SessionEvent> last =
                                projection.toolHistory().stream()
                                        .filter(e -> e.type().equals("tool.completed"))
                                        .reduce((a, b) -> b);
                        boolean verified =
                                last.isPresent()
                                        && "SUCCESS".equals(last.get().payload().get("status"))
                                        && Boolean.TRUE.equals(
                                                Values.object(last.get().payload().get("value"))
                                                        .get("verified"));
                        return new CompletionEvaluator.CompletionDecision(
                                verified, verified ? "GOAL_VERIFIED" : "GOAL_NOT_VERIFIED");
                    };
            return new AgentRuntimeServices(
                    SCOPE,
                    reader,
                    EventSessionProjection::new,
                    checkpoints,
                    approvals,
                    pipeline,
                    new EventRequestJournal(),
                    sanitizer,
                    completion);
        }

        AgentLoopDependencies core(EventBus events, LlmClient model) {
            return new AgentLoopDependencies(
                    model,
                    tools,
                    writer,
                    project(),
                    new DefaultPromptComposer(tools, SCOPE, Map.of()),
                    events,
                    policy,
                    cancellation,
                    budget);
        }

        void run() {
            new DefaultAgentLoopFactory(
                            runtime(new DefaultToolPipeline(new JsonSchemaValidator(), sanitizer)))
                    .create(core(bus, llm))
                    .run(CancellationToken.NONE);
        }

        void tool(String name, Function<ToolInvocation, ToolResult> body) {
            tools.register(
                    new AgentTool() {
                        @Override
                        public String name() {
                            return name;
                        }

                        @Override
                        public String description() {
                            return "simulated " + name;
                        }

                        @Override
                        public Map<String, Object> inputSchema() {
                            return Map.of(
                                    "type",
                                    "object",
                                    "required",
                                    List.of("text"),
                                    "properties",
                                    Map.of("text", Map.of("type", "string")));
                        }

                        @Override
                        public Map<String, Object> outputSchema() {
                            return Map.of("type", "object");
                        }

                        @Override
                        public SideEffectLevel sideEffectLevel() {
                            return SideEffectLevel.READ_ONLY;
                        } // No real files or processes in M1.

                        @Override
                        public ToolResult execute(ToolInvocation call, ToolContext context) {
                            execution.add(name + ":" + call.callId());
                            return body.apply(call);
                        }
                    },
                    SCOPE);
        }

        @Override
        public void close() {
            writer.close();
            bus.close();
        }
    }
}
