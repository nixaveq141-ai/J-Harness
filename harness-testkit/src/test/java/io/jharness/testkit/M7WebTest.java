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
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class M7WebTest {
    private static final String TOKEN = "web_test_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;
    private JsonlSessionRepository sessions, catalog;
    private final AtomicInteger opened = new AtomicInteger();
    private final List<FakeLlm> models = new CopyOnWriteArrayList<>();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeEach
    void setup() throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        sessions = new JsonlSessionRepository(root.resolve("sessions"));
        catalog = new JsonlSessionRepository(root.resolve("catalog"));
    }

    private static ChatResponse response(ToolInvocation... calls) {
        return new ChatResponse("fixture", List.of(calls), Map.of());
    }

    private static ToolInvocation write() {
        return new ToolInvocation(
                "write",
                "write_file",
                Map.of("path", "marker.txt", "content", "approved"),
                SideEffectLevel.READ_ONLY);
    }

    private HarnessSdk sdk(
            String id, ApprovalService approvals, LlmClient model, DataSanitizer sanitizer)
            throws Exception {
        return new HarnessSdk(
                id,
                "workspace",
                sessions,
                new LocalExecutionEnvironment("local", root.resolve("workspace"), List.of()),
                model,
                new LocalArtifactStore(root.resolve("artifacts"), sanitizer),
                sanitizer,
                new DefaultPolicyEngine(),
                approvals,
                new Budget(5, Duration.ofSeconds(10)),
                (reply, projection) ->
                        new CompletionEvaluator.CompletionDecision(
                                projection.toolHistory().stream()
                                        .anyMatch(
                                                e ->
                                                        e.type().equals("tool.completed")
                                                                && "SUCCESS"
                                                                        .equals(
                                                                                e.payload()
                                                                                        .get(
                                                                                                "status"))),
                                "tool evidence required"),
                ApiCostGuard.unavailable(),
                List.of());
    }

    private HarnessWebServer.Profile profile(Supplier<List<ChatResponse>> responses) {
        return new HarnessWebServer.Profile(
                "workspace",
                sessions,
                (id, approvals, sanitizer) -> {
                    opened.incrementAndGet();
                    var model = new FakeLlm(responses.get(), 32768);
                    models.add(model);
                    return sdk(id, approvals, model, sanitizer);
                });
    }

    private HarnessWebServer server(HarnessWebServer.Profile profile, Duration timeout)
            throws Exception {
        return new HarnessWebServer(
                0,
                TOKEN,
                Map.of("local", profile),
                catalog,
                "web-catalog",
                new SecretRedactor(),
                1,
                8,
                timeout);
    }

    private HttpResponse<String> request(
            HarnessWebServer server,
            String method,
            String path,
            String body,
            String token,
            String... headers)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(server.address().resolve(path))
                        .timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        for (int i = 0; i < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> request(
            HarnessWebServer server, String method, String path, Object body) throws Exception {
        return request(
                server, method, path, body == null ? null : JSON.writeValueAsString(body), TOKEN);
    }

    private static Map<String, Object> object(HttpResponse<String> response) throws Exception {
        return JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private String submit(HarnessWebServer server, String requestId) throws Exception {
        var response =
                request(
                        server,
                        "POST",
                        "/v1/tasks",
                        Map.of("profile", "local", "goal", "write marker", "requestId", requestId));
        assertEquals(202, response.statusCode(), response.body());
        return (String) object(response).get("taskId");
    }

    private Map<String, Object> await(
            HarnessWebServer server, String path, Predicate<Map<String, Object>> check)
            throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        Map<String, Object> value;
        do {
            var result = request(server, "GET", path, null);
            assertEquals(200, result.statusCode(), result.body());
            value = object(result);
            if (check.test(value)) return value;
            Thread.sleep(15);
        } while (System.nanoTime() < end);
        fail("Timed out waiting for " + path + ": " + value);
        return value;
    }

    private Map<String, Object> approval(HarnessWebServer server, String id) throws Exception {
        var value =
                await(
                        server,
                        "/v1/tasks/" + id + "/approvals",
                        v -> !((List<?>) v.get("approvals")).isEmpty());
        return Values.object(((List<?>) value.get("approvals")).getFirst());
    }

    private Map<String, Object> finished(HarnessWebServer server, String id) throws Exception {
        return await(
                server,
                "/v1/tasks/" + id,
                v -> Set.of("FINISHED", "UNKNOWN").contains(v.get("phase")));
    }

    @Test
    void authenticationOriginAndBoundedStrictBodiesCannotConstructExecution() throws Exception {
        try (var server = server(profile(() -> List.of(response())), Duration.ofSeconds(2))) {
            assertEquals(401, request(server, "GET", "/v1/tasks", null, null).statusCode());
            assertEquals(401, request(server, "GET", "/v1/tasks", null, "wrong").statusCode());
            assertEquals(
                    403,
                    request(
                                    server,
                                    "GET",
                                    "/v1/tasks",
                                    null,
                                    TOKEN,
                                    "Origin",
                                    "http://evil.invalid")
                            .statusCode());
            assertEquals(
                    400,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    "{\"profile\":\"local\",\"profile\":\"local\"}",
                                    TOKEN)
                            .statusCode());
            assertEquals(
                    400,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "local",
                                            "goal",
                                            "x",
                                            "requestId",
                                            "x",
                                            "workspace",
                                            "C:/"))
                            .statusCode());
            assertEquals(
                    413,
                    request(server, "POST", "/v1/tasks", " ".repeat(65537), TOKEN).statusCode());
            assertEquals(
                    400,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of("profile", "absent", "goal", "x", "requestId", "x"))
                            .statusCode());
            assertEquals(404, request(server, "GET", "/v1/tasks/unknown", null).statusCode());
            assertEquals(0, opened.get());
            assertThrows(
                    HarnessException.class,
                    () -> catalog.openWriter("web-catalog", WriteOwnership.EXCLUSIVE));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new HarnessWebServer(
                                0,
                                "weak",
                                Map.of("local", profile(() -> List.of())),
                                catalog,
                                "other",
                                new SecretRedactor(),
                                1,
                                1,
                                Duration.ofSeconds(1)));
    }

    @Test
    void approvedExecutionIsDurableReconstructableAndIdempotentAcrossRestart() throws Exception {
        var profile = profile(() -> List.of(response(write()), response()));
        String id;
        try (var server = server(profile, Duration.ofSeconds(4))) {
            id = submit(server, "once");
            var pending = approval(server, id);
            assertFalse(Files.exists(root.resolve("workspace/marker.txt")));
            assertEquals(id, submit(server, "once"));
            String target = "/v1/tasks/" + id + "/approvals/" + pending.get("approvalId");
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    target,
                                    Map.of(
                                            "approved",
                                            true,
                                            "reason",
                                            "checked",
                                            "callId",
                                            "other"))
                            .statusCode());
            assertEquals(
                    200,
                    request(
                                    server,
                                    "POST",
                                    target,
                                    Map.of(
                                            "approved",
                                            true,
                                            "reason",
                                            "checked exact content",
                                            "callId",
                                            "write"))
                            .statusCode());
            assertEquals("COMPLETED", finished(server, id).get("status"));
            assertEquals("approved", Files.readString(root.resolve("workspace/marker.txt")));
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    target,
                                    Map.of(
                                            "approved",
                                            true,
                                            "reason",
                                            "duplicate",
                                            "callId",
                                            "write"))
                            .statusCode());
            var page = object(request(server, "GET", "/v1/tasks/" + id + "/events?limit=2", null));
            assertEquals(2, ((List<?>) page.get("events")).size());
            var second =
                    object(
                            request(
                                    server,
                                    "GET",
                                    "/v1/tasks/"
                                            + id
                                            + "/events?after="
                                            + page.get("next")
                                            + "&limit=2",
                                    null));
            assertNotEquals(page.get("next"), second.get("next"));
            assertEquals(
                    400,
                    request(server, "GET", "/v1/tasks/" + id + "/events?after=unknown", null)
                            .statusCode());
            assertEquals(
                    400,
                    request(server, "GET", "/v1/tasks/" + id + "/events?limit=101", null)
                            .statusCode());
            assertEquals(
                    404, request(server, "GET", "/v1/tasks/" + id + "/diff", null).statusCode());
        }
        var events = sessions.openReader(id).events();
        assertTrue(SessionRecovery.inspect(events).ready());
        int count = 0;
        for (int i = 0; i < events.size(); i++)
            if (events.get(i).type().equals("model.requested"))
                assertEquals(
                        models.getFirst().requests().get(count++),
                        new EventRequestJournal()
                                .reconstruct(events.subList(0, i + 1), events.get(i).eventId()));
        assertEquals(2, count);
        try (var server = server(profile, Duration.ofSeconds(4))) {
            assertEquals(id, submit(server, "once"));
            assertEquals(
                    true, object(request(server, "GET", "/v1/tasks/" + id, null)).get("recovered"));
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "local",
                                            "goal",
                                            "different",
                                            "requestId",
                                            "once"))
                            .statusCode());
            assertEquals(1, opened.get());
        }
    }

    @Test
    void denialAndApprovalTimeoutNeverEnterWriteBody() throws Exception {
        try (var server =
                server(
                        profile(
                                () ->
                                        List.of(
                                                response(write()),
                                                response(),
                                                response(),
                                                response(),
                                                response())),
                        Duration.ofMillis(450))) {
            String id = submit(server, "deny");
            var pending = approval(server, id);
            assertEquals(
                    200,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks/" + id + "/approvals/" + pending.get("approvalId"),
                                    Map.of(
                                            "approved",
                                            false,
                                            "reason",
                                            "denied",
                                            "callId",
                                            "write"))
                            .statusCode());
            finished(server, id);
            assertFalse(Files.exists(root.resolve("workspace/marker.txt")));
            String timeout = submit(server, "timeout");
            finished(server, timeout);
            for (String task : List.of(id, timeout)) {
                var events = sessions.openReader(task).events();
                assertFalse(events.stream().anyMatch(e -> e.type().equals("tool.intent")));
                assertTrue(
                        events.stream()
                                .anyMatch(
                                        e ->
                                                e.type().equals("tool.completed")
                                                        && "DENIED"
                                                                .equals(
                                                                        e.payload()
                                                                                .get("status"))));
            }
        }
    }

    @Test
    void cancellationRevokesPendingApprovalAndDoesNotWrite() throws Exception {
        try (var server =
                server(
                        profile(() -> List.of(response(write()), response())),
                        Duration.ofSeconds(4))) {
            String id = submit(server, "cancel");
            var pending = approval(server, id);
            assertEquals(
                    200,
                    request(server, "POST", "/v1/tasks/" + id + "/cancel", Map.of()).statusCode());
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks/" + id + "/approvals/" + pending.get("approvalId"),
                                    Map.of("approved", true, "reason", "late", "callId", "write"))
                            .statusCode());
            finished(server, id);
            assertFalse(Files.exists(root.resolve("workspace/marker.txt")));
        }
    }

    @Test
    void sharedWorkspaceIsExclusiveAndSettledHistoryOutlivesHotSet() throws Exception {
        var profile = profile(() -> List.of(response(write()), response()));
        try (var server =
                new HarnessWebServer(
                        0,
                        TOKEN,
                        Map.of("local", profile, "alias", profile),
                        catalog,
                        "web-catalog",
                        new SecretRedactor(),
                        2,
                        2,
                        Duration.ofSeconds(3))) {
            String id = submit(server, "one");
            approval(server, id);
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of("profile", "alias", "goal", "x", "requestId", "alias"))
                            .statusCode());
            request(server, "POST", "/v1/tasks/" + id + "/cancel", Map.of());
            finished(server, id);
            String second = submit(server, "two");
            approval(server, second);
            request(server, "POST", "/v1/tasks/" + second + "/cancel", Map.of());
            finished(server, second);
            var third =
                    request(
                            server,
                            "POST",
                            "/v1/tasks",
                            Map.of("profile", "local", "goal", "x", "requestId", "three"));
            assertEquals(202, third.statusCode());
            String thirdId = (String) object(third).get("taskId");
            approval(server, thirdId);
            request(server, "POST", "/v1/tasks/" + thirdId + "/cancel", Map.of());
            finished(server, thirdId);
            assertEquals(3, opened.get());
            assertTrue(
                    ((Number) object(request(server, "GET", "/v1/tasks", null)).get("hotCount"))
                                    .intValue()
                            <= 2);
        }
    }

    @Test
    void catalogCheckpointFailureCannotConstructSdkOrRetryAdmission() throws Exception {
        SessionRepository failing =
                new SessionRepository() {
                    public SessionWriter openWriter(String id, WriteOwnership ownership) {
                        var writer = catalog.openWriter(id, ownership);
                        return new SessionWriter() {
                            public boolean durable() {
                                return true;
                            }

                            public String append(SessionEvent e) {
                                return writer.append(e);
                            }

                            public void flush() {
                                throw new HarnessException("INJECTED", "secret detail");
                            }

                            public void close() {
                                writer.close();
                            }
                        };
                    }

                    public SessionReader openReader(String id) {
                        return catalog.openReader(id);
                    }

                    public List<String> list() {
                        return catalog.list();
                    }

                    public String fork(String id, String event) {
                        throw new UnsupportedOperationException();
                    }
                };
        try (var server =
                new HarnessWebServer(
                        0,
                        TOKEN,
                        Map.of("local", profile(() -> List.of(response()))),
                        failing,
                        "web-catalog",
                        new SecretRedactor(),
                        1,
                        8,
                        Duration.ofSeconds(1))) {
            var result =
                    request(
                            server,
                            "POST",
                            "/v1/tasks",
                            Map.of("profile", "local", "goal", "x", "requestId", "one"));
            assertEquals(503, result.statusCode());
            assertFalse(result.body().contains("secret detail"));
            assertEquals(
                    503,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of("profile", "local", "goal", "x", "requestId", "two"))
                            .statusCode());
            assertEquals(0, opened.get());
        }
    }

    @Test
    void shutdownDuringUncooperativeFactoryRestoresUnknownWithoutReplaying() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var model = new FakeLlm(List.of(response()));
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (id, approvals, sanitizer) -> {
                            opened.incrementAndGet();
                            entered.countDown();
                            while (release.getCount() > 0) {
                                try {
                                    release.await();
                                } catch (InterruptedException ignored) {
                                    /* injected provider */
                                }
                            }
                            return sdk(id, approvals, model, sanitizer);
                        });
        var server = server(profile, Duration.ofSeconds(1));
        String id;
        try {
            id = submit(server, "interrupted");
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long start = System.nanoTime();
            server.close();
            assertTrue(System.nanoTime() - start < Duration.ofSeconds(4).toNanos());
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> server(profile, Duration.ofSeconds(1)))
                            .code());
            release.countDown();
            long cleanupDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            boolean ownerReleased = false;
            while (System.nanoTime() < cleanupDeadline) {
                try (var writer = catalog.openWriter("web-catalog", WriteOwnership.EXCLUSIVE);
                        var workspaceOwner =
                                sessions.openWriter(
                                        "web-workspace-owner", WriteOwnership.EXCLUSIVE)) {
                    writer.flush();
                    workspaceOwner.flush();
                    ownerReleased = true;
                    break;
                } catch (HarnessException busy) {
                    assertEquals("WRITE_CONFLICT", busy.code());
                    Thread.sleep(10);
                }
            }
            assertTrue(ownerReleased);
            try (var restored = server(profile, Duration.ofSeconds(1))) {
                assertEquals(id, submit(restored, "interrupted"));
                assertEquals(
                        "UNKNOWN",
                        object(request(restored, "GET", "/v1/tasks/" + id, null)).get("phase"));
                assertEquals(
                        409,
                        request(
                                        restored,
                                        "POST",
                                        "/v1/tasks",
                                        Map.of(
                                                "profile",
                                                "local",
                                                "goal",
                                                "x",
                                                "requestId",
                                                "fresh"))
                                .statusCode());
                assertEquals(1, opened.get());
            }
        } finally {
            release.countDown();
            server.close();
        }
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!sessions.list().contains(id) && System.nanoTime() < end) Thread.sleep(10);
        while (System.nanoTime() < end) {
            try (var writer = sessions.openWriter(id, WriteOwnership.EXCLUSIVE)) {
                writer.flush();
                break;
            } catch (HarnessException ex) {
                Thread.sleep(10);
            }
        }
        assertTrue(model.requests().isEmpty());
    }

    @Test
    void unknownModelRequestBlocksWorkspaceAndErrorDetailsAreNotExposed() throws Exception {
        var profile = profile(List::of);
        String id;
        try (var server = server(profile, Duration.ofSeconds(1))) {
            id = submit(server, "uncertain");
            assertEquals("UNKNOWN", finished(server, id).get("phase"));
            assertFalse(SessionRecovery.inspect(sessions.openReader(id).events()).ready());
            assertEquals(
                    409,
                    request(
                                    server,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of("profile", "local", "goal", "x", "requestId", "fresh"))
                            .statusCode());
        }
        try (var server = server(profile, Duration.ofSeconds(1))) {
            assertEquals(id, submit(server, "uncertain"));
            assertEquals(1, opened.get());
        }
    }

    @Test
    void eventPagesRedactBearerAndOmitLargePayloadsWithoutLosingCursor() throws Exception {
        var profile =
                profile(
                        () ->
                                List.of(
                                        new ChatResponse(
                                                TOKEN + "x".repeat(70000), List.of(), Map.of())));
        try (var server = server(profile, Duration.ofSeconds(1))) {
            var result =
                    request(
                            server,
                            "POST",
                            "/v1/tasks",
                            Map.of(
                                    "profile",
                                    "local",
                                    "goal",
                                    "inspect " + TOKEN,
                                    "requestId",
                                    "large"));
            String id = (String) object(result).get("taskId");
            finished(server, id);
            var page = request(server, "GET", "/v1/tasks/" + id + "/events?limit=100", null);
            assertEquals(200, page.statusCode());
            assertFalse(page.body().contains(TOKEN));
            assertTrue(page.body().contains("EVENT_PAYLOAD_LIMIT"));
            assertTrue(page.body().contains("[REDACTED]"));
            assertFalse(
                    JSON.writeValueAsString(
                                    sessions.openReader(id).events().stream()
                                            .map(SessionEvent::payload)
                                            .toList())
                            .contains(TOKEN));
        }
    }

    @Test
    void diffEndpointReturnsActualRecordedGitResultWithSourceEvent() throws Exception {
        Path workspace = root.resolve("workspace");
        Files.writeString(workspace.resolve("input.txt"), "before\n");
        for (var args :
                List.of(List.of("git", "init", "--quiet"), List.of("git", "add", "input.txt"))) {
            var process =
                    new ProcessBuilder(args)
                            .directory(workspace.toFile())
                            .redirectErrorStream(true)
                            .start();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(
                    0,
                    process.exitValue(),
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        Files.writeString(workspace.resolve("input.txt"), "after\n");
        var profile =
                profile(
                        () ->
                                List.of(
                                        response(
                                                new ToolInvocation(
                                                        "diff",
                                                        "git_diff",
                                                        Map.of("scope", "working"),
                                                        SideEffectLevel.READ_ONLY)),
                                        response()));
        try (var server = server(profile, Duration.ofSeconds(1))) {
            String id = submit(server, "diff");
            finished(server, id);
            var result = request(server, "GET", "/v1/tasks/" + id + "/diff", null);
            assertEquals(200, result.statusCode(), result.body());
            assertTrue(result.body().contains("+after"), result.body());
            assertEquals("recorded-tool-result", object(result).get("source"));
            assertTrue(
                    sessions.openReader(id).events().stream()
                            .anyMatch(
                                    e ->
                                            e.eventId()
                                                    .equals(
                                                            Values.object(
                                                                            uncheckedObject(result)
                                                                                    .get("event"))
                                                                    .get("eventId"))));
        }
    }

    @Test
    void boundedSseUsesDurableCursorsAndKeepsApprovalRequestsAvailable() throws Exception {
        try (var server =
                server(
                        profile(() -> List.of(response(write()), response())),
                        Duration.ofSeconds(4))) {
            String id = submit(server, "stream");
            var pending = approval(server, id);
            URI uri = server.address().resolve("/v1/tasks/" + id + "/events?limit=100");
            var streamRequest =
                    HttpRequest.newBuilder(uri)
                            .header("Authorization", "Bearer " + TOKEN)
                            .header("Accept", "text/event-stream")
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build();
            var first = client.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream());
            var second = client.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream());
            try (var firstBody = first.body();
                    var secondBody = second.body()) {
                assertEquals(200, first.statusCode());
                assertEquals(200, second.statusCode());
                assertEquals(
                        429,
                        request(
                                        server,
                                        "GET",
                                        "/v1/tasks/" + id + "/events",
                                        null,
                                        TOKEN,
                                        "Accept",
                                        "text/event-stream")
                                .statusCode());
                assertEquals(
                        200,
                        request(
                                        server,
                                        "POST",
                                        "/v1/tasks/"
                                                + id
                                                + "/approvals/"
                                                + pending.get("approvalId"),
                                        Map.of(
                                                "approved",
                                                true,
                                                "reason",
                                                "checked while streaming",
                                                "callId",
                                                "write"))
                                .statusCode());
                finished(server, id);
                String frames =
                        new String(
                                firstBody.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(frames.contains("event: session\ndata:"), frames);
                assertTrue(frames.contains("tool.completed"), frames);
                assertTrue(secondBody.readAllBytes().length > 0);
            }
            var events = sessions.openReader(id).events();
            var resumed =
                    request(
                            server,
                            "GET",
                            "/v1/tasks/" + id + "/events?limit=1",
                            null,
                            TOKEN,
                            "Accept",
                            "text/event-stream",
                            "Last-Event-ID",
                            events.getFirst().eventId());
            assertEquals(200, resumed.statusCode());
            assertTrue(resumed.body().startsWith("id: " + events.get(1).eventId()));
            assertEquals(
                    400,
                    request(
                                    server,
                                    "GET",
                                    "/v1/tasks/"
                                            + id
                                            + "/events?after="
                                            + events.getFirst().eventId(),
                                    null,
                                    TOKEN,
                                    "Accept",
                                    "text/event-stream",
                                    "Last-Event-ID",
                                    events.getFirst().eventId())
                            .statusCode());
        }
    }

    @Test
    void hostileHostHeaderIsRejectedEvenWithValidBearer() throws Exception {
        try (var server = server(profile(List::of), Duration.ofSeconds(1));
                var socket = new java.net.Socket("127.0.0.1", server.address().getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream()
                    .write(
                            ("GET /v1/tasks HTTP/1.1\r\nHost: evil.invalid\r\nAuthorization: Bearer "
                                            + TOKEN
                                            + "\r\nConnection: close\r\n\r\n")
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String reply =
                    new String(
                            socket.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(reply.startsWith("HTTP/1.1 403"), reply);
            assertFalse(reply.contains(TOKEN));
            assertEquals(0, opened.get());
        }
    }

    @Test
    void offlineWebVerificationRequiresOwnershipAndUnderlyingAuditWithoutReplay() throws Exception {
        var profile = profile(List::of);
        String id;
        try (var server = server(profile, Duration.ofSeconds(1))) {
            id = submit(server, "audit");
            assertEquals("UNKNOWN", finished(server, id).get("phase"));
            assertThrows(
                    HarnessException.class,
                    () ->
                            HarnessWebServer.verifyTask(
                                    catalog,
                                    "web-catalog",
                                    sessions,
                                    id,
                                    "workspace",
                                    "checked",
                                    new SecretRedactor()));
        }
        assertThrows(
                HarnessException.class,
                () ->
                        HarnessWebServer.verifyTask(
                                catalog,
                                "web-catalog",
                                sessions,
                                id,
                                "workspace",
                                "checked",
                                new SecretRedactor()));
        var requested =
                sessions.openReader(id).events().stream()
                        .filter(e -> e.type().equals("model.requested"))
                        .findFirst()
                        .orElseThrow();
        try (var writer = sessions.openWriter(id, WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            id,
                            "model.verified",
                            Map.of(
                                    "stepId",
                                    requested.stepId(),
                                    "actor",
                                    "operator",
                                    "reason",
                                    "fixture client closed; no replay")));
            writer.flush();
            assertThrows(
                    HarnessException.class,
                    () ->
                            HarnessWebServer.verifyTask(
                                    catalog,
                                    "web-catalog",
                                    sessions,
                                    id,
                                    "workspace",
                                    "checked",
                                    new SecretRedactor()));
        }
        HarnessWebServer.verifyTask(
                catalog,
                "web-catalog",
                sessions,
                id,
                "workspace",
                "checked stopped host and clients",
                new SecretRedactor());
        assertThrows(
                HarnessException.class,
                () ->
                        HarnessWebServer.verifyTask(
                                catalog,
                                "web-catalog",
                                sessions,
                                id,
                                "workspace",
                                "duplicate",
                                new SecretRedactor()));
        try (var server = server(profile, Duration.ofSeconds(1))) {
            assertEquals(id, submit(server, "audit"));
            assertEquals(
                    "VERIFIED",
                    object(request(server, "GET", "/v1/tasks/" + id, null)).get("phase"));
            assertNotEquals(
                    "COMPLETED",
                    object(request(server, "GET", "/v1/tasks/" + id, null)).get("status"));
            assertEquals(1, opened.get());
        }
    }

    private static Map<String, Object> uncheckedObject(HttpResponse<String> response) {
        try {
            return object(response);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
