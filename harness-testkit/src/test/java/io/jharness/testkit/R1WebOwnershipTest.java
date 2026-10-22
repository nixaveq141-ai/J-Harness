package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1WebOwnershipTest {
    @TempDir Path root;
    static final String TOKEN = "r1_fixture_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final HttpClient client = HttpClient.newHttpClient();

    HarnessWebServer host(LlmClient model) throws Exception {
        return host(model, new JsonlSessionRepository(root.resolve("sessions")));
    }

    HarnessWebServer host(LlmClient model, SessionRepository repo) throws Exception {
        return host(model, repo, new JsonlSessionRepository(root.resolve("catalog")));
    }

    HarnessWebServer host(LlmClient model, SessionRepository repo, SessionRepository catalog)
            throws Exception {
        return host(model, repo, catalog, Duration.ofSeconds(3));
    }

    HarnessWebServer host(
            LlmClient model,
            SessionRepository repo,
            SessionRepository catalog,
            Duration approvalTimeout)
            throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        return new HarnessWebServer(
                0,
                TOKEN,
                Map.of(
                        "local",
                        new HarnessWebServer.Profile(
                                "workspace",
                                repo,
                                (id, approval, sanitizer) ->
                                        new HarnessSdk(
                                                id,
                                                "fixture",
                                                repo,
                                                new LocalExecutionEnvironment(
                                                        "local",
                                                        root.resolve("workspace"),
                                                        List.of()),
                                                model,
                                                new LocalArtifactStore(
                                                        root.resolve("artifacts"), sanitizer),
                                                sanitizer,
                                                new DefaultPolicyEngine(),
                                                approval,
                                                new Budget(2, Duration.ofSeconds(20)),
                                                (a, b) ->
                                                        new CompletionEvaluator.CompletionDecision(
                                                                true, "fixture"),
                                                ApiCostGuard.unavailable(),
                                                List.of()))),
                catalog,
                "web-catalog",
                new SecretRedactor(),
                1,
                8,
                approvalTimeout);
    }

    @Test
    void slowHistoryReconciliationCannotBlockUnrelatedRequestsOrOwnServerMonitor()
            throws Exception {
        var physical = new JsonlSessionRepository(root.resolve("sessions"));
        var block = new AtomicBoolean();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SessionRepository repo =
                new SessionRepository() {
                    public SessionWriter openWriter(String id, WriteOwnership ownership) {
                        return physical.openWriter(id, ownership);
                    }

                    public SessionReader openReader(String id) {
                        return () -> {
                            if (block.get()) {
                                entered.countDown();
                                try {
                                    if (!release.await(5, TimeUnit.SECONDS))
                                        throw new AssertionError("history fixture timed out");
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(ex);
                                }
                            }
                            return physical.openReader(id).events();
                        };
                    }

                    public List<String> list() {
                        return physical.list();
                    }

                    public String fork(String id, String event) {
                        return physical.fork(id, event);
                    }
                };
        try (var host =
                        host(
                                new FakeLlm(
                                        List.of(new ChatResponse("done", List.of(), Map.of())),
                                        8192),
                                repo);
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var admitted =
                    request(
                            host,
                            "/v1/tasks",
                            "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"history\"}");
            assertEquals(202, admitted.statusCode());
            String task =
                    (String)
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(admitted.body(), Map.class)
                                    .get("taskId");
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            boolean finished = false;
            while (System.nanoTime() < end) {
                var state =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(
                                        request(host, "/v1/tasks/" + task, null).body(), Map.class);
                if ("FINISHED".equals(state.get("phase"))) {
                    finished = true;
                    break;
                }
                Thread.sleep(5);
            }
            assertTrue(finished);
            block.set(true);
            var page = pool.submit(() -> request(host, "/v1/tasks?view=all", null));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertEquals(200, request(host, "/v1/workspaces", null).statusCode());
                assertFalse(page.isDone());
            } finally {
                release.countDown();
            }
            assertEquals(200, page.get(2, TimeUnit.SECONDS).statusCode());
        } finally {
            release.countDown();
        }
    }

    HttpResponse<String> request(HarnessWebServer host, String path, String body) throws Exception {
        return request(host, path, body, Duration.ofSeconds(1));
    }

    HttpResponse<String> request(HarnessWebServer host, String path, String body, Duration timeout)
            throws Exception {
        var b =
                HttpRequest.newBuilder(host.address().resolve(path))
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + TOKEN);
        if (body != null)
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        else b.GET();
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void slowSettingsBodyCannotOwnServerLockOrBlockUnrelatedRequests() throws Exception {
        try (var host =
                host(new FakeLlm(List.of(new ChatResponse("done", List.of(), Map.of())), 8192))) {
            host.settings(
                    new HarnessWebServer.Settings() {
                        public Map<String, Object> read() {
                            return Map.of();
                        }

                        public Map<String, Object> save(Map<String, Object> in) {
                            return Map.of();
                        }

                        public void prepareTask() {}
                    });
            try (var socket = new Socket("127.0.0.1", host.address().getPort())) {
                socket.getOutputStream()
                        .write(
                                ("POST /v1/settings HTTP/1.1\r\nHost: 127.0.0.1:"
                                                + host.address().getPort()
                                                + "\r\nAuthorization: Bearer "
                                                + TOKEN
                                                + "\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{")
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                Thread.sleep(100);
                assertEquals(200, request(host, "/v1/workspaces", null).statusCode());
            }
        }
    }

    @Test
    void slowSettingsSaveReleasesServerMonitorAndReservesAdmission() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var host =
                        host(
                                new FakeLlm(
                                        List.of(new ChatResponse("done", List.of(), Map.of())),
                                        8192));
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            host.settings(
                    new HarnessWebServer.Settings() {
                        public Map<String, Object> read() {
                            return Map.of();
                        }

                        public Map<String, Object> save(Map<String, Object> in) {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            return Map.of("saved", true);
                        }

                        public void prepareTask() {}
                    });
            var save =
                    pool.submit(
                            () ->
                                    request(
                                            host,
                                            "/v1/settings",
                                            "{\"revision\":\"r\",\"models\":[],\"selected\":\"x\",\"extensions\":{},\"apiKeys\":{}}"));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertEquals(200, request(host, "/v1/workspaces", null).statusCode());
                assertEquals(
                        409,
                        request(
                                        host,
                                        "/v1/tasks",
                                        "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"busy\"}")
                                .statusCode());
            } finally {
                release.countDown();
            }
            assertEquals(200, save.get(2, TimeUnit.SECONDS).statusCode());
        } finally {
            release.countDown();
        }
    }

    @Test
    void closeRetainsCatalogOwnershipUntilUncooperativeWorkerActuallyLeaves() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        LlmClient model =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(true, false, false, false, 8192);
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return new PreparedRequest(request);
                    }

                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        entered.countDown();
                        while (release.getCount() > 0) {
                            try {
                                release.await();
                            } catch (InterruptedException ignored) {
                                /* deliberately noncooperating fixture */
                            }
                        }
                        return new ChatResponse("done", List.of(), Map.of());
                    }

                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        throw new UnsupportedOperationException();
                    }

                    public void close() {
                        closed.countDown();
                    }
                };
        var host = host(model);
        try {
            assertEquals(
                    202,
                    request(
                                    host,
                                    "/v1/tasks",
                                    "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"hold\"}")
                            .statusCode());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            host.close();
            var catalog = new JsonlSessionRepository(root.resolve("catalog"));
            assertThrows(
                    HarnessException.class,
                    () -> catalog.openWriter("web-catalog", WriteOwnership.EXCLUSIVE));
            release.countDown();
            assertTrue(closed.await(4, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            boolean freed = false;
            while (System.nanoTime() < deadline) {
                try (var writer = catalog.openWriter("web-catalog", WriteOwnership.EXCLUSIVE)) {
                    writer.flush();
                    freed = true;
                    break;
                } catch (HarnessException busy) {
                    Thread.sleep(5);
                }
            }
            assertTrue(freed);
        } finally {
            release.countDown();
            host.close();
        }
    }

    @Test
    void concurrentProfileRegistrationAndInfoReadReturnCompleteSafeSnapshots() throws Exception {
        var registered = new CopyOnWriteArrayList<Map<String, Object>>();
        try (var host =
                        host(
                                new FakeLlm(
                                        List.of(new ChatResponse("done", List.of(), Map.of())),
                                        8192));
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            host.workspaces(
                    new HarnessWebServer.Workspaces() {
                        public Map<String, Object> read() {
                            return Map.of("workspaces", List.copyOf(registered));
                        }

                        public Map.Entry<String, HarnessWebServer.Profile> register(
                                String path, String name) throws java.io.IOException {
                            registered.add(Map.of("id", name, "name", name));
                            return Map.entry(
                                    name,
                                    new HarnessWebServer.Profile(
                                            "workspace-" + name,
                                            new JsonlSessionRepository(
                                                    root.resolve("sessions-" + name)),
                                            (id, a, z) -> {
                                                throw new AssertionError(
                                                        "Registration must not execute SDK");
                                            }));
                        }
                    });
            var start = new CyclicBarrier(2);
            var reads =
                    pool.submit(
                            () -> {
                                start.await();
                                for (int i = 0; i < 60; i++) {
                                    var response = request(host, "/v1/info", null);
                                    assertEquals(200, response.statusCode());
                                    var body =
                                            new com.fasterxml.jackson.databind.ObjectMapper()
                                                    .readValue(response.body(), Map.class);
                                    var profiles = (List<?>) body.get("profiles");
                                    assertTrue(profiles.contains("local"));
                                    assertEquals(profiles.size(), new HashSet<>(profiles).size());
                                }
                                return true;
                            });
            start.await();
            for (int i = 0; i < 12; i++)
                assertEquals(
                        200,
                        request(
                                        host,
                                        "/v1/workspaces",
                                        "{\"path\":\"fixture\",\"name\":\"p" + i + "\"}")
                                .statusCode());
            assertTrue(reads.get(8, TimeUnit.SECONDS));
            var body =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(request(host, "/v1/info", null).body(), Map.class);
            assertEquals(13, ((List<?>) body.get("profiles")).size());
        }
    }

    @Test
    void displaySnapshotFailureStillClosesSdkAndPreservesUnknownOwnership() throws Exception {
        var physical = new JsonlSessionRepository(root.resolve("sessions"));
        var injected = new AtomicInteger();
        var closed = new CountDownLatch(1);
        SessionRepository repo =
                new SessionRepository() {
                    public SessionWriter openWriter(String id, WriteOwnership ownership) {
                        return physical.openWriter(id, ownership);
                    }

                    public SessionReader openReader(String id) {
                        return () -> {
                            boolean display =
                                    Arrays.stream(Thread.currentThread().getStackTrace())
                                            .anyMatch(
                                                    frame ->
                                                            frame.getClassName()
                                                                            .equals(
                                                                                    "io.jharness.ecosystem.ModelDisplayBuffer")
                                                                    && frame.getMethodName()
                                                                            .equals("snapshot"));
                            if (display) {
                                injected.incrementAndGet();
                                throw new HarnessException(
                                        "INJECTED_DISPLAY_READ", "fixture display failure");
                            }
                            return physical.openReader(id).events();
                        };
                    }

                    public List<String> list() {
                        return physical.list();
                    }

                    public String fork(String id, String event) {
                        return physical.fork(id, event);
                    }
                };
        var delegate = new FakeLlm(List.of(new ChatResponse("done", List.of(), Map.of())), 8192);
        LlmClient model =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return delegate.capabilities();
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return delegate.prepare(request);
                    }

                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        return delegate.complete(request, token);
                    }

                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        return delegate.stream(request, token);
                    }

                    public void close() {
                        closed.countDown();
                    }
                };
        try (var host = host(model, repo)) {
            var admitted =
                    request(
                            host,
                            "/v1/tasks",
                            "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"display-failure\"}");
            assertEquals(202, admitted.statusCode());
            String task =
                    (String)
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(admitted.body(), Map.class)
                                    .get("taskId");
            assertTrue(closed.await(3, TimeUnit.SECONDS));
            assertTrue(injected.get() > 0);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            Map<?, ?> state = Map.of();
            while (System.nanoTime() < end) {
                state =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(
                                        request(host, "/v1/tasks/" + task, null).body(), Map.class);
                if ("UNKNOWN".equals(state.get("phase"))) break;
                Thread.sleep(5);
            }
            assertEquals("UNKNOWN", state.get("phase"));
            assertEquals("TASK_DISPLAY_UNCERTAIN", state.get("error"));
            assertEquals(
                    409,
                    request(
                                    host,
                                    "/v1/tasks",
                                    "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"must-stay-owned\"}")
                            .statusCode());
        }
    }

    @Test
    void failedQueuePauseNotifiesWaitersAndCannotReleaseUncertainTask() throws Exception {
        var injected = new AtomicInteger();
        var catalog =
                new JsonlSessionRepository(
                        root.resolve("catalog"),
                        JsonlSessionRepository.TailPolicy.REJECT,
                        channel -> {
                            boolean pause =
                                    Arrays.stream(Thread.currentThread().getStackTrace())
                                            .anyMatch(
                                                    frame ->
                                                            frame.getClassName()
                                                                            .equals(
                                                                                    HarnessWebServer
                                                                                            .class
                                                                                            .getName())
                                                                    && frame.getMethodName()
                                                                            .equals("pauseInputs"));
                            if (pause) {
                                injected.incrementAndGet();
                                throw new java.io.IOException("fixture pause checkpoint failure");
                            }
                            channel.force(true);
                        },
                        code -> {});
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var notified = new CountDownLatch(1);
        LlmClient model =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(true, false, false, false, 8192);
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return new PreparedRequest(request);
                    }

                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("fixture release timeout");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        token.throwIfCancelled();
                        return new ChatResponse("done", List.of(), Map.of());
                    }

                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        throw new UnsupportedOperationException();
                    }

                    public void close() {}
                };
        try (var host = host(model, new JsonlSessionRepository(root.resolve("sessions")), catalog);
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var admitted =
                    request(
                            host,
                            "/v1/tasks",
                            "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"pause-failure\"}");
            assertEquals(202, admitted.statusCode());
            String task =
                    (String)
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(admitted.body(), Map.class)
                                    .get("taskId");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(
                    202,
                    request(
                                    host,
                                    "/v1/tasks/" + task + "/turns",
                                    "{\"requestId\":\"queued\",\"text\":\"must not execute\",\"kind\":\"message\"}")
                            .statusCode());
            assertEquals(200, request(host, "/v1/tasks/" + task + "/cancel", "{}").statusCode());
            var waiter =
                    pool.submit(
                            () -> {
                                synchronized (host) {
                                    waiting.countDown();
                                    host.wait(5000);
                                    notified.countDown();
                                }
                                return true;
                            });
            assertTrue(waiting.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(notified.await(2, TimeUnit.SECONDS));
            assertTrue(waiter.get(1, TimeUnit.SECONDS));
            assertEquals(1, injected.get());
            var state =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(request(host, "/v1/tasks/" + task, null).body(), Map.class);
            assertEquals("UNKNOWN", state.get("phase"));
            assertEquals("QUEUE_CHECKPOINT_FAILED", state.get("error"));
            assertEquals(
                    503,
                    request(
                                    host,
                                    "/v1/tasks",
                                    "{\"profile\":\"local\",\"goal\":\"test\",\"requestId\":\"broken\"}")
                            .statusCode());
        } finally {
            release.countDown();
        }
    }

    // The owner token is an explicit barrier before the decision read; real HTTP POSTs
    // still traverse authentication, task association, deadline checks and completion.
    @Test
    void approvalAcceptedBeforeDeadlineSurvivesOwnerResumingAfterDeadline() throws Exception {
        approvalDeadlineRace(true);
    }

    @Test
    void expiredApprovalPostAndOwnerContendAtSameBarrierAndBothDeny() throws Exception {
        approvalDeadlineRace(false);
    }

    @SuppressWarnings("unchecked")
    void approvalDeadlineRace(boolean acceptBeforeDeadline) throws Exception {
        try (var host =
                        host(
                                new FakeLlm(List.of(), 8192),
                                new JsonlSessionRepository(root.resolve("sessions")),
                                new JsonlSessionRepository(root.resolve("catalog")),
                                Duration.ofMillis(1500));
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Class<?> taskType = Class.forName(HarnessWebServer.class.getName() + "$Task");
            var constructor =
                    taskType.getDeclaredConstructor(
                            String.class, String.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            Object task =
                    constructor.newInstance(
                            "race-task", "local", "0".repeat(64), "1".repeat(64), "workspace");
            var tasksField = HarnessWebServer.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            synchronized (host) {
                ((Map<String, Object>) tasksField.get(host)).put("race-task", task);
            }
            var approve =
                    HarnessWebServer.class.getDeclaredMethod(
                            "approve", taskType, ToolInvocation.class, ToolContext.class);
            approve.setAccessible(true);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            CancellationToken token =
                    () -> {
                        // approve checks cancellation only after publishing the pending approval.
                        entered.countDown();
                        try {
                            if (!release.await(4, TimeUnit.SECONDS))
                                throw new AssertionError("owner release timeout");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                        }
                        return false;
                    };
            var owner =
                    pool.submit(
                            () ->
                                    (ApprovalService.ApprovalDecision)
                                            approve.invoke(
                                                    host,
                                                    task,
                                                    new ToolInvocation(
                                                            "race-call",
                                                            "write_file",
                                                            Map.of("path", "x"),
                                                            SideEffectLevel.WORKSPACE_WRITE),
                                                    new ToolContext(
                                                            new PluginScope("race-task", "agent"),
                                                            token)));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                var response = request(host, "/v1/tasks/race-task/approvals", null);
                var body =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(response.body(), Map.class);
                var approvals = (List<Map<String, Object>>) body.get("approvals");
                assertEquals(1, approvals.size());
                String path =
                        "/v1/tasks/race-task/approvals/" + approvals.getFirst().get("approvalId");
                var approvalsField = taskType.getDeclaredField("approvals");
                approvalsField.setAccessible(true);
                Object pending;
                synchronized (host) {
                    pending = ((Map<?, ?>) approvalsField.get(task)).values().iterator().next();
                }
                var deadlineField = pending.getClass().getDeclaredField("deadline");
                deadlineField.setAccessible(true);
                long deadline = deadlineField.getLong(pending);
                String decision =
                        "{\"approved\":true,\"reason\":\"operator\",\"callId\":\"race-call\"}";
                Future<HttpResponse<String>> posted = null;
                if (acceptBeforeDeadline) {
                    var accepted = request(host, path, decision);
                    assertEquals(200, accepted.statusCode());
                    assertTrue(accepted.body().contains("true"));
                }
                synchronized (host) {
                    if (!acceptBeforeDeadline) {
                        var postStarted = new CountDownLatch(1);
                        posted =
                                pool.submit(
                                        () -> {
                                            postStarted.countDown();
                                            return request(
                                                    host, path, decision, Duration.ofSeconds(4));
                                        });
                        assertTrue(postStarted.await(1, TimeUnit.SECONDS));
                        release.countDown();
                        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
                        long waitingUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                        boolean httpBlocked = false;
                        while (System.nanoTime() < waitingUntil && !httpBlocked) {
                            httpBlocked =
                                    Arrays.stream(threads.dumpAllThreads(false, false))
                                            .anyMatch(
                                                    info ->
                                                            info.getThreadName()
                                                                            .startsWith(
                                                                                    "jharness-web-http")
                                                                    && info.getThreadState()
                                                                            == Thread.State.BLOCKED
                                                                    && info.getLockInfo() != null
                                                                    && info.getLockInfo()
                                                                                    .getIdentityHashCode()
                                                                            == System
                                                                                    .identityHashCode(
                                                                                            host));
                            if (!httpBlocked) Thread.sleep(2);
                        }
                        assertTrue(
                                httpBlocked,
                                "HTTP handler must contend for the held server monitor");
                    }
                    // Both contenders cannot acquire the monitor until the deadline is past.
                    while (System.nanoTime() <= deadline) Thread.sleep(2);
                    if (acceptBeforeDeadline) release.countDown();
                }
                var outcome = owner.get(2, TimeUnit.SECONDS);
                assertEquals(acceptBeforeDeadline, outcome.approved());
                assertEquals(
                        acceptBeforeDeadline ? "operator" : "Approval expired", outcome.reason());
                assertEquals(acceptBeforeDeadline ? "web-operator" : "web-server", outcome.actor());
                if (posted != null) {
                    var rejected = posted.get(2, TimeUnit.SECONDS);
                    assertEquals(409, rejected.statusCode());
                    assertTrue(rejected.body().contains("STALE_APPROVAL"));
                }
                assertEquals(409, request(host, path, decision).statusCode());
                var empty =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(
                                        request(host, "/v1/tasks/race-task/approvals", null).body(),
                                        Map.class);
                assertTrue(((List<?>) empty.get("approvals")).isEmpty());
            } finally {
                release.countDown();
            }
        }
    }
}
