package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Real backend commits, then an independent HTTP gateway corrupts only the client response. */
class R4RealResponseFaultTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static HttpRequest.Builder request(URI base, String path) {
        return HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + M10BrowserHost.TOKEN);
    }

    static Map<String, Object> call(URI base, String path, Object body) throws Exception {
        var b = request(base, path);
        if (body == null) b.GET();
        else
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
        var r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertTrue(r.statusCode() < 300, "HTTP " + r.statusCode() + " " + path);
        return JSON.readValue(r.body(), new TypeReference<>() {});
    }

    static Map<String, Object> await(
            Callable<Map<String, Object>> read,
            java.util.function.Predicate<Map<String, Object>> done)
            throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < until) {
            var row = read.call();
            if (done.test(row)) return row;
            Thread.sleep(10);
        }
        throw new AssertionError("Fixture state not reached");
    }

    static final class LostResponse implements AutoCloseable {
        final HttpServer proxy;
        URI backend;
        final String fault;
        final AtomicInteger forwarded = new AtomicInteger();
        final AtomicReference<Throwable> backendFailure = new AtomicReference<>();

        LostResponse(URI backend, String fault) throws IOException {
            this.backend = backend;
            this.fault = fault;
            proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            proxy.createContext(
                    "/",
                    exchange -> {
                        try {
                            byte[] input = exchange.getRequestBody().readNBytes(65537);
                            if (input.length > 65536) throw new IOException("Fixture body bound");
                            forwarded.incrementAndGet();
                            var response =
                                    HTTP.send(
                                            request(
                                                            this.backend,
                                                            exchange.getRequestURI().toString())
                                                    .header("Content-Type", "application/json")
                                                    .POST(
                                                            HttpRequest.BodyPublishers.ofByteArray(
                                                                    input))
                                                    .build(),
                                            HttpResponse.BodyHandlers.ofByteArray());
                            if (response.statusCode() < 200 || response.statusCode() >= 300)
                                throw new AssertionError(
                                        "Backend rejected mutation: " + response.statusCode());
                            // The real handler has returned its successful response after mutation.
                            // No retry is made, and no backend response body reaches the caller.
                            if (fault.equals("headers")) return;
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, fault.equals("body") ? 100 : 1);
                            exchange.getResponseBody().write('{');
                        } catch (Throwable failure) {
                            backendFailure.set(failure);
                        } finally {
                            exchange.close();
                        }
                    });
            proxy.start();
        }

        void lose(String path, Object body) throws Exception {
            int before = forwarded.get();
            try {
                var uri = URI.create("http://127.0.0.1:" + proxy.getAddress().getPort());
                call(uri, path, body);
                fail("Corrupted response was accepted");
            } catch (IOException expected) {
                /* Missing headers, truncated body or malformed JSON. */
            }
            assertNull(backendFailure.get(), "Backend itself must succeed before transport fault");
            assertEquals(
                    before + 1, forwarded.get(), "Exactly one real backend POST per lost response");
        }

        public void close() {
            proxy.stop(0);
        }
    }

    static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            text.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void force(Path target, Object data) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(data);
        try (var channel =
                java.nio.channels.FileChannel.open(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    static final class Rig implements AutoCloseable {
        final Path root;
        final JsonlSessionRepository catalog;
        final Map<String, HarnessWebServer.Profile> profiles = new LinkedHashMap<>();
        final AtomicInteger opens = new AtomicInteger();
        final CountDownLatch holdEntered = new CountDownLatch(1);
        HarnessWebServer server;

        Rig(Path root) throws Exception {
            this.root = Files.createDirectories(root).toRealPath();
            catalog = new JsonlSessionRepository(this.root.resolve("catalog"));
            for (String name : List.of("plain", "write", "ask", "hold")) {
                Path work = Files.createDirectories(this.root.resolve(name));
                Files.writeString(work.resolve("input.txt"), "fixture\n");
                var sessions = new JsonlSessionRepository(this.root.resolve(name + "-sessions"));
                profiles.put(
                        name,
                        new HarnessWebServer.Profile(
                                name,
                                sessions,
                                (id, approval, sanitizer) -> {
                                    String actionId = "action-" + opens.incrementAndGet();
                                    var calls =
                                            name.equals("write")
                                                    ? List.of(
                                                            new ToolInvocation(
                                                                    actionId,
                                                                    "write_file",
                                                                    Map.of(
                                                                            "path",
                                                                            "result.txt",
                                                                            "content",
                                                                            "committed"),
                                                                    SideEffectLevel
                                                                            .WORKSPACE_WRITE))
                                                    : name.equals("ask")
                                                            ? List.of(
                                                                    new ToolInvocation(
                                                                            actionId,
                                                                            "ask_user",
                                                                            M1055QuestionsTest.spec(
                                                                                    "text", 60),
                                                                            SideEffectLevel
                                                                                    .SYSTEM_IMPACT))
                                                            : List.of(
                                                                    new ToolInvocation(
                                                                            actionId,
                                                                            "read_file",
                                                                            Map.of(
                                                                                    "path",
                                                                                    "input.txt"),
                                                                            SideEffectLevel
                                                                                    .READ_ONLY));
                                    var fake =
                                            new FakeLlm(
                                                    List.of(
                                                            new ChatResponse(
                                                                    "step", calls, Map.of()),
                                                            new ChatResponse(
                                                                    "done", List.of(), Map.of())),
                                                    200000);
                                    LlmClient model =
                                            name.equals("hold")
                                                    ? new LlmClient() {
                                                        public ModelCapabilities capabilities() {
                                                            return fake.capabilities();
                                                        }

                                                        public PreparedRequest prepare(
                                                                ChatRequest r) {
                                                            return fake.prepare(r);
                                                        }

                                                        public ChatResponse complete(
                                                                ChatRequest r,
                                                                CancellationToken c) {
                                                            holdEntered.countDown();
                                                            while (!c.isCancelled())
                                                                try {
                                                                    Thread.sleep(10);
                                                                } catch (InterruptedException e) {
                                                                    Thread.currentThread()
                                                                            .interrupt();
                                                                    throw new CancellationException();
                                                                }
                                                            c.throwIfCancelled();
                                                            throw new AssertionError();
                                                        }

                                                        public StreamHandle stream(
                                                                ChatRequest r,
                                                                CancellationToken c) {
                                                            throw new UnsupportedOperationException();
                                                        }
                                                    }
                                                    : fake;
                                    return new HarnessSdk(
                                            id,
                                            name,
                                            sessions,
                                            new LocalExecutionEnvironment("local", work, List.of()),
                                            model,
                                            new LocalArtifactStore(
                                                    this.root.resolve(name + "-artifacts"),
                                                    sanitizer),
                                            sanitizer,
                                            new DefaultPolicyEngine(),
                                            approval,
                                            new Budget(4, Duration.ofSeconds(60)),
                                            (a, b) ->
                                                    new CompletionEvaluator.CompletionDecision(
                                                            true, "fixture"),
                                            ApiCostGuard.unavailable(),
                                            List.of());
                                },
                                HarnessWebServer.ForkArtifacts.local(
                                        new LocalArtifactStore(
                                                this.root.resolve(name + "-artifacts"),
                                                new SecretRedactor()))));
            }
            start();
        }

        void start() throws Exception {
            server =
                    new HarnessWebServer(
                            0,
                            M10BrowserHost.TOKEN,
                            profiles,
                            catalog,
                            "faults",
                            new SecretRedactor(),
                            4,
                            32,
                            Duration.ofSeconds(30));
        }

        URI base() {
            return server.address();
        }

        String submit(String profile, String request) throws Exception {
            return (String)
                    call(
                                    base(),
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            profile,
                                            "goal",
                                            "fixture",
                                            "requestId",
                                            request))
                            .get("taskId");
        }

        Map<String, Object> task(String id) throws Exception {
            return call(base(), "/v1/tasks/" + id, null);
        }

        void finished(String id) throws Exception {
            await(() -> task(id), r -> r.get("phase").equals("FINISHED"));
        }

        List<SessionEvent> facts(String profile, String id) throws IOException {
            return new JsonlSessionRepository(root.resolve(profile + "-sessions"))
                    .openReader(id)
                    .events();
        }

        public void close() {
            server.close();
        }
    }

    @Test
    @Timeout(180)
    void realServerMutationsRemainReconciliableAcrossThreeResponseFaults() throws Exception {
        for (String fault : List.of("headers", "body", "json"))
            try (var rig = new Rig(root.resolve(fault));
                    var loss = new LostResponse(rig.base(), fault)) {
                var seen = new HashSet<String>();
                loss.lose(
                        "/v1/tasks",
                        Map.of("profile", "plain", "goal", "fixture", "requestId", "lost-create"));
                String id =
                        (String)
                                call(rig.base(), "/v1/submissions/lost-create", null).get("taskId");
                rig.finished(id);
                assertEquals(1, rig.opens.get());
                seen.add("submit");
                for (String kind : List.of("message", "supplement")) {
                    loss.lose(
                            "/v1/tasks/" + id + "/turns",
                            Map.of("requestId", kind, "text", kind, "kind", kind));
                    await(
                            () ->
                                    call(
                                            rig.base(),
                                            "/v1/tasks/" + id + "/turns?request=" + kind,
                                            null),
                            value ->
                                    Values.object(value.get("submission"))
                                            .get("state")
                                            .equals("FINISHED"));
                    rig.finished(id);
                    seen.add(kind.equals("message") ? "turn" : "supplement");
                }
                rig.server.close();
                Path record;
                try (var paths = Files.walk(rig.root.resolve("catalog"))) {
                    record =
                            paths.filter(
                                            path ->
                                                    path.getFileName()
                                                            .toString()
                                                            .equals(
                                                                    "h-"
                                                                            + hash("lost-create")
                                                                            + ".jsonl"))
                                    .findFirst()
                                    .orElseThrow();
                }
                try (var writer =
                        new JsonlSessionRepository(record.getParent())
                                .openWriter(
                                        record.getFileName().toString().replace(".jsonl", ""),
                                        WriteOwnership.EXCLUSIVE)) {
                    for (String pending : List.of("queued-cancel", "queued-start"))
                        writer.append(
                                SessionEvent.session(
                                        record.getFileName().toString().replace(".jsonl", ""),
                                        "web.turn.accepted",
                                        Map.of(
                                                "taskId",
                                                id,
                                                "requestHash",
                                                hash(pending),
                                                "goalHash",
                                                hash("queued"),
                                                "kind",
                                                "message",
                                                "content",
                                                "queued",
                                                "selection",
                                                Map.of())));
                }
                rig.start();
                loss.backend = rig.base();
                for (String action : List.of("cancel", "start")) {
                    String queued = "queued-" + action;
                    var q =
                            Values.object(
                                    call(
                                                    rig.base(),
                                                    "/v1/tasks/" + id + "/turns?request=" + queued,
                                                    null)
                                            .get("submission"));
                    assertEquals("PAUSED", q.get("state"));
                    loss.lose(
                            "/v1/tasks/" + id + "/turns/" + q.get("submissionId") + "/" + action,
                            Map.of());
                    await(
                            () ->
                                    call(
                                            rig.base(),
                                            "/v1/tasks/" + id + "/turns?request=" + queued,
                                            null),
                            value ->
                                    Values.object(value.get("submission"))
                                            .get("state")
                                            .equals(
                                                    action.equals("cancel")
                                                            ? "CANCELLED"
                                                            : "FINISHED"));
                    rig.finished(id);
                    seen.add(action + " turn");
                }
                int plainOpens = rig.opens.get();
                for (String action : List.of("rename", "pin", "archive")) {
                    var before = rig.task(id);
                    String name = action.equals("rename") ? "updated" : (String) before.get("name");
                    boolean pinned =
                            action.equals("pin") || Boolean.TRUE.equals(before.get("pinned"));
                    boolean archived = action.equals("archive");
                    loss.lose(
                            "/v1/tasks/" + id + "/metadata",
                            Map.of(
                                    "revision",
                                    before.get("revision"),
                                    "name",
                                    name,
                                    "pinned",
                                    pinned,
                                    "archived",
                                    archived));
                    var after = rig.task(id);
                    assertEquals(name, after.get("name"));
                    assertEquals(pinned, after.get("pinned"));
                    assertEquals(archived, after.get("archived"));
                    assertNotEquals(before.get("revision"), after.get("revision"));
                    seen.add(action);
                }
                // Unarchive is setup, outside the measured mutation matrix.
                var row = rig.task(id);
                call(
                        rig.base(),
                        "/v1/tasks/" + id + "/metadata",
                        Map.of(
                                "revision",
                                row.get("revision"),
                                "name",
                                row.get("name"),
                                "pinned",
                                true,
                                "archived",
                                false));
                var beforeIds = new HashSet<>();
                for (Object t : (List<?>) call(rig.base(), "/v1/tasks?view=all", null).get("tasks"))
                    beforeIds.add(Values.object(t).get("taskId"));
                String event = rig.facts("plain", id).getLast().eventId();
                loss.lose(
                        "/v1/tasks/" + id + "/fork",
                        Map.of("eventId", event, "requestId", "lost-fork"));
                var afterIds = new HashSet<>();
                for (Object t : (List<?>) call(rig.base(), "/v1/tasks?view=all", null).get("tasks"))
                    afterIds.add(Values.object(t).get("taskId"));
                afterIds.removeAll(beforeIds);
                assertEquals(1, afterIds.size());
                assertEquals(plainOpens, rig.opens.get());
                seen.add("fork");
                String held = rig.submit("hold", "held");
                await(() -> rig.task(held), r -> r.get("phase").equals("RUNNING"));
                assertTrue(
                        rig.holdEntered.await(10, TimeUnit.SECONDS),
                        "Cancellation fixture must reach actual model body");
                loss.lose("/v1/tasks/" + held + "/cancel", Map.of());
                await(() -> rig.task(held), state -> state.get("phase").equals("UNKNOWN"));
                assertEquals(true, rig.task(held).get("cancelRequested"));
                seen.add("cancel task");
                for (boolean approved : List.of(true, false)) {
                    String write = rig.submit("write", "write-" + approved);
                    var waiting =
                            await(
                                    () ->
                                            call(
                                                    rig.base(),
                                                    "/v1/tasks/" + write + "/approvals",
                                                    null),
                                    r -> !((List<?>) r.get("approvals")).isEmpty());
                    var approval = Values.object(((List<?>) waiting.get("approvals")).getFirst());
                    loss.lose(
                            "/v1/tasks/" + write + "/approvals/" + approval.get("approvalId"),
                            Map.of(
                                    "approved",
                                    approved,
                                    "reason",
                                    "fixture",
                                    "callId",
                                    approval.get("callId")));
                    rig.finished(write);
                    assertEquals(
                            1,
                            rig.facts("write", write).stream()
                                    .filter(e -> e.type().equals("tool.completed"))
                                    .count());
                    seen.add(approved ? "approve" : "deny");
                }
                for (boolean skip : List.of(false, true)) {
                    String asked = rig.submit("ask", "ask-" + skip);
                    var pending =
                            await(
                                    () ->
                                            call(
                                                    rig.base(),
                                                    "/v1/tasks/" + asked + "/questions",
                                                    null),
                                    r -> Boolean.TRUE.equals(r.get("hasWaiting")));
                    var q = Values.object(((List<?>) pending.get("questions")).getLast());
                    var input = new LinkedHashMap<String, Object>();
                    for (String k : List.of("questionId", "sessionId", "turnId", "callId"))
                        input.put(k, q.get(k));
                    input.put("selected", List.of());
                    input.put("text", "draft");
                    input.put("revision", 0);
                    if (!skip) {
                        loss.lose(
                                "/v1/tasks/"
                                        + asked
                                        + "/questions/"
                                        + q.get("questionId")
                                        + "/draft",
                                input);
                        var current =
                                Values.object(
                                        ((List<?>)
                                                        call(
                                                                        rig.base(),
                                                                        "/v1/tasks/"
                                                                                + asked
                                                                                + "/questions",
                                                                        null)
                                                                .get("questions"))
                                                .getLast());
                        assertTrue(current.toString().contains("draft"));
                        seen.add("draft");
                    }
                    input.remove("revision");
                    input.put("requestId", "answer-" + skip);
                    input.put("skip", skip);
                    input.put("text", skip ? "" : "answer");
                    loss.lose(
                            "/v1/tasks/" + asked + "/questions/" + q.get("questionId") + "/answer",
                            input);
                    rig.finished(asked);
                    assertEquals(
                            1,
                            rig.facts("ask", asked).stream()
                                    .filter(e -> e.type().equals("question.resolved"))
                                    .count());
                    seen.add(skip ? "skip" : "answer");
                }
                try (var clean = new Rig(root.resolve(fault + "-settings"));
                        var mutation = new LostResponse(clean.base(), fault)) {
                    var registered = new AtomicInteger();
                    var saves = new AtomicInteger();
                    Path registration = clean.root.resolve("registry.json"),
                            settings = clean.root.resolve("settings.json");
                    clean.server.workspaces(
                            new HarnessWebServer.Workspaces() {
                                public Map<String, Object> read() {
                                    return Map.of(
                                            "workspaces",
                                            registered.get() == 0
                                                    ? List.of()
                                                    : List.of(
                                                            Map.of(
                                                                    "id",
                                                                    "registered",
                                                                    "name",
                                                                    "registered",
                                                                    "path",
                                                                    clean.root
                                                                            .resolve("plain")
                                                                            .toString())));
                                }

                                public Map.Entry<String, HarnessWebServer.Profile> register(
                                        String path, String name) throws IOException {
                                    force(registration, Map.of("path", path, "name", name));
                                    registered.incrementAndGet();
                                    return Map.entry("registered", clean.profiles.get("plain"));
                                }
                            });
                    mutation.lose(
                            "/v1/workspaces",
                            Map.of(
                                    "path",
                                    clean.root.resolve("plain").toString(),
                                    "name",
                                    "registered"));
                    assertEquals(1, registered.get());
                    assertTrue(Files.readString(registration).contains("registered"));
                    assertEquals(
                            1,
                            ((List<?>) call(clean.base(), "/v1/workspaces", null).get("workspaces"))
                                    .size());
                    seen.add("register");
                    clean.server.settings(
                            new HarnessWebServer.Settings() {
                                public Map<String, Object> read() {
                                    return Map.of("revision", Integer.toString(saves.get()));
                                }

                                public Map<String, Object> save(Map<String, Object> value)
                                        throws IOException {
                                    force(settings, value);
                                    saves.incrementAndGet();
                                    return read();
                                }
                            });
                    mutation.lose(
                            "/v1/settings",
                            Map.of(
                                    "revision",
                                    "0",
                                    "models",
                                    List.of(),
                                    "selected",
                                    "",
                                    "extensions",
                                    Map.of(),
                                    "apiKeys",
                                    Map.of()));
                    assertEquals(1, saves.get());
                    assertTrue(Files.isRegularFile(settings));
                    assertEquals("1", call(clean.base(), "/v1/settings", null).get("revision"));
                    seen.add("settings");
                    assertEquals(2, mutation.forwarded.get());
                }
                assertEquals(
                        Set.of(
                                "submit",
                                "turn",
                                "supplement",
                                "start turn",
                                "cancel turn",
                                "register",
                                "settings",
                                "rename",
                                "pin",
                                "archive",
                                "fork",
                                "cancel task",
                                "approve",
                                "deny",
                                "draft",
                                "answer",
                                "skip"),
                        seen);
                assertEquals(15, loss.forwarded.get());
            }
    }
}
