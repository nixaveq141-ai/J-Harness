package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2WebFactsTest {
    @TempDir Path root;
    private final String hash = "a".repeat(64);

    @Test
    void replacementDurableRepositoryRestoresMetadataAndPausedTurnProjection() throws Exception {
        var backing = new JsonlSessionRepository(root);
        String inputHash = "b".repeat(64), token = "r2_token_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        try (var writer = backing.openWriter("catalog", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    fact(
                            "web.task.accepted",
                            Map.of(
                                    "taskId",
                                    "web-legacy",
                                    "profile",
                                    "local",
                                    "requestHash",
                                    hash,
                                    "goalHash",
                                    hash,
                                    "workspaceBinding",
                                    "binding")));
            writer.append(
                    fact(
                            "web.task.changed",
                            Map.of(
                                    "taskId",
                                    "web-legacy",
                                    "name",
                                    "Restored metadata",
                                    "pinned",
                                    true,
                                    "archived",
                                    false)));
            writer.append(
                    fact(
                            "web.task.finished",
                            Map.of(
                                    "taskId",
                                    "web-legacy",
                                    "phase",
                                    "FINISHED",
                                    "status",
                                    "COMPLETED",
                                    "error",
                                    "")));
            writer.append(
                    fact(
                            "web.turn.accepted",
                            Map.of(
                                    "taskId",
                                    "web-legacy",
                                    "requestHash",
                                    inputHash,
                                    "goalHash",
                                    hash,
                                    "kind",
                                    "message",
                                    "content",
                                    "paused input")));
        }
        SessionRepository replacement =
                new SessionRepository() {
                    public SessionWriter openWriter(String id, WriteOwnership ownership) {
                        return backing.openWriter(id, ownership);
                    }

                    public SessionReader openReader(String id) {
                        return backing.openReader(id);
                    }

                    public List<String> list() {
                        return backing.list();
                    }

                    public String fork(String id, String event) {
                        return backing.fork(id, event);
                    }
                };
        var profile =
                new HarnessWebServer.Profile(
                        "binding",
                        new InMemorySessionRepository(),
                        (id, approvals, sanitizer) -> {
                            throw new AssertionError("Restoration must never construct execution");
                        });
        try (var server =
                        new HarnessWebServer(
                                0,
                                token,
                                Map.of("local", profile),
                                replacement,
                                "catalog",
                                new SecretRedactor(),
                                1,
                                4,
                                java.time.Duration.ofSeconds(1));
                var client = java.net.http.HttpClient.newHttpClient()) {
            var detail =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            server.address().resolve("/v1/tasks/web-legacy"))
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detail.statusCode(), detail.body());
            assertTrue(detail.body().contains("Restored metadata"));
            assertTrue(detail.body().contains("\"pinned\":true"));
            var turns =
                    client.send(
                            java.net.http.HttpRequest.newBuilder(
                                            server.address().resolve("/v1/tasks/web-legacy/turns"))
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, turns.statusCode(), turns.body());
            assertTrue(turns.body().contains(inputHash));
            assertTrue(turns.body().contains("PAUSED"));
        }
        try (var writer = backing.openWriter("catalog", WriteOwnership.EXCLUSIVE)) {
            writer.flush();
        }
    }

    private SessionEvent fact(String type, Map<String, Object> payload) {
        return SessionEvent.session("catalog", type, payload);
    }

    @Test
    void migrationResumesAcrossNewSecretConfigurationWithoutLosingIdentity() throws Exception {
        var repo = new JsonlSessionRepository(root);
        try (var writer = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    fact(
                            "web.task.accepted",
                            Map.of(
                                    "taskId",
                                    "web-legacy",
                                    "requestHash",
                                    hash,
                                    "goalHash",
                                    hash,
                                    "profile",
                                    "local",
                                    "workspaceBinding",
                                    "binding",
                                    "note",
                                    "new-secret")));
        }
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE)) {
            assertEquals(
                    "MIGRATION_INTERRUPTED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            new DurableTaskCatalog(
                                                    repo,
                                                    "catalog",
                                                    owner,
                                                    new SecretRedactor(),
                                                    () -> {
                                                        throw new HarnessException(
                                                                "MIGRATION_INTERRUPTED",
                                                                "test checkpoint");
                                                    }))
                            .code());
        }
        assertEquals(1, repo.openReader("catalog").events().size());
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(
                                repo,
                                "catalog",
                                owner,
                                new SecretRedactor(List.of("new-secret")))) {
            assertEquals(1, catalog.count());
            assertEquals("web-legacy", catalog.byHash(hash).getFirst().payload().get("taskId"));
            assertEquals(
                    64,
                    ((String) catalog.byHash(hash).getFirst().payload().get("migrationSourceHash"))
                            .length());
        }
    }

    @Test
    void catalogRejectsMalformedWritesAndPreservesUnknownOutcomes() throws Exception {
        var repo = new JsonlSessionRepository(root);
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor())) {
            assertEquals(
                    "INVALID_WEB_CATALOG",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            catalog.append(
                                                    fact(
                                                            "web.task.accepted",
                                                            Map.of("taskId", "../bad"))))
                            .code());
            assertEquals(0, catalog.count());
            assertTrue(catalog.byId("not-found").isEmpty());
            catalog.append(
                    fact(
                            "web.task.accepted",
                            Map.of(
                                    "taskId",
                                    "web-task",
                                    "profile",
                                    "local",
                                    "requestHash",
                                    hash,
                                    "goalHash",
                                    hash,
                                    "workspaceBinding",
                                    "binding")));
            catalog.append(
                    fact(
                            "web.task.finished",
                            Map.of(
                                    "taskId",
                                    "web-task",
                                    "phase",
                                    "UNKNOWN",
                                    "status",
                                    "UNKNOWN",
                                    "error",
                                    "RECOVERY_REQUIRED")));
            assertEquals("UNKNOWN", catalog.byId("web-task").getLast().payload().get("status"));
            assertEquals(1, catalog.count());
        }
    }

    @Test
    void startedQueueDropsExecutableTextAndRejectsNullOutcome() {
        var queue = new TurnQueue();
        queue.apply(
                fact(
                        "web.turn.accepted",
                        Map.of(
                                "requestHash",
                                hash,
                                "goalHash",
                                hash,
                                "kind",
                                "message",
                                "content",
                                "private execution text")));
        queue.apply(fact("web.turn.started", Map.of("requestHash", hash)));
        assertEquals("", queue.inputs.get(hash).content);
        assertEquals("UNKNOWN", queue.inputs.get(hash).state);
        var p = new HashMap<String, Object>();
        p.put("requestHash", hash);
        p.put("status", null);
        assertEquals(
                "INVALID_WEB_CATALOG",
                assertThrows(
                                HarnessException.class,
                                () -> queue.apply(fact("web.turn.finished", p)))
                        .code());
        assertEquals("UNKNOWN", queue.inputs.get(hash).state);
    }

    @Test
    void queueLifetimeAndPendingLimitsAreBoundedWithoutDroppingIdempotence() {
        var queue = new TurnQueue();
        for (int i = 0; i < 256; i++) {
            String id = String.format("%064x", i);
            queue.apply(
                    fact(
                            "web.turn.accepted",
                            Map.of(
                                    "requestHash",
                                    id,
                                    "goalHash",
                                    hash,
                                    "kind",
                                    "message",
                                    "content",
                                    "text")));
            queue.apply(
                    fact(
                            "web.turn.finished",
                            Map.of(
                                    "requestHash",
                                    id,
                                    "status",
                                    "COMPLETED",
                                    "turnId",
                                    "turn-" + i)));
        }
        assertEquals(0, queue.pending());
        assertEquals(256, queue.inputs.size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        queue.apply(
                                fact(
                                        "web.turn.accepted",
                                        Map.of(
                                                "requestHash",
                                                hash,
                                                "goalHash",
                                                hash,
                                                "kind",
                                                "message",
                                                "content",
                                                "rejected"))));
        assertEquals(256, queue.page(0).get("total"));
        assertEquals(32, ((List<?>) queue.page(0).get("submissions")).size());
    }

    @Test
    void oversizedLeaseIsRejectedAndOwnershipReleasedForDiagnosis() throws Exception {
        var repo = new JsonlSessionRepository(root);
        Files.write(root.resolve("web-workspace-owner.json"), new byte[65537]);
        assertThrows(java.io.IOException.class, () -> new WorkspaceLease(repo));
        try (var owner = repo.openWriter(WorkspaceLease.LOCK, WriteOwnership.EXCLUSIVE)) {
            owner.flush();
        }
    }

    @Test
    void malformedLeaseIdentityNeverPersistsAndValidLeaseReopensExactly() throws Exception {
        var repo = new JsonlSessionRepository(root);
        try (var lease = new WorkspaceLease(repo)) {
            assertEquals(
                    "INVALID_WORKSPACE_LEASE",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            lease.accept(
                                                    Map.of("taskId", "../bad", "requestHash", hash),
                                                    hash))
                            .code());
            assertFalse(Files.exists(root.resolve("web-workspace-owner.json")));
            lease.accept(Map.of("taskId", "web-task", "requestHash", hash), hash);
        }
        try (var lease = new WorkspaceLease(repo)) {
            assertEquals("web-task", lease.pending().get("taskId"));
            lease.clear("web-task", hash);
            assertTrue(lease.pending().isEmpty());
        }
    }
}
