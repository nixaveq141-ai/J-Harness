package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class InMemorySessionTest {
    @Test
    void writerIsExclusiveReadersSeeSnapshotsAndClosedWriterCannotReenter() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        SessionWriter first = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
        SessionReader reader = repo.openReader("s");
        List<SessionEvent> before = reader.events();
        SessionEvent created = SessionEvent.session("s", "session.created", Map.of());
        assertEquals(created.eventId(), first.append(created));
        first.flush();
        assertTrue(before.isEmpty());
        assertEquals(List.of(created), reader.events());
        assertEquals(
                "WRITE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () -> repo.openWriter("s", WriteOwnership.EXCLUSIVE))
                        .code());
        first.close();
        first.close();
        try (SessionWriter replacement = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            assertEquals(
                    "WRITER_CLOSED", assertThrows(HarnessException.class, first::flush).code());
            assertEquals(
                    "WRITER_CLOSED",
                    assertThrows(HarnessException.class, () -> first.append(created)).code());
            first.close(); // Stale close must not release replacement ownership.
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> repo.openWriter("s", WriteOwnership.EXCLUSIVE))
                            .code());
            replacement.append(SessionEvent.session("s", "extension", Map.of()));
        }
        assertEquals(2, reader.events().size());
    }

    @Test
    void concurrentWriterOpenHasExactlyOneWinner() throws Exception {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<SessionWriter> attempt =
                    () -> {
                        ready.countDown();
                        start.await();
                        try {
                            return repo.openWriter("s", WriteOwnership.EXCLUSIVE);
                        } catch (HarnessException ex) {
                            assertEquals("WRITE_CONFLICT", ex.code());
                            return null;
                        }
                    };
            Future<SessionWriter> one = workers.submit(attempt);
            Future<SessionWriter> two = workers.submit(attempt);
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            SessionWriter a = one.get(2, TimeUnit.SECONDS);
            SessionWriter b = two.get(2, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null));
            SessionWriter winner = a != null ? a : b;
            winner.close();
        } finally {
            start.countDown();
        }
    }

    @Test
    void rejectWrongSessionDuplicatesAndFutureSchemaWithoutAppending() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            SessionEvent event = SessionEvent.session("s", "session.created", Map.of());
            writer.append(event);
            assertEquals(
                    "DUPLICATE_EVENT",
                    assertThrows(HarnessException.class, () -> writer.append(event)).code());
            assertEquals(
                    "SESSION_MISMATCH",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            writer.append(
                                                    SessionEvent.session(
                                                            "other", "session.created", Map.of())))
                            .code());
            SessionEvent future =
                    new SessionEvent(
                            "future",
                            "s",
                            "session.created",
                            Instant.EPOCH,
                            2,
                            EventLevel.SESSION,
                            null,
                            null,
                            null,
                            Map.of());
            assertEquals(
                    "FUTURE_VERSION",
                    assertThrows(HarnessException.class, () -> writer.append(future)).code());
            assertEquals(1, repo.openReader("s").events().size());
        }
        assertEquals(
                "SESSION_NOT_FOUND",
                assertThrows(HarnessException.class, () -> repo.openReader("missing")).code());
        assertEquals(
                "EVENT_NOT_FOUND",
                assertThrows(HarnessException.class, () -> repo.fork("s", "event")).code());
    }

    @Test
    void replayProjectionIsStableAndHasNoSeparateAuthoritativeHistory() {
        List<SessionEvent> events =
                List.of(
                        SessionEvent.session("s", "session.created", Map.of()),
                        scoped("turn.started", EventLevel.TURN, Map.of()),
                        SessionEvent.session(
                                "s", "user.message", Map.of("role", "user", "content", "hello")),
                        scoped(
                                "assistant.message",
                                EventLevel.STEP,
                                Map.of("role", "assistant", "content", "response")),
                        scoped("tool.requested", EventLevel.TOOL, Map.of("tool", "read")),
                        scoped("approval.decided", EventLevel.TOOL, Map.of("decision", "ALLOW")),
                        scoped("tool.started", EventLevel.TOOL, Map.of()),
                        scoped("tool.completed", EventLevel.TOOL, Map.of("status", "SUCCESS")),
                        scoped("turn.ended", EventLevel.TURN, Map.of("status", "COMPLETED")));
        SessionProjection one = new EventSessionProjection(events);
        SessionProjection two = new EventSessionProjection(events);
        assertEquals(one.messages(), two.messages());
        assertEquals(one.toolHistory(), two.toolHistory());
        assertEquals(one.approvals(), two.approvals());
        assertEquals(TaskStatus.COMPLETED, one.taskStatus());
        assertEquals(2, one.messages().size());
        assertEquals(3, one.toolHistory().size());
        assertEquals(1, one.approvals().size());
        assertThrows(UnsupportedOperationException.class, () -> one.messages().clear());
        assertEquals(
                TaskStatus.RUNNING,
                new EventSessionProjection(events.subList(0, events.size() - 1)).taskStatus());
    }

    @Test
    void malformedProjectionDoesNotPretendToHaveCompleted() {
        assertEquals(
                "INVALID_EVENT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new EventSessionProjection(
                                                List.of(
                                                        scoped(
                                                                "turn.ended",
                                                                EventLevel.TURN,
                                                                Map.of("status", "RUNNING")))))
                        .code());
        assertEquals(
                "INVALID_EVENT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new EventSessionProjection(
                                                List.of(
                                                        scoped(
                                                                "turn.ended",
                                                                EventLevel.TURN,
                                                                Map.of()))))
                        .code());
        assertEquals(
                "SESSION_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new EventSessionProjection(
                                                List.of(
                                                        SessionEvent.session(
                                                                "a", "session.created", Map.of()),
                                                        SessionEvent.session(
                                                                "b", "session.created", Map.of()))))
                        .code());
    }

    @Test
    void checkpointPolicyRequiresDurabilityForSideEffectsAndMemoryPolicyIsExplicit() {
        CheckpointPolicy policy = new DefaultCheckpointPolicy();
        SessionState state = new SessionState("s", TaskStatus.RUNNING);
        assertEquals(CheckpointDecision.DURABLE_FLUSH, policy.beforeEachModelRequest(state));
        assertEquals(CheckpointDecision.DURABLE_FLUSH, policy.atStepBoundary(state));
        for (SideEffectLevel level : SideEffectLevel.values()) {
            ToolInvocation call = new ToolInvocation("c", "tool", Map.of(), level);
            assertEquals(
                    level == SideEffectLevel.READ_ONLY
                            ? CheckpointDecision.FLUSH
                            : CheckpointDecision.DURABLE_FLUSH,
                    policy.beforeTopLevelTool(call));
            assertEquals(
                    CheckpointDecision.MEMORY_ONLY,
                    new MemoryCheckpointPolicy().beforeTopLevelTool(call));
        }
    }

    private static SessionEvent scoped(String type, EventLevel level, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                "s",
                type,
                Instant.EPOCH,
                1,
                level,
                "t",
                level == EventLevel.TURN ? null : "p",
                level == EventLevel.TOOL ? "c" : null,
                payload);
    }
}
