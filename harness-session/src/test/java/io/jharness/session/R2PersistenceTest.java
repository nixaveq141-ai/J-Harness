package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2PersistenceTest {
    @TempDir Path root;

    @Test
    void replacedArtifactRootIdentityIsRejectedBeforeScopeCreation() throws Exception {
        Path directory = root.resolve("artifacts");
        var artifacts = new LocalArtifactStore(directory, new SecretRedactor());
        Files.move(directory, root.resolve("old-artifacts"), StandardCopyOption.ATOMIC_MOVE);
        Files.createDirectory(directory);
        assertEquals(
                "ARTIFACT_DENIED",
                assertThrows(
                                HarnessException.class,
                                () -> artifacts.put("child", "must not be written"))
                        .code());
        assertFalse(Files.exists(directory.resolve("child")));
    }

    @Test
    void terminalVerificationRejectsStaleSnapshotsButNewObservedActivityRemainsUnknown() {
        var snapshot =
                SessionEvent.session(
                        "s",
                        "terminal.snapshot",
                        Map.of("tasks", List.of(Map.of("id", "terminal", "state", "UNKNOWN"))));
        var verified = SessionEvent.session("s", "terminal.verified", Map.of("id", "terminal"));
        assertTrue(
                SessionRecovery.inspect(
                                List.of(
                                        snapshot,
                                        verified,
                                        SessionEvent.session(
                                                "s", "terminal.snapshot", snapshot.payload())))
                        .ready());
        var changed =
                SessionEvent.session(
                        "s",
                        "terminal.snapshot",
                        Map.of(
                                "tasks",
                                List.of(
                                        Map.of(
                                                "id",
                                                "terminal",
                                                "state",
                                                "UNKNOWN",
                                                "generation",
                                                2))));
        assertFalse(SessionRecovery.inspect(List.of(snapshot, verified, changed)).ready());
        var active =
                new SessionEvent(
                        "activity",
                        "s",
                        "tool.completed",
                        Instant.EPOCH,
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "c",
                        Map.of(
                                "value",
                                Map.of("id", "terminal", "state", "RUNNING", "pty", false)));
        assertFalse(SessionRecovery.inspect(List.of(snapshot, verified, active)).ready());
    }

    @Test
    void ambiguousResponsesCannotSettleTwoUnresolvedAttempts() {
        var a =
                new SessionEvent(
                        "a",
                        "s",
                        "model.requested",
                        Instant.EPOCH,
                        1,
                        EventLevel.STEP,
                        "t",
                        "p",
                        null,
                        Map.of());
        var b =
                new SessionEvent(
                        "b",
                        "s",
                        "model.requested",
                        Instant.EPOCH,
                        1,
                        EventLevel.STEP,
                        "t",
                        "p",
                        null,
                        Map.of());
        var reply =
                new SessionEvent(
                        "reply",
                        "s",
                        "assistant.message",
                        Instant.EPOCH,
                        1,
                        EventLevel.STEP,
                        "t",
                        "p",
                        null,
                        Map.of("role", "assistant", "content", "ambiguous"));
        assertEquals(
                "AMBIGUOUS_MODEL_REQUEST",
                assertThrows(
                                HarnessException.class,
                                () -> SessionRecovery.inspect(List.of(a, b, reply)))
                        .code());
        assertTrue(SessionRecovery.isRequestPending(List.of(a, b), "a"));
        assertTrue(SessionRecovery.isRequestPending(List.of(a, b), "b"));
    }

    private String seed(JsonlSessionRepository repository, String id) {
        try (var writer = repository.openWriter(id, WriteOwnership.EXCLUSIVE)) {
            return writer.append(SessionEvent.session(id, "session.created", Map.of()));
        }
    }

    @Test
    void existingAndOwnedForkDestinationsNeverChangeOrPrepareArtifacts() throws Exception {
        var repo = new JsonlSessionRepository(root);
        String event = seed(repo, "source");
        seed(repo, "target");
        byte[] original = Files.readAllBytes(root.resolve("target.jsonl"));
        var prepared = new AtomicBoolean();
        assertEquals(
                "SESSION_EXISTS",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        repo.forkTo(
                                                "source",
                                                event,
                                                "target",
                                                id -> prepared.set(true)))
                        .code());
        try (var owner = repo.openWriter("busy", WriteOwnership.EXCLUSIVE)) {
            owner.flush();
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            repo.forkTo(
                                                    "source",
                                                    event,
                                                    "busy",
                                                    id -> prepared.set(true)))
                            .code());
        }
        assertFalse(prepared.get());
        assertArrayEquals(original, Files.readAllBytes(root.resolve("target.jsonl")));
    }

    @Test
    void destinationOwnershipIsHeldThroughoutPreparationAcrossProcesses() throws Exception {
        var repo = new JsonlSessionRepository(root);
        String event = seed(repo, "source");
        repo.forkTo(
                "source",
                event,
                "child",
                id -> {
                    Process child = null;
                    try {
                        String java =
                                Path.of(System.getProperty("java.home"), "bin", "java").toString();
                        child =
                                new ProcessBuilder(
                                                java,
                                                "-cp",
                                                System.getProperty("java.class.path"),
                                                WriterProbe.class.getName(),
                                                root.toString(),
                                                id)
                                        .redirectErrorStream(true)
                                        .start();
                        assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                        assertEquals(23, child.exitValue());
                        assertEquals(
                                "WRITE_CONFLICT",
                                new String(child.getInputStream().readAllBytes()).strip());
                        assertFalse(Files.exists(root.resolve("child.jsonl")));
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    } finally {
                        if (child != null && child.isAlive()) child.destroyForcibly();
                    }
                });
        assertEquals(2, repo.openReader("child").events().size());
    }

    @Test
    void eventBudgetIsCheckedBeforeArtifactPreparation() throws Exception {
        var repo = new JsonlSessionRepository(root);
        String last = "";
        try (var writer = repo.openWriter("source", WriteOwnership.EXCLUSIVE)) {
            for (int i = 0; i < 10000; i++)
                last = writer.append(SessionEvent.session("source", "fact", Map.of()));
        }
        String end = last;
        var prepared = new AtomicBoolean();
        assertEquals(
                "SESSION_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> repo.forkTo("source", end, "child", id -> prepared.set(true)))
                        .code());
        assertFalse(prepared.get());
        assertFalse(repo.list().contains("child"));
    }

    @Test
    void failedArtifactCopyPublishesNoPartialScopeAndValidCopyIsIndependent() throws Exception {
        var artifacts = new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor());
        var first = artifacts.put("source", "中文😀first");
        var second = artifacts.put("source", "second");
        Path corrupted = root.resolve("artifacts/source").resolve(second.id() + ".txt");
        Files.writeString(corrupted, "tampered");
        assertEquals(
                "ARTIFACT_CORRUPT",
                assertThrows(HarnessException.class, () -> artifacts.copyScope("source", "child"))
                        .code());
        assertFalse(Files.exists(root.resolve("artifacts/child")));
        Files.writeString(corrupted, "second");
        artifacts.copyScope("source", "child");
        Files.delete(root.resolve("artifacts/source").resolve(first.id() + ".txt"));
        assertEquals("中文😀first", artifacts.readText("child", first.id()));
        assertEquals(
                "ARTIFACT_SCOPE_EXISTS",
                assertThrows(HarnessException.class, () -> artifacts.copyScope("source", "child"))
                        .code());
        assertEquals(
                "ARTIFACT_SCOPE_EXISTS",
                assertThrows(HarnessException.class, () -> artifacts.copyScope("absent", "child"))
                        .code());
        assertEquals("中文😀first", artifacts.readText("child", first.id()));
        assertEquals(
                "INVALID_SESSION_ID",
                assertThrows(
                                HarnessException.class,
                                () -> artifacts.copyScope("../absent", "another"))
                        .code());
    }

    @Test
    void logPublicationFailureCompensatesOnlyNewlyCopiedScope() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        String event = seed(repo, "source");
        var artifacts = new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor());
        var artifact = artifacts.put("source", "kept");
        assertEquals(
                "SESSION_PATH_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        repo.forkTo(
                                                "source",
                                                event,
                                                "child",
                                                id -> {
                                                    try {
                                                        artifacts.copyScope("source", id);
                                                        Files.createDirectory(
                                                                root.resolve(
                                                                        "sessions/child.jsonl"));
                                                    } catch (Exception ex) {
                                                        throw new AssertionError(ex);
                                                    }
                                                },
                                                id -> {
                                                    try {
                                                        artifacts.rollbackCopy(id);
                                                    } catch (Exception ex) {
                                                        throw new AssertionError(ex);
                                                    }
                                                }))
                        .code());
        assertFalse(Files.exists(root.resolve("artifacts/child")));
        assertEquals("kept", artifacts.readText("source", artifact.id()));
    }

    @Test
    void archiveRestoreCatalogAndMissingLifecycleHaveOneMeaning() throws Exception {
        var repo = new JsonlSessionRepository(root);
        seed(repo, "s");
        SessionLifecycle.setArchived(repo, "s", true, new SecretRedactor());
        SessionLifecycle.setArchived(repo, "s", false, new SecretRedactor());
        var rows = SessionCatalog.query(repo, null, null, null, null, null, null, 0, 10);
        assertEquals(false, rows.getFirst().get("archived"));
        assertEquals(
                "SESSION_NOT_FOUND",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SessionLifecycle.setArchived(
                                                repo, "absent", true, new SecretRedactor()))
                        .code());
        assertFalse(Files.exists(root.resolve("absent.jsonl")));
        assertFalse(Files.exists(root.resolve("absent.lock")));
    }

    @Test
    void malformedTimestampIsTailDamageButUnsupportedSchemaCannotBeRepairedAway() throws Exception {
        var repo = new JsonlSessionRepository(root);
        seed(repo, "s");
        Path log = root.resolve("s.jsonl");
        String original = Files.readString(log);
        String damaged =
                original.replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"bad-time\"");
        Files.writeString(log, damaged);
        assertEquals(
                "TAIL_DAMAGED",
                assertThrows(HarnessException.class, () -> repo.openReader("s").events()).code());
        Files.writeString(log, original.replace("\"schemaVersion\":1", "\"schemaVersion\":0"));
        assertEquals(
                "UNSUPPORTED_EVENT_SCHEMA",
                assertThrows(HarnessException.class, () -> repo.repairTail("s")).code());
    }

    @Test
    void orphanContextIsNotComposedAndMalformedCoreMessagesFailClosed() {
        var context =
                new SessionEvent(
                        "context",
                        "s",
                        "request.context",
                        Instant.EPOCH,
                        1,
                        EventLevel.STEP,
                        "t",
                        "p",
                        null,
                        Map.of(
                                "messages",
                                List.of(Map.of("role", "user", "content", "uncommitted envelope")),
                                "tools",
                                List.of(),
                                "parameters",
                                Map.of()));
        assertTrue(new EventSessionProjection(List.of(context)).messages().isEmpty());
        assertEquals(
                "INVALID_EVENT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SessionRecovery.inspect(
                                                List.of(
                                                        SessionEvent.session(
                                                                "s",
                                                                "context.injected",
                                                                Map.of("content", "bad")))))
                        .code());
        var call =
                new SessionEvent(
                        "request",
                        "s",
                        "approval.requested",
                        Instant.EPOCH,
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "c",
                        Map.of());
        assertFalse(SessionRecovery.inspect(List.of(call)).ready());
        assertEquals(List.of("c"), SessionRecovery.inspect(List.of(call)).unsettledCalls());
    }
}
