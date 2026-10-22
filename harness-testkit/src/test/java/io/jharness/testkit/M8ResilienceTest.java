package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.cli.CliApplication;
import io.jharness.ecosystem.*;
import io.jharness.llm.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M8ResilienceTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper();
    static final ChatResponse OK = new ChatResponse("done", List.of(), Map.of());

    static ModelResilienceOptions options(
            int attempts, int backoff, int timeout, int interval, int waiters) {
        return new ModelResilienceOptions(true, attempts, backoff, timeout, interval, waiters);
    }

    static String code(Runnable action) {
        return assertThrows(HarnessException.class, action::run).code();
    }

    final class Server implements AutoCloseable {
        final HttpServer server;
        final List<Map<String, Object>> wire = new CopyOnWriteArrayList<>();
        final int rejected;

        Server(int rejected) throws Exception {
            this(rejected, false);
        }

        Server(int rejected, boolean read) throws Exception {
            this.rejected = rejected;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/chat/completions",
                    exchange -> {
                        wire.add(
                                Values.object(
                                        JSON.readValue(exchange.getRequestBody(), Map.class)));
                        if (read && wire.size() == rejected + 1) {
                            byte[] body =
                                    JSON.writeValueAsBytes(
                                            M4CliIntegrationTest.calls(
                                                    M4CliIntegrationTest.call(
                                                            "read",
                                                            "read_file",
                                                            Map.of("path", "input.txt"))));
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, body.length);
                            exchange.getResponseBody().write(body);
                            exchange.close();
                            return;
                        }
                        byte[] body =
                                (wire.size() <= rejected
                                                ? "{}"
                                                : "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"},\"finish_reason\":\"stop\"}]}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(
                                wire.size() <= rejected ? 429 : 200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
        }

        LlmClient adapter(int legacyRetries) {
            return new OpenAiCompatibleLlmClient(
                    new OpenAiCompatibleConfig(
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                            "fixture",
                            "fixture-secret",
                            Duration.ofSeconds(3),
                            65536,
                            legacyRetries,
                            new ModelCapabilities(true, false, false, false, 32768)));
        }

        public void close() {
            server.stop(0);
        }
    }

    final class Fixture implements AutoCloseable {
        final JsonlSessionRepository disk;
        final HarnessSdk sdk;
        final AtomicBoolean failFlush = new AtomicBoolean();
        final AtomicBoolean failRejectedFlush = new AtomicBoolean();

        Fixture(String name, LlmClient client, ModelResilienceOptions options, BigDecimal maxCost)
                throws Exception {
            Path base = Files.createDirectories(root.resolve(name));
            disk = new JsonlSessionRepository(base.resolve("sessions"));
            SessionRepository repo =
                    new SessionRepository() {
                        public SessionWriter openWriter(String session, WriteOwnership ownership) {
                            var writer = disk.openWriter(session, ownership);
                            return new SessionWriter() {
                                boolean armed;

                                public boolean durable() {
                                    return true;
                                }

                                public String append(SessionEvent event) {
                                    if (event.type().equals("model.requested")) armed = true;
                                    if (event.type().equals("model.rejected")
                                            && failRejectedFlush.get()) failFlush.set(true);
                                    return writer.append(event);
                                }

                                public void flush() {
                                    if (armed && failFlush.compareAndSet(true, false))
                                        throw new HarnessException(
                                                "CHECKPOINT_FAILED", "injected flush");
                                    writer.flush();
                                }

                                public void close() {
                                    writer.close();
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
            sdk =
                    new HarnessSdk(
                            "s",
                            "binding",
                            repo,
                            new LocalExecutionEnvironment("local", base, List.of()),
                            client,
                            new LocalArtifactStore(base.resolve("artifacts"), new SecretRedactor()),
                            new SecretRedactor(),
                            new DefaultPolicyEngine(),
                            ApprovalService.rejectAll(),
                            new Budget(3, Duration.ofSeconds(5), Optional.ofNullable(maxCost)),
                            (response, projection) ->
                                    new CompletionEvaluator.CompletionDecision(
                                            true, "fixture response"),
                            request -> BigDecimal.ONE,
                            List.of(),
                            new SdkConfiguration(
                                    "",
                                    Set.of(),
                                    Map.of(),
                                    ToolSchedulingOptions.serial(),
                                    options));
        }

        List<SessionEvent> events() {
            return disk.openReader("s").events();
        }

        long count(String type) {
            return events().stream().filter(e -> e.type().equals(type)).count();
        }

        String reason() {
            return events().stream()
                    .filter(e -> e.type().equals("turn.ended"))
                    .reduce((a, b) -> b)
                    .orElseThrow()
                    .payload()
                    .get("reason")
                    .toString();
        }

        public void close() {
            sdk.close();
        }
    }

    @Test
    void everyRetryHasItsOwnDurableReplayAndNoAdapterMultiplication() throws Exception {
        try (var server = new Server(1);
                var f =
                        new Fixture(
                                "replay",
                                server.adapter(3),
                                options(3, 1, 3000, 0, 0),
                                new BigDecimal("4"))) {
            assertEquals(TaskStatus.COMPLETED, f.sdk.run("hello", CancellationToken.NONE).status());
            assertEquals(2, server.wire.size());
            assertEquals(2, f.count("model.requested"));
            assertEquals(2, f.count("budget.reserved"));
            assertEquals(1, f.count("model.rejected"));
            assertEquals(1, f.count("model.retry.scheduled"));
            var events = f.events();
            int sent = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested")) {
                    ChatRequest replay =
                            new EventRequestJournal()
                                    .reconstruct(events.subList(0, i + 1), events.get(i).eventId());
                    assertEquals(replay.messages(), server.wire.get(sent).get("messages"));
                    assertEquals(
                            replay.parameters().get("model"), server.wire.get(sent++).get("model"));
                }
            assertTrue(SessionRecovery.inspect(events).ready());
        }
    }

    @Test
    void disabledPolicyDoesNotAddRetriesAndLegacyAdapterStillOwnsItsBehavior() throws Exception {
        try (var server = new Server(9);
                var f =
                        new Fixture(
                                "default",
                                server.adapter(0),
                                ModelResilienceOptions.disabled(),
                                null)) {
            assertEquals(TaskStatus.FAILED, f.sdk.run("hello", CancellationToken.NONE).status());
            assertEquals(1, server.wire.size());
            assertEquals(0, f.count("model.retry.scheduled"));
        }
        try (var server = new Server(1);
                var f =
                        new Fixture(
                                "legacy",
                                server.adapter(1),
                                ModelResilienceOptions.disabled(),
                                null)) {
            assertEquals(TaskStatus.COMPLETED, f.sdk.run("hello", CancellationToken.NONE).status());
            assertEquals(2, server.wire.size());
            assertEquals(1, f.count("model.requested"));
        }
    }

    @Test
    void attemptAndFeeLimitsStopBeforeAnotherWireRequest() throws Exception {
        try (var server = new Server(9);
                var f =
                        new Fixture(
                                "attempts", server.adapter(3), options(2, 0, 3000, 0, 0), null)) {
            f.sdk.run("hello", CancellationToken.NONE);
            assertEquals("MODEL_RATE_LIMIT", f.reason());
            assertEquals(2, server.wire.size());
            assertTrue(SessionRecovery.inspect(f.events()).ready());
        }
        try (var server = new Server(9);
                var f =
                        new Fixture(
                                "fees",
                                server.adapter(3),
                                options(4, 0, 3000, 0, 0),
                                BigDecimal.ONE)) {
            assertEquals(
                    TaskStatus.LIMIT_REACHED, f.sdk.run("hello", CancellationToken.NONE).status());
            assertEquals("COST_LIMIT", f.reason());
            assertEquals(1, server.wire.size());
        }
    }

    @Test
    void checkpointFailureNeverEntersTransport() throws Exception {
        try (var server = new Server(0);
                var f = new Fixture("flush", server.adapter(3), options(3, 0, 3000, 0, 0), null)) {
            f.failFlush.set(true);
            f.sdk.run("hello", CancellationToken.NONE);
            assertEquals(0, server.wire.size());
            assertEquals("CHECKPOINT_FAILED", f.reason());
            assertFalse(SessionRecovery.inspect(f.events()).ready());
        }
    }

    static LlmClient unknown(AtomicInteger sends, String code) {
        return new LlmClient() {
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(false, false, false, false, 32768);
            }

            public PreparedRequest prepare(ChatRequest r) {
                return new PreparedRequest(r);
            }

            public ChatResponse complete(ChatRequest r, CancellationToken c) {
                sends.incrementAndGet();
                throw new HarnessException(code, code);
            }

            public ChatResponse completeSingleAttempt(ChatRequest r, CancellationToken c) {
                return complete(r, c);
            }

            public StreamHandle stream(ChatRequest r, CancellationToken c) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void unknownTimeoutStreamAndSpoofedRateCodeNeverReplay() throws Exception {
        for (String error :
                List.of(
                        "MODEL_TRANSPORT_UNKNOWN",
                        "MODEL_TIMEOUT",
                        "MODEL_STREAM_INCOMPLETE",
                        "MODEL_RATE_LIMIT")) {
            var sends = new AtomicInteger();
            try (var f =
                    new Fixture(error, unknown(sends, error), options(4, 0, 3000, 0, 0), null)) {
                f.sdk.run("hello", CancellationToken.NONE);
                assertEquals(1, sends.get());
                assertEquals(0, f.count("model.rejected"));
                assertFalse(SessionRecovery.inspect(f.events()).ready());
                assertEquals(
                        "UNKNOWN_SIDE_EFFECT",
                        code(() -> f.sdk.run("again", CancellationToken.NONE)));
            }
        }
    }

    @Test
    void deadlineIncludesBackoffAndDoesNotSendAgain() throws Exception {
        try (var server = new Server(9);
                var f =
                        new Fixture(
                                "deadline", server.adapter(3), options(4, 1000, 500, 0, 0), null)) {
            f.sdk.run("hello", CancellationToken.NONE);
            assertEquals("MODEL_RESILIENCE_TIMEOUT", f.reason());
            assertEquals(1, server.wire.size());
            assertTrue(SessionRecovery.inspect(f.events()).ready());
        }
    }

    @Test
    void boundedWaitQueueCancelsAndRejectsOverflowWithoutBody() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var cancelled = new AtomicBoolean();
        var bodies = new AtomicInteger();
        try (var policy =
                        new BoundedModelExecution(
                                options(2, 0, 3000, 0, 1),
                                (type, payload) -> {
                                    if (type.equals("model.rate.waiting")) waiting.countDown();
                                });
                var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () ->
                                    policy.execute(
                                            (single, token) -> {
                                                entered.countDown();
                                                try {
                                                    assertTrue(release.await(2, TimeUnit.SECONDS));
                                                } catch (InterruptedException ex) {
                                                    throw new RuntimeException(ex);
                                                }
                                                return OK;
                                            },
                                            CancellationToken.NONE));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            var second =
                    pool.submit(
                            () ->
                                    code(
                                            () ->
                                                    policy.execute(
                                                            (single, token) -> {
                                                                bodies.incrementAndGet();
                                                                return OK;
                                                            },
                                                            cancelled::get)));
            try {
                assertTrue(waiting.await(1, TimeUnit.SECONDS));
                assertEquals(
                        "MODEL_RATE_QUEUE_FULL",
                        code(() -> policy.execute((s, t) -> OK, CancellationToken.NONE)));
                cancelled.set(true);
                assertEquals("CANCELLED", second.get(1, TimeUnit.SECONDS));
                assertEquals(0, bodies.get());
            } finally {
                release.countDown();
            }
            assertEquals(OK, first.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    @SuppressWarnings("try") // Explicit close is the lifecycle behavior under test.
    void intervalIsEnforcedAndWaitingCancelsOnClose() throws Exception {
        var starts = new ArrayList<Long>();
        try (var policy = new BoundedModelExecution(options(1, 0, 3000, 100, 0))) {
            for (int i = 0; i < 2; i++)
                policy.execute(
                        (s, t) -> {
                            starts.add(System.nanoTime());
                            return OK;
                        },
                        CancellationToken.NONE);
            assertTrue(starts.get(1) - starts.get(0) >= TimeUnit.MILLISECONDS.toNanos(90));
        }
        var waiting = new CountDownLatch(1);
        try (var policy =
                        new BoundedModelExecution(
                                options(1, 0, 3000, 2000, 0),
                                (type, payload) -> {
                                    if (type.equals("model.rate.waiting")) waiting.countDown();
                                });
                var pool = Executors.newSingleThreadExecutor()) {
            policy.execute((s, t) -> OK, CancellationToken.NONE);
            var next =
                    pool.submit(
                            () ->
                                    code(
                                            () ->
                                                    policy.execute(
                                                            (s, t) -> fail("must not send"),
                                                            CancellationToken.NONE)));
            assertTrue(waiting.await(1, TimeUnit.SECONDS));
            policy.close();
            assertEquals("MODEL_RESILIENCE_CLOSED", next.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void unloadRevokesRetainedExtensionAndReplacementService() throws Exception {
        var calls = new AtomicInteger();
        try (var f =
                new M7EcosystemFixture(
                        root.resolve("unload"),
                        new ModelResiliencePlugin(
                                (attempt, token) -> {
                                    calls.incrementAndGet();
                                    return attempt.complete(true, token);
                                }))) {
            f.start();
            var execution =
                    f.container
                            .events()
                            .invoke(
                                    StandardExtensionPoints.MODEL_EXECUTION,
                                    ModelExecution.once(),
                                    v -> v);
            assertEquals(OK, execution.execute((s, t) -> OK, CancellationToken.NONE));
            assertEquals(1, calls.get());
            f.container.stop();
            assertThrows(
                    HarnessException.class,
                    () -> execution.execute((s, t) -> fail("revoked"), CancellationToken.NONE));
        }
    }

    @Test
    void rejectedFactOnlySettlesItsExactAttempt() {
        var first = event("first", "model.requested", Map.of());
        var second = event("second", "model.requested", Map.of());
        var reject =
                event(
                        "reject",
                        "model.rejected",
                        Map.of("requestEventId", "first", "reason", "HTTP_429"));
        assertTrue(SessionRecovery.inspect(List.of(first, reject)).ready());
        assertFalse(SessionRecovery.isRequestPending(List.of(first, second, reject), "first"));
        assertTrue(SessionRecovery.isRequestPending(List.of(first, second, reject), "second"));
        var legacy = SessionEvent.session("s", "model.verified", Map.of("stepId", "step"));
        assertTrue(SessionRecovery.inspect(List.of(first, legacy)).ready());
        assertFalse(SessionRecovery.inspect(List.of(first, second, legacy)).ready());
        var verified =
                SessionEvent.session(
                        "s", "model.verified", Map.of("sourceEvent", "first", "stepId", "step"));
        assertTrue(SessionRecovery.isRequestPending(List.of(first, second, verified), "second"));
        assertEquals(
                List.of("step"),
                SessionRecovery.inspect(List.of(first, second, reject)).pendingRequests());
    }

    @Test
    void rejectionFlushFailureStopsBeforeRetry() throws Exception {
        try (var server = new Server(9);
                var f =
                        new Fixture(
                                "rejected-flush",
                                server.adapter(3),
                                options(4, 0, 3000, 0, 0),
                                null)) {
            f.failRejectedFlush.set(true);
            f.sdk.run("hello", CancellationToken.NONE);
            assertEquals("CHECKPOINT_FAILED", f.reason());
            assertEquals(1, server.wire.size());
            assertEquals(0, f.count("model.retry.scheduled"));
        }
    }

    @Test
    void cancellationDuringBackoffAndCooperativeSendDeadlineStopAttempts() {
        var cancelled = new AtomicBoolean();
        var sends = new AtomicInteger();
        try (var policy =
                new BoundedModelExecution(
                        options(4, 1000, 3000, 0, 0),
                        (type, payload) -> {
                            if (type.equals("model.retry.scheduled")) cancelled.set(true);
                        })) {
            assertEquals(
                    "CANCELLED",
                    code(
                            () ->
                                    policy.execute(
                                            (single, token) -> {
                                                sends.incrementAndGet();
                                                throw new ModelRejectedException();
                                            },
                                            cancelled::get)));
            assertEquals(1, sends.get());
        }
        try (var policy = new BoundedModelExecution(options(4, 0, 50, 0, 0))) {
            assertEquals(
                    "MODEL_RESILIENCE_TIMEOUT",
                    code(
                            () ->
                                    policy.execute(
                                            (single, token) -> {
                                                while (true) {
                                                    token.throwIfCancelled();
                                                    Thread.onSpinWait();
                                                }
                                            },
                                            CancellationToken.NONE)));
        }
    }

    @Test
    void classicAndEcosystemCliUseThePolicyAndObservedSingleAttemptWrappers() throws Exception {
        for (boolean ecosystem : List.of(false, true)) {
            Path workspace = Files.createDirectories(root.resolve(ecosystem ? "eco" : "classic"));
            Files.writeString(workspace.resolve("input.txt"), "evidence");
            try (var server = new Server(1, true)) {
                var config = new LinkedHashMap<String, Object>();
                config.put("baseUrl", "http://127.0.0.1:" + server.server.getAddress().getPort());
                config.put("model", "fixture");
                config.put("apiKeyEnv", "FIXTURE_API_KEY");
                config.put(
                        "modelResilience",
                        Map.of("enabled", true, "maxAttempts", 3, "backoffMillis", 1));
                if (ecosystem) config.put("ecosystem", Map.of());
                Path file = workspace.resolve("config.json");
                JSON.writeValue(file.toFile(), config);
                var output = new ByteArrayOutputStream();
                try (var out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                    int exit =
                            new CliApplication(Map.of("FIXTURE_API_KEY", "fixture-secret"))
                                    .execute(
                                            new String[] {
                                                "run",
                                                "read",
                                                "--workspace",
                                                workspace.toString(),
                                                "--config",
                                                file.toString(),
                                                "--session",
                                                "s",
                                                "--require-read",
                                                "input.txt"
                                            },
                                            new ByteArrayInputStream(new byte[0]),
                                            out,
                                            out);
                    assertEquals(0, exit, output.toString(StandardCharsets.UTF_8));
                }
                var events =
                        new JsonlSessionRepository(workspace.resolve(".jharness/sessions"))
                                .openReader("s")
                                .events();
                assertEquals(3, server.wire.size());
                assertEquals(
                        3, events.stream().filter(e -> e.type().equals("model.requested")).count());
                assertEquals(
                        1,
                        events.stream()
                                .filter(e -> e.type().equals("model.retry.scheduled"))
                                .count());
                assertEquals(
                        1, events.stream().filter(e -> e.type().equals("tool.completed")).count());
                assertTrue(SessionRecovery.inspect(events).ready());
                assertFalse(events.toString().contains("fixture-secret"));
                assertFalse(output.toString(StandardCharsets.UTF_8).contains("fixture-secret"));
                try (var files = Files.walk(workspace.resolve(".jharness"))) {
                    for (Path path : files.filter(Files::isRegularFile).toList())
                        assertFalse(
                                new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                                        .contains("fixture-secret"),
                                path.toString());
                }
            }
        }
    }

    static SessionEvent event(String id, String type, Map<String, Object> payload) {
        return new SessionEvent(
                id,
                "s",
                type,
                java.time.Instant.now(),
                1,
                EventLevel.STEP,
                "turn",
                "step",
                null,
                payload);
    }
}
