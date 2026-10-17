package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class M1053TurnsTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper();
    final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger opens = new AtomicInteger();
    volatile CountDownLatch entered = new CountDownLatch(0), release = new CountDownLatch(0);
    volatile boolean uncertain;
    JsonlSessionRepository sessions, catalog;

    @BeforeEach
    void setup() throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        sessions = new JsonlSessionRepository(root.resolve("sessions"));
        catalog = new JsonlSessionRepository(root.resolve("catalog"));
    }

    HarnessWebServer host() throws Exception {
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (id, approval, sanitizer) -> {
                            opens.incrementAndGet();
                            var model =
                                    new LlmClient() {
                                        public ModelCapabilities capabilities() {
                                            return new ModelCapabilities(
                                                    true, false, false, false, 200000);
                                        }

                                        public PreparedRequest prepare(ChatRequest request) {
                                            return new PreparedRequest(request);
                                        }

                                        public StreamHandle stream(
                                                ChatRequest r, CancellationToken c) {
                                            throw new UnsupportedOperationException();
                                        }

                                        public ChatResponse complete(
                                                ChatRequest request, CancellationToken cancel) {
                                            requests.add(request);
                                            entered.countDown();
                                            try {
                                                while (!release.await(20, TimeUnit.MILLISECONDS))
                                                    cancel.throwIfCancelled();
                                            } catch (InterruptedException e) {
                                                throw new CancellationException();
                                            }
                                            if (uncertain)
                                                throw new HarnessException(
                                                        "FIXTURE_UNKNOWN",
                                                        "request outcome unknown");
                                            return new ChatResponse(
                                                    "recorded reply", List.of(), Map.of());
                                        }
                                    };
                            return new HarnessSdk(
                                    id,
                                    "workspace",
                                    sessions,
                                    new LocalExecutionEnvironment(
                                            "local", root.resolve("workspace"), List.of()),
                                    model,
                                    new LocalArtifactStore(root.resolve("artifacts"), sanitizer),
                                    sanitizer,
                                    new DefaultPolicyEngine(),
                                    approval,
                                    new Budget(3, Duration.ofSeconds(20)),
                                    (reply, projection) ->
                                            new CompletionEvaluator.CompletionDecision(
                                                    false, "fixture has no goal evidence"),
                                    ApiCostGuard.unavailable(),
                                    List.of());
                        });
        return new HarnessWebServer(
                0,
                M10BrowserHost.TOKEN,
                Map.of("local", profile),
                catalog,
                "turns",
                new SecretRedactor(List.of("synthetic-secret")),
                1,
                32,
                Duration.ofSeconds(1));
    }

    HttpResponse<String> req(HarnessWebServer host, String path, Object body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(host.address() + path))
                        .header("Authorization", "Bearer " + M10BrowserHost.TOKEN);
        if (body == null) request.GET();
        else
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    Map<String, Object> data(HttpResponse<String> response) throws Exception {
        assertTrue(response.statusCode() < 300, response.statusCode() + ": " + response.body());
        return JSON.readValue(response.body(), new TypeReference<>() {});
    }

    String create(HarnessWebServer host) throws Exception {
        return (String)
                data(req(
                                host,
                                "/v1/tasks",
                                Map.of(
                                        "profile",
                                        "local",
                                        "goal",
                                        "first",
                                        "requestId",
                                        "initial")))
                        .get("taskId");
    }

    Map<String, Object> turn(
            HarnessWebServer host, String id, String request, String text, String kind)
            throws Exception {
        return data(
                req(
                        host,
                        "/v1/tasks/" + id + "/turns",
                        Map.of("requestId", request, "text", text, "kind", kind)));
    }

    void settled(HarnessWebServer host, String id, int turns) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var task = data(req(host, "/v1/tasks/" + id, null));
            if (Set.of("FINISHED", "UNKNOWN", "VERIFIED").contains(task.get("phase"))
                    && requests.size() >= turns) return;
            Thread.sleep(15);
        }
        fail("did not settle");
    }

    List<Map<String, Object>> inputs(HarnessWebServer host, String id) throws Exception {
        var values =
                (List<?>) data(req(host, "/v1/tasks/" + id + "/turns", null)).get("submissions");
        return values.stream().map(Values::object).toList();
    }

    @Test
    void threeTurnsRetainSessionPrefixAndIndependentIdempotence() throws Exception {
        try (var host = host()) {
            String id = create(host);
            settled(host, id, 1);
            turn(host, id, "second", "second input", "message");
            settled(host, id, 2);
            turn(host, id, "third", "third input", "supplement");
            settled(host, id, 3);
            turn(host, id, "second", "second input", "message");
            assertEquals(3, opens.get());
            assertEquals(
                    409,
                    req(
                                    host,
                                    "/v1/tasks/" + id + "/turns",
                                    Map.of(
                                            "requestId",
                                            "second",
                                            "text",
                                            "different",
                                            "kind",
                                            "message"))
                            .statusCode());
            var events = sessions.openReader(id).events();
            int n = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested")) {
                    assertEquals(
                            requests.get(n++),
                            new EventRequestJournal()
                                    .reconstruct(
                                            events.subList(0, i + 1), events.get(i).eventId()));
                }
            assertEquals(3, n);
            assertEquals(
                    3,
                    events.stream()
                            .filter(e -> e.type().equals("turn.started"))
                            .map(SessionEvent::turnId)
                            .distinct()
                            .count());
            assertTrue(requests.get(2).messages().toString().contains("second input"));
            assertEquals(
                    2, events.stream().filter(e -> e.type().equals("sdk.input.applied")).count());
            assertTrue(
                    inputs(host, id).stream()
                            .allMatch(
                                    i ->
                                            i.get("state").equals("FINISHED")
                                                    && !i.get("turnId").equals("")));
            SessionLifecycle.setArchived(sessions, id, true, new SecretRedactor());
            assertEquals(
                    409,
                    req(
                                    host,
                                    "/v1/tasks/" + id + "/turns",
                                    Map.of(
                                            "requestId",
                                            "archived",
                                            "text",
                                            "no",
                                            "kind",
                                            "message"))
                            .statusCode());
            SessionLifecycle.setArchived(sessions, id, false, new SecretRedactor());
            turn(host, id, "restored", "after restore", "message");
            settled(host, id, 4);
        }
    }

    @Test
    void busyQueueSupplementCancelAndConcurrentDuplicateAreSerialized() throws Exception {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        try (var host = host()) {
            String id = create(host);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var a =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return turn(host, id, "same", "next", "message");
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                            });
            var b =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return turn(host, id, "same", "next", "message");
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                            });
            assertEquals(a.get().get("submission"), b.get().get("submission"));
            var cancelled =
                    Values.object(
                            turn(host, id, "cancelled", "do not apply", "message")
                                    .get("submission"));
            data(
                    req(
                            host,
                            "/v1/tasks/"
                                    + id
                                    + "/turns/"
                                    + cancelled.get("submissionId")
                                    + "/cancel",
                            Map.of()));
            turn(host, id, "supplement", "late boundary instruction", "supplement");
            assertEquals(1, requests.size());
            assertFalse(requests.getFirst().messages().toString().contains("late boundary"));
            release.countDown();
            settled(host, id, 3);
            assertEquals(3, requests.size());
            assertFalse(requests.toString().contains("do not apply"));
            assertTrue(requests.get(2).messages().toString().contains("late boundary instruction"));
            assertEquals("CANCELLED", inputs(host, id).get(1).get("state"));
        } finally {
            release.countDown();
        }
    }

    @Test
    void queueCapacityAndUnknownPreventUnsafeContinuation() throws Exception {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        uncertain = true;
        try (var host = host()) {
            String id = create(host);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 8; i++) turn(host, id, "queued-" + i, "queued", "message");
            assertEquals(
                    429,
                    req(
                                    host,
                                    "/v1/tasks/" + id + "/turns",
                                    Map.of(
                                            "requestId",
                                            "overflow",
                                            "text",
                                            "no",
                                            "kind",
                                            "message"))
                            .statusCode());
            release.countDown();
            settled(host, id, 1);
            assertEquals("UNKNOWN", data(req(host, "/v1/tasks/" + id, null)).get("phase"));
            assertTrue(inputs(host, id).stream().allMatch(i -> i.get("state").equals("PAUSED")));
            var first = inputs(host, id).getFirst();
            assertEquals(
                    409,
                    req(
                                    host,
                                    "/v1/tasks/"
                                            + id
                                            + "/turns/"
                                            + first.get("submissionId")
                                            + "/start",
                                    Map.of())
                            .statusCode());
            assertEquals(1, opens.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void restartLeavesAcceptedQueuePausedAndRequiresExplicitStart() throws Exception {
        String id;
        try (var host = host()) {
            id = create(host);
            settled(host, id, 1);
            // Persist admission without start to model a crash after durable acceptance.
        }
        Path file;
        try (var files = Files.walk(root.resolve("catalog"))) {
            file =
                    files.filter(
                                    p ->
                                            p.getFileName().toString().startsWith("h-")
                                                    && p.toString().endsWith(".jsonl"))
                            .findFirst()
                            .orElseThrow();
        }
        String record = file.getFileName().toString().replace(".jsonl", "");
        var repo = new JsonlSessionRepository(file.getParent());
        String hash =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest("paused".getBytes()));
        String goalHash =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest("next".getBytes()));
        try (var writer = repo.openWriter(record, WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            record,
                            "web.turn.accepted",
                            Map.of(
                                    "taskId",
                                    id,
                                    "requestHash",
                                    hash,
                                    "goalHash",
                                    goalHash,
                                    "kind",
                                    "message",
                                    "content",
                                    "next")));
            writer.flush();
        }
        int before = opens.get();
        try (var host = host()) {
            assertEquals("PAUSED", inputs(host, id).getFirst().get("state"));
            assertEquals(before, opens.get());
            assertEquals(
                    "PAUSED",
                    Values.object(
                                    data(req(
                                                    host,
                                                    "/v1/tasks/" + id + "/turns?request=paused",
                                                    null))
                                            .get("submission"))
                            .get("state"));
            assertEquals(before, opens.get());
            data(req(host, "/v1/tasks/" + id + "/turns/" + hash + "/start", Map.of()));
            settled(host, id, 2);
            assertEquals(before + 1, opens.get());
        }
    }

    @Test
    void hostExitNeverExecutesAcceptedQueueOnRestart() throws Exception {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        String id;
        try (var host = host()) {
            id = create(host);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            turn(host, id, "queued-on-exit", "must not run on restart", "supplement");
        }
        release.countDown();
        int before = opens.get();
        try (var host = host()) {
            assertEquals(before, opens.get());
            assertEquals("UNKNOWN", data(req(host, "/v1/tasks/" + id, null)).get("phase"));
            assertEquals("PAUSED", inputs(host, id).getFirst().get("state"));
            assertEquals(
                    409,
                    req(
                                    host,
                                    "/v1/tasks/"
                                            + id
                                            + "/turns/"
                                            + inputs(host, id).getFirst().get("submissionId")
                                            + "/start",
                                    Map.of())
                            .statusCode());
            assertEquals(before, opens.get());
        }
    }

    @Test
    void failedStartCheckpointRecoversOriginalTurnAsUnknownWithoutReplay() throws Exception {
        var remaining = new AtomicInteger();
        catalog =
                new JsonlSessionRepository(
                        root.resolve("catalog"),
                        JsonlSessionRepository.TailPolicy.REJECT,
                        channel -> {
                            if (remaining.get() > 0 && remaining.decrementAndGet() == 0)
                                throw new java.io.IOException("injected start checkpoint");
                            channel.force(true);
                        },
                        code -> {});
        String id;
        try (var host = host()) {
            id = create(host);
            settled(host, id, 1);
            remaining.set(3);
            assertEquals(
                    503,
                    req(
                                    host,
                                    "/v1/tasks/" + id + "/turns",
                                    Map.of(
                                            "requestId",
                                            "failed-start",
                                            "text",
                                            "synthetic-secret next",
                                            "kind",
                                            "message"))
                            .statusCode());
            assertEquals(1, opens.get());
        }
        remaining.set(0);
        try (var host = host()) {
            assertEquals("UNKNOWN", data(req(host, "/v1/tasks/" + id, null)).get("phase"));
            assertEquals("UNKNOWN", inputs(host, id).getFirst().get("state"));
            assertEquals(1, opens.get());
        }
        HarnessWebServer.verifyTask(
                catalog,
                "turns",
                sessions,
                id,
                "workspace",
                "fixture failed before second SDK construction; no executor running; original resources inspected",
                new SecretRedactor());
        try (var host = host()) {
            assertEquals("VERIFIED", inputs(host, id).getFirst().get("state"));
            assertEquals(1, opens.get());
            turn(host, id, "after-verification", "operator explicitly continues", "message");
            settled(host, id, 2);
        }
        try (var files = Files.walk(root.resolve("catalog"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".jsonl")).toList())
                assertFalse(Files.readString(path).contains("synthetic-secret"));
        }
    }
}
