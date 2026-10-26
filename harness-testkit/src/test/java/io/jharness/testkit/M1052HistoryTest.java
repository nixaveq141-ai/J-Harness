package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M1052HistoryTest {
    @Test
    void interruptedAdmissionIsRecoveredFromWorkspacePointerWithoutExecuting() throws Exception {
        var sessions = new JsonlSessionRepository(root.resolve("sessions"));
        var fail = new AtomicBoolean(false);
        var catalog =
                new JsonlSessionRepository(
                        root.resolve("catalog"),
                        JsonlSessionRepository.TailPolicy.REJECT,
                        channel -> {
                            if (fail.get())
                                throw new java.io.IOException("injected admission force");
                            channel.force(true);
                        },
                        code -> {});
        var opens = new AtomicInteger();
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (id, approvals, sanitizer) -> {
                            opens.incrementAndGet();
                            throw new AssertionError("Must never execute interrupted admission");
                        });
        String id;
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile),
                        catalog,
                        "one",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            fail.set(true);
            assertEquals(
                    503,
                    request(
                                    server.address(),
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "local",
                                            "goal",
                                            "Read fixture",
                                            "requestId",
                                            "interrupted"))
                            .statusCode());
            assertEquals(0, opens.get());
        }
        fail.set(false);
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile),
                        catalog,
                        "one",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            id =
                    (String)
                            object(request(server.address(), "/v1/submissions/interrupted", null))
                                    .get("taskId");
            assertEquals(
                    "UNKNOWN",
                    object(request(server.address(), "/v1/tasks/" + id, null)).get("phase"));
            assertEquals(0, opens.get());
        }
        HarnessWebServer.verifyTask(
                catalog,
                "one",
                sessions,
                id,
                "workspace",
                "fault before SDK construction; host stopped; resources checked",
                new SecretRedactor());
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile),
                        catalog,
                        "one",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            assertEquals(
                    "VERIFIED",
                    object(request(server.address(), "/v1/submissions/interrupted", null))
                            .get("phase"));
            assertEquals(0, opens.get());
        }
    }

    @Test
    void futureCatalogVersionIsRejectedWithoutChangingItsBytes() throws Exception {
        var catalog = new JsonlSessionRepository(root.resolve("catalog"));
        var sessions = new JsonlSessionRepository(root.resolve("sessions"));
        try (var writer = catalog.openWriter("future", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session("future", "web.catalog.version", Map.of("version", 3)));
            writer.flush();
        }
        byte[] before = Files.readAllBytes(root.resolve("catalog/future.jsonl"));
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (id, approvals, sanitizer) -> {
                            throw new AssertionError("Future catalog cannot execute");
                        });
        assertEquals(
                "FUTURE_WEB_CATALOG",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new HarnessWebServer(
                                                0,
                                                M10BrowserHost.TOKEN,
                                                Map.of("local", profile),
                                                catalog,
                                                "future",
                                                new SecretRedactor(),
                                                1,
                                                32,
                                                Duration.ofSeconds(1)))
                        .code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("catalog/future.jsonl")));
    }

    @TempDir Path root;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private HttpResponse<String> request(URI base, String path, Object body) throws Exception {
        var b =
                HttpRequest.newBuilder(base.resolve(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + M10BrowserHost.TOKEN);
        if (body == null) b.GET();
        else
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> object(HttpResponse<String> response) throws Exception {
        assertTrue(response.statusCode() < 300, response.statusCode() + ": " + response.body());
        return JSON.readValue(response.body(), new TypeReference<>() {});
    }

    private String submit(URI base, String profile, String id) throws Exception {
        return (String)
                object(
                                request(
                                        base,
                                        "/v1/tasks",
                                        Map.of(
                                                "profile",
                                                profile,
                                                "goal",
                                                "Read fixture",
                                                "requestId",
                                                id)))
                        .get("taskId");
    }

    private Map<String, Object> finished(URI base, String id) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < end) {
            var task = object(request(base, "/v1/tasks/" + id, null));
            if (Set.of("FINISHED", "UNKNOWN", "VERIFIED").contains(task.get("phase"))) return task;
            Thread.sleep(10);
        }
        throw new AssertionError("Task not settled");
    }

    private Map<String, Object> change(
            URI base, Map<String, Object> task, String name, boolean pinned, boolean archived)
            throws Exception {
        return object(
                request(
                        base,
                        "/v1/tasks/" + task.get("taskId") + "/metadata",
                        Map.of(
                                "revision",
                                task.get("revision"),
                                "name",
                                name,
                                "pinned",
                                pinned,
                                "archived",
                                archived)));
    }

    @Test
    void sixtyFiveTasksPageArchiveForkExportAndRestartWithoutLosingIdempotence() throws Exception {
        List<String> ids = new ArrayList<>();
        String fork;
        try (var host = new M10BrowserHost(root)) {
            URI base = host.server.address();
            for (int i = 0; i < 65; i++) {
                String id = submit(base, "read", "history-" + i);
                ids.add(id);
                assertEquals("FINISHED", finished(base, id).get("phase"));
            }
            assertEquals(65, host.opened.get());
            assertEquals(ids.getFirst(), submit(base, "read", "history-0"));
            assertEquals(65, host.opened.get());
            var seen = new HashSet<String>();
            String cursor = "";
            int pages = 0;
            do {
                var page =
                        object(
                                request(
                                        base,
                                        "/v1/tasks?limit=24&view=all"
                                                + (cursor.isEmpty() ? "" : "&after=" + cursor),
                                        null));
                assertTrue(((Number) page.get("hotCount")).intValue() <= 32);
                assertEquals(65, ((Number) page.get("historyCount")).intValue());
                for (Object raw : (List<?>) page.get("tasks"))
                    assertTrue(seen.add((String) Values.object(raw).get("taskId")));
                cursor = (String) page.get("next");
                pages++;
            } while (!cursor.isEmpty());
            assertEquals(3, pages);
            assertEquals(new HashSet<>(ids), seen);
            var oldResponse = request(base, "/v1/tasks?limit=8", null);
            assertEquals(200, oldResponse.statusCode());
            var oldPage = object(oldResponse);
            assertEquals(8, ((List<?>) oldPage.get("tasks")).size());
            assertEquals(true, oldPage.get("more"));
            String oldCursor = assertInstanceOf(String.class, oldPage.get("next"));
            assertFalse(oldCursor.isEmpty());
            assertEquals(
                    200, request(base, "/v1/tasks?limit=8&after=" + oldCursor, null).statusCode());
            var initial = object(request(base, "/v1/tasks/" + ids.getFirst(), null));
            var named = change(base, initial, "Pinned investigation", true, false);
            assertEquals(
                    409,
                    request(
                                    base,
                                    "/v1/tasks/" + ids.getFirst() + "/metadata",
                                    Map.of(
                                            "revision",
                                            initial.get("revision"),
                                            "name",
                                            "stale",
                                            "pinned",
                                            false,
                                            "archived",
                                            false))
                            .statusCode());
            var stalePage = request(base, "/v1/tasks?limit=8&after=" + oldCursor, null);
            assertEquals(400, stalePage.statusCode());
            assertEquals(
                    "UNKNOWN_CURSOR",
                    new ObjectMapper().readTree(stalePage.body()).get("error").asText());
            assertEquals(
                    ids.getFirst(),
                    Values.object(
                                    ((List<?>)
                                                    object(request(base, "/v1/tasks", null))
                                                            .get("tasks"))
                                            .getFirst())
                            .get("taskId"));
            var archived = change(base, named, "Pinned investigation", true, true);
            assertEquals(
                    1,
                    ((List<?>) object(request(base, "/v1/tasks?view=archived", null)).get("tasks"))
                            .size());
            var repo = new JsonlSessionRepository(root.resolve("read-sessions"));
            assertTrue(SessionLifecycle.archived(repo.openReader(ids.getFirst()).events()));
            change(base, archived, "Pinned investigation", true, false);
            assertFalse(SessionLifecycle.archived(repo.openReader(ids.getFirst()).events()));
            var exported = object(request(base, "/v1/tasks/" + ids.getFirst() + "/export", null));
            byte[] content =
                    ((String) exported.get("content"))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                    exported.get("sha256"));
            assertEquals(
                    repo.openReader(ids.getFirst()).events().size() + 1,
                    ((String) exported.get("content")).lines().count());
            var artifacts =
                    new LocalArtifactStore(root.resolve("read-artifacts"), new SecretRedactor());
            var artifact = artifacts.put(ids.getFirst(), "preserved fork artifact");
            var history = repo.openReader(ids.getFirst()).events();
            String eventId = history.getLast().eventId();
            var body = Map.of("eventId", eventId, "requestId", "snapshot-request");
            fork =
                    (String)
                            object(request(base, "/v1/tasks/" + ids.getFirst() + "/fork", body))
                                    .get("taskId");
            assertEquals(
                    fork,
                    object(request(base, "/v1/tasks/" + ids.getFirst() + "/fork", body))
                            .get("taskId"));
            assertEquals("preserved fork artifact", artifacts.read(fork, artifact.id(), 0, 200));
            var child = repo.openReader(fork).events();
            assertEquals(history.getFirst().eventId(), child.getFirst().eventId());
            assertEquals("session.forked", child.getLast().type());
            assertEquals(65, host.opened.get());
        }
        // SDK fact survives a crash before its derived catalog metadata is appended.
        SessionLifecycle.setArchived(
                new JsonlSessionRepository(root.resolve("read-sessions")),
                ids.getFirst(),
                true,
                new SecretRedactor());
        try (var host = new M10BrowserHost(root)) {
            URI base = host.server.address();
            var recoveredArchive = object(request(base, "/v1/tasks/" + ids.getFirst(), null));
            assertEquals(true, recoveredArchive.get("archived"));
            assertEquals(
                    1,
                    ((List<?>) object(request(base, "/v1/tasks?view=archived", null)).get("tasks"))
                            .size());
            change(base, recoveredArchive, "Pinned investigation", true, false);
            assertEquals(ids.getFirst(), submit(base, "read", "history-0"));
            assertEquals(
                    fork,
                    object(request(base, "/v1/submissions/snapshot-request", null)).get("taskId"));
            assertEquals(
                    "Pinned investigation",
                    object(request(base, "/v1/tasks/" + ids.getFirst(), null)).get("name"));
            assertEquals(0, host.opened.get());
            assertEquals(
                    "FINISHED", finished(base, submit(base, "read", "after-restart")).get("phase"));
        }
    }

    @Test
    void unknownAndRunningMaintenanceCannotReleaseBindings() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            URI base = host.server.address();
            String id = submit(base, "unknown", "uncertain");
            var t = finished(base, id);
            assertEquals("UNKNOWN", t.get("phase"));
            assertEquals(
                    409,
                    request(
                                    base,
                                    "/v1/tasks/" + id + "/metadata",
                                    Map.of(
                                            "revision",
                                            t.get("revision"),
                                            "name",
                                            "still blocked",
                                            "pinned",
                                            false,
                                            "archived",
                                            true))
                            .statusCode());
            assertEquals(
                    409,
                    request(
                                    base,
                                    "/v1/tasks/" + id + "/fork",
                                    Map.of("eventId", "unused", "requestId", "blocked-fork"))
                            .statusCode());
            assertEquals(409, request(base, "/v1/tasks/" + id + "/export", null).statusCode());
            assertEquals(
                    409,
                    request(
                                    base,
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "unknown",
                                            "goal",
                                            "again",
                                            "requestId",
                                            "blocked-create"))
                            .statusCode());
            String running = submit(base, "cancel", "running");
            var active = object(request(base, "/v1/tasks/" + running, null));
            assertEquals(
                    409,
                    request(
                                    base,
                                    "/v1/tasks/" + running + "/metadata",
                                    Map.of(
                                            "revision",
                                            active.get("revision"),
                                            "name",
                                            "active",
                                            "pinned",
                                            false,
                                            "archived",
                                            true))
                            .statusCode());
            request(base, "/v1/tasks/" + running + "/cancel", Map.of());
            finished(base, running);
        }
    }

    @Test
    void workspaceOwnershipSurvivesCatalogSwitchAndRequiresOriginalOfflineVerification()
            throws Exception {
        var sessions = new JsonlSessionRepository(root.resolve("sessions"));
        var catalog = new JsonlSessionRepository(root.resolve("catalog"));
        var opens = new AtomicInteger();
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (id, approval, sanitizer) -> {
                            opens.incrementAndGet();
                            throw new IllegalStateException("factory uncertain");
                        });
        String id;
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile, "alias", profile),
                        catalog,
                        "one",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            id = submit(server.address(), "local", "one");
            assertEquals("UNKNOWN", finished(server.address(), id).get("phase"));
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            new HarnessWebServer(
                                                    0,
                                                    M10BrowserHost.TOKEN,
                                                    Map.of("local", profile),
                                                    catalog,
                                                    "two",
                                                    new SecretRedactor(),
                                                    1,
                                                    32,
                                                    Duration.ofSeconds(1)))
                            .code());
        }
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("alias", profile),
                        catalog,
                        "two",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            assertEquals(
                    409,
                    request(
                                    server.address(),
                                    "/v1/tasks",
                                    Map.of("profile", "alias", "goal", "again", "requestId", "two"))
                            .statusCode());
            assertEquals(1, opens.get());
        }
        assertEquals(
                "ENVIRONMENT_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        HarnessWebServer.verifyTask(
                                                catalog,
                                                "two",
                                                sessions,
                                                id,
                                                "workspace",
                                                "checked original resources",
                                                new SecretRedactor()))
                        .code());
        HarnessWebServer.verifyTask(
                catalog,
                "one",
                sessions,
                id,
                "workspace",
                "old host stopped and actual resources inspected",
                new SecretRedactor());
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile),
                        catalog,
                        "one",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            assertEquals(
                    "VERIFIED",
                    object(request(server.address(), "/v1/tasks/" + id, null)).get("phase"));
            assertEquals(1, opens.get());
        }
    }

    @Test
    void legacyCatalogKeepsExactByteBackupAndMigrationFailureNeverExecutes() throws Exception {
        var fail = new AtomicBoolean();
        var injected = new AtomicInteger();
        var catalog =
                new JsonlSessionRepository(
                        root.resolve("catalog"),
                        JsonlSessionRepository.TailPolicy.REJECT,
                        channel -> {
                            if (fail.get()) {
                                injected.incrementAndGet();
                                throw new java.io.IOException("injected backup force");
                            }
                            channel.force(true);
                        },
                        code -> {});
        var sessions = new JsonlSessionRepository(root.resolve("sessions"));
        String hash = "a".repeat(64), id = "web-legacy";
        try (var writer = catalog.openWriter("old", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            "old",
                            "web.task.accepted",
                            Map.of(
                                    "taskId",
                                    id,
                                    "profile",
                                    "local",
                                    "requestHash",
                                    hash,
                                    "goalHash",
                                    "b".repeat(64),
                                    "workspaceBinding",
                                    "workspace")));
            writer.append(
                    SessionEvent.session(
                            "old",
                            "web.task.finished",
                            Map.of(
                                    "taskId",
                                    id,
                                    "phase",
                                    "FINISHED",
                                    "status",
                                    "COMPLETED",
                                    "error",
                                    "")));
            writer.flush();
        }
        byte[] original = Files.readAllBytes(root.resolve("catalog/old.jsonl"));
        var opens = new AtomicInteger();
        var profile =
                new HarnessWebServer.Profile(
                        "workspace",
                        sessions,
                        (task, approvals, sanitizer) -> {
                            opens.incrementAndGet();
                            throw new IllegalStateException();
                        });
        fail.set(true);
        assertEquals(
                "SESSION_BACKUP_FAILED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new HarnessWebServer(
                                                0,
                                                M10BrowserHost.TOKEN,
                                                Map.of("local", profile),
                                                catalog,
                                                "old",
                                                new SecretRedactor(),
                                                1,
                                                32,
                                                Duration.ofSeconds(1)))
                        .code());
        assertEquals(0, opens.get());
        assertEquals(1, injected.get());
        assertArrayEquals(original, Files.readAllBytes(root.resolve("catalog/old.jsonl")));
        try (var backups = Files.newDirectoryStream(root.resolve("catalog"), "old.backup-*.bak")) {
            assertArrayEquals(original, Files.readAllBytes(backups.iterator().next()));
        }
        fail.set(false);
        try (var server =
                new HarnessWebServer(
                        0,
                        M10BrowserHost.TOKEN,
                        Map.of("local", profile),
                        catalog,
                        "old",
                        new SecretRedactor(),
                        1,
                        32,
                        Duration.ofSeconds(1))) {
            assertEquals(
                    "COMPLETED",
                    object(request(server.address(), "/v1/tasks/" + id, null)).get("status"));
            assertEquals(
                    1,
                    ((List<?>) object(request(server.address(), "/v1/tasks", null)).get("tasks"))
                            .size());
            assertEquals(0, opens.get());
        }
    }
}
