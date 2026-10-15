package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M5SessionTest {
    @TempDir Path root;

    private SessionEvent fact(
            String id, String type, EventLevel level, String call, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                id,
                type,
                Instant.parse("2026-10-14T00:00:00Z"),
                1,
                level,
                level == EventLevel.SESSION ? null : "turn",
                level == EventLevel.STEP || level == EventLevel.TOOL ? "step" : null,
                call,
                payload);
    }

    private List<SessionEvent> history(String id) {
        return List.of(
                SessionEvent.session(id, "session.created", Map.of("workspace", "fixture")),
                fact(
                        id,
                        "user.message",
                        EventLevel.TURN,
                        null,
                        Map.of("role", "user", "content", "hello")),
                fact(
                        id,
                        "request.context",
                        EventLevel.STEP,
                        null,
                        Map.of(
                                "messages",
                                List.of(Map.of("role", "user", "content", "hello")),
                                "tools",
                                List.of(),
                                "parameters",
                                Map.of("model", "fixture"))));
    }

    private void save(SessionRepository repo, String id, List<SessionEvent> events) {
        try (SessionWriter writer = repo.openWriter(id, WriteOwnership.EXCLUSIVE)) {
            events.forEach(writer::append);
        }
    }

    private String code(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(HarnessException.class, action).code();
    }

    @Test
    void diskAndMemoryShareQueryForkAndRequestReconstructionContract() throws Exception {
        for (SessionRepository repo :
                List.of(new InMemorySessionRepository(), new JsonlSessionRepository(root))) {
            List<SessionEvent> original = new ArrayList<>(history("parent"));
            SessionEvent requested =
                    fact(
                            "parent",
                            "model.requested",
                            EventLevel.STEP,
                            null,
                            Map.of("contextEventId", original.getLast().eventId()));
            original.add(requested);
            save(repo, "parent", original);
            String child = repo.fork("parent", requested.eventId());
            var inherited = repo.openReader(child).events();
            assertEquals(
                    new EventRequestJournal().reconstruct(original, requested.eventId()),
                    new EventRequestJournal().reconstruct(inherited, requested.eventId()));
            assertEquals(
                    new EventSessionProjection(original).messages(),
                    new EventSessionProjection(inherited).messages());
            assertTrue(inherited.stream().allMatch(e -> child.equals(e.sessionId())));
            save(repo, child, List.of(SessionEvent.session(child, "child.only", Map.of())));
            save(repo, "parent", List.of(SessionEvent.session("parent", "parent.only", Map.of())));
            assertTrue(
                    repo.openReader("parent").events().stream()
                            .noneMatch(e -> e.type().equals("child.only")));
            assertTrue(
                    repo.openReader(child).events().stream()
                            .noneMatch(e -> e.type().equals("parent.only")));
            assertEquals(
                    1,
                    repo.openReader(child)
                            .query(
                                    new SessionQuery(
                                            null, null, "model.requested", null, null, 0, 10))
                            .size());
        }
    }

    @Test
    void maintenanceAndForkRefuseActiveWriterAndReleaseLocksAfterFailure() throws Exception {
        var repo = new JsonlSessionRepository(root);
        save(repo, "s", history("s"));
        String event = repo.openReader("s").events().getFirst().eventId();
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.flush();
            assertEquals("WRITE_CONFLICT", code(() -> repo.fork("s", event)));
            assertEquals("WRITE_CONFLICT", code(() -> repo.repairTail("s")));
            assertEquals("WRITE_CONFLICT", code(() -> repo.migrate("s")));
            assertEquals("WRITE_CONFLICT", code(() -> repo.export("s", root.resolve("out"))));
        }
        assertEquals("EVENT_NOT_FOUND", code(() -> repo.fork("s", "absent")));
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.flush();
        }
        assertEquals("ALREADY_VALID", repo.repairTail("s"));
    }

    @Test
    void migrationPreservesEveryEventProjectionRequestAndOriginalBackup() throws Exception {
        var repo = new JsonlSessionRepository(root);
        var events = new ArrayList<>(history("s"));
        events.add(
                fact(
                        "s",
                        "model.requested",
                        EventLevel.STEP,
                        null,
                        Map.of("contextEventId", events.getLast().eventId())));
        save(repo, "s", events);
        Path file = root.resolve("s.jsonl");
        Files.writeString(file, Files.readString(file).replace("\"version\":1", "\"version\":0"));
        byte[] before = Files.readAllBytes(file);
        assertEquals("MIGRATION_REQUIRED", repo.inspect("s").diagnostic());
        String backup = repo.migrate("s");
        assertArrayEquals(before, Files.readAllBytes(root.resolve(backup)));
        var cold = new JsonlSessionRepository(root).openReader("s").events();
        assertEquals(events, cold);
        assertEquals(
                new EventSessionProjection(events).messages(),
                new EventSessionProjection(cold).messages());
        assertEquals(
                new EventRequestJournal().reconstruct(events, events.getLast().eventId()),
                new EventRequestJournal().reconstruct(cold, events.getLast().eventId()));
    }

    @Test
    void tailRepairBacksUpTruncatedAndInvalidCompleteFinalRecords() throws Exception {
        for (String tail : List.of("{broken", "{broken}\n")) {
            var repo = new JsonlSessionRepository(root);
            save(repo, "s", history("s"));
            var original = repo.openReader("s").events();
            Files.writeString(root.resolve("s.jsonl"), tail, StandardOpenOption.APPEND);
            byte[] damaged = Files.readAllBytes(root.resolve("s.jsonl"));
            assertEquals("TAIL_DAMAGED", code(() -> repo.openReader("s").events()));
            String backup = repo.repairTail("s");
            assertArrayEquals(damaged, Files.readAllBytes(root.resolve(backup)));
            assertEquals(original, new JsonlSessionRepository(root).openReader("s").events());
            Files.delete(root.resolve("s.jsonl"));
        }
    }

    @Test
    void middleDamageAndFutureVersionsNeverChangeBytesOrCreateFork() throws Exception {
        var repo = new JsonlSessionRepository(root);
        save(repo, "s", history("s"));
        Path file = root.resolve("s.jsonl");
        String valid = Files.readString(file);
        for (String damaged :
                List.of(
                        valid.replace("\"version\":1", "\"version\":2"),
                        valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                        valid.substring(0, valid.indexOf('\n') + 1)
                                + "{bad}\n"
                                + valid.substring(valid.indexOf('\n') + 1))) {
            Files.writeString(file, damaged);
            byte[] before = Files.readAllBytes(file);
            assertThrows(HarnessException.class, () -> repo.repairTail("s"));
            assertThrows(HarnessException.class, () -> repo.migrate("s"));
            assertThrows(HarnessException.class, () -> repo.fork("s", "absent"));
            assertArrayEquals(before, Files.readAllBytes(file));
            assertEquals(List.of("s"), repo.list());
        }
    }

    @Test
    void migrationFailureBeforeAtomicPublishLeavesSourceUntouched() throws Exception {
        var repo = new JsonlSessionRepository(root);
        save(repo, "s", history("s"));
        Path file = root.resolve("s.jsonl");
        Files.writeString(file, Files.readString(file).replace("\"version\":1", "\"version\":0"));
        byte[] before = Files.readAllBytes(file);
        int[] forces = {0};
        var failing =
                new JsonlSessionRepository(
                        root,
                        JsonlSessionRepository.TailPolicy.REJECT,
                        c -> {
                            if (++forces[0] == 2) throw new IOException("backup force fails");
                            c.force(true);
                        },
                        ignored -> {});
        assertEquals("SESSION_MAINTENANCE_FAILED", code(() -> failing.migrate("s")));
        assertArrayEquals(before, Files.readAllBytes(file));
        repo.migrate("s");
        assertEquals(3, repo.openReader("s").events().size());
    }

    @Test
    void exportReopensAsColdRepositoryAndNeverOverwrites() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("source"));
        save(repo, "s", history("s"));
        Path target = Files.createDirectories(root.resolve("export")).resolve("s.jsonl");
        repo.export("s", target);
        assertEquals(
                repo.openReader("s").events(),
                new JsonlSessionRepository(target.getParent()).openReader("s").events());
        byte[] before = Files.readAllBytes(target);
        assertEquals("SESSION_EXPORT_FAILED", code(() -> repo.export("s", target)));
        assertArrayEquals(before, Files.readAllBytes(target));
    }

    @Test
    void forkCopiesColdArtifactsAndDetectsTamperingBeforePublication() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var artifacts = new LocalArtifactStore(root.resolve("artifacts"), data -> data);
        var ref = artifacts.put("s", "冷产物\n" + "a".repeat(1000));
        var event = SessionEvent.session("s", "context.injected", Map.of("artifactId", ref.id()));
        save(repo, "s", List.of(event));
        String child =
                repo.fork(
                        "s",
                        event.eventId(),
                        target -> {
                            try {
                                artifacts.copyScope("s", target);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
        var cold = new LocalArtifactStore(root.resolve("artifacts"), data -> data);
        assertEquals("冷产物\n", cold.read(child, ref.id(), 0, 4));
        Files.writeString(root.resolve("artifacts/s/" + ref.id() + ".txt"), "tampered");
        assertEquals("ARTIFACT_CORRUPT", code(() -> cold.read("s", ref.id(), 0, 10)));
        assertEquals("冷产物\n", cold.read(child, ref.id(), 0, 4));
        assertEquals(
                "ARTIFACT_CORRUPT",
                code(
                        () ->
                                repo.fork(
                                        "s",
                                        event.eventId(),
                                        target -> {
                                            try {
                                                artifacts.copyScope("s", target);
                                            } catch (IOException ex) {
                                                throw new RuntimeException(ex);
                                            }
                                        })));
        assertEquals(2, repo.list().size());
    }

    @Test
    void recoveryCarriesUnknownAndUnresolvedRequestsAcrossForkAndReopen() throws Exception {
        var repo = new JsonlSessionRepository(root);
        var events = new ArrayList<>(history("s"));
        var request =
                fact(
                        "s",
                        "model.requested",
                        EventLevel.STEP,
                        null,
                        Map.of("contextEventId", events.getLast().eventId()));
        events.add(request);
        events.add(
                fact(
                        "s",
                        "tool.intent",
                        EventLevel.TOOL,
                        "call",
                        Map.of("sideEffectLevel", "WORKSPACE_WRITE")));
        save(repo, "s", events);
        String child = repo.fork("s", events.getLast().eventId());
        var recovery =
                SessionRecovery.inspect(
                        new JsonlSessionRepository(root).openReader(child).events());
        assertFalse(recovery.ready());
        assertEquals(List.of("call"), recovery.uncertainCalls());
        assertEquals(List.of("step"), recovery.pendingRequests());
        save(
                repo,
                child,
                List.of(
                        fact(
                                child,
                                "tool.verified",
                                EventLevel.TOOL,
                                "call",
                                Map.of("outcome", "NOT_APPLIED")),
                        SessionEvent.session(
                                child,
                                "model.verified",
                                Map.of("stepId", "step", "sourceEvent", request.eventId()))));
        assertTrue(SessionRecovery.inspect(repo.openReader(child).events()).ready());
        assertFalse(SessionRecovery.inspect(repo.openReader("s").events()).ready());
    }

    @Test
    void catalogFiltersColdSessionsAndReportsDamageWithoutBuildingAnIndex() throws Exception {
        var repo = new JsonlSessionRepository(root);
        save(repo, "s", history("s"));
        save(
                repo,
                "other",
                List.of(
                        SessionEvent.session(
                                "other", "session.created", Map.of("workspace", "other"))));
        Files.writeString(root.resolve("broken.jsonl"), "broken\n");
        assertEquals(
                3, SessionCatalog.query(repo, null, null, null, null, null, null, 0, 100).size());
        assertEquals(
                "s",
                SessionCatalog.query(
                                repo, "fixture", "fixture", null, null, null, "CREATED", 0, 100)
                        .getFirst()
                        .get("session"));
        assertTrue(
                SessionCatalog.query(repo, "absent", null, null, null, null, null, 0, 100)
                        .isEmpty());
        assertEquals(
                1, SessionCatalog.query(repo, null, null, null, null, null, null, 1, 1).size());
        assertThrows(
                HarnessException.class,
                () -> new SessionQuery(null, null, null, null, null, -1, 1));
        assertThrows(
                HarnessException.class,
                () -> new SessionQuery(null, null, null, null, null, 0, 1001));
    }

    @Test
    void eventQueryHasInclusiveTimesExactToolStatusAndStablePagination() throws Exception {
        var repo = new JsonlSessionRepository(root);
        var event =
                fact(
                        "s",
                        "tool.requested",
                        EventLevel.TOOL,
                        "call",
                        Map.of("tool", "read_file", "status", "PENDING"));
        save(repo, "s", List.of(event, SessionEvent.session("s", "other", Map.of())));
        assertEquals(
                List.of(event),
                repo.openReader("s")
                        .query(
                                new SessionQuery(
                                        event.timestamp(),
                                        event.timestamp(),
                                        "tool.requested",
                                        "read_file",
                                        "PENDING",
                                        0,
                                        10)));
        assertTrue(
                repo.openReader("s")
                        .query(new SessionQuery(null, null, null, "write_file", null, 0, 10))
                        .isEmpty());
        assertTrue(
                repo.openReader("s")
                        .query(new SessionQuery(null, null, "tool.requested", null, null, 1, 1))
                        .isEmpty());
        assertThrows(
                HarnessException.class,
                () ->
                        new SessionQuery(
                                event.timestamp().plusSeconds(1),
                                event.timestamp(),
                                null,
                                null,
                                null,
                                0,
                                1));
    }
}
