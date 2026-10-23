package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EventRequestJournalTest {
    private static EventBus bus() {
        return new EventBus() {
            @Override
            public void publish(SessionEvent e) {}

            @Override
            public Registration notify(String t, NotificationListener l) {
                return () -> {};
            }

            @Override
            public Registration serial(String t, SerialListener l, int p, java.time.Duration d) {
                return () -> {};
            }

            @Override
            public void publishSerial(SessionEvent e) {}

            @Override
            public <T> Registration intercept(ExtensionPoint<T> p, Interceptor<T> i, int n) {
                return () -> {};
            }

            @Override
            public <T> T invoke(ExtensionPoint<T> p, T r, Interceptor.Next<T> next) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void anyRequestReconstructsFromItsPrefixEvenAfterLaterContextChanges() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        EventRequestJournal journal = new EventRequestJournal();
        ChatRequest first = request("first", 0.2);
        ChatRequest second = request("second", 0.7);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            String one = journal.record(first, "s", "t", "p1", writer, bus());
            String two = journal.record(second, "s", "t", "p2", writer, bus());
            List<SessionEvent> events = repo.openReader("s").events();
            assertEquals(first, journal.reconstruct(events.subList(0, 2), one));
            assertEquals(first, journal.reconstruct(events, one));
            assertEquals(second, journal.reconstruct(events, two));
            assertEquals(second.messages(), new EventSessionProjection(events).messages());
        }
    }

    @Test
    void missingFutureOrWrongStepContextCannotBeUsed() {
        EventRequestJournal journal = new EventRequestJournal();
        SessionEvent target =
                event("request", "model.requested", "p", Map.of("contextEventId", "later"));
        SessionEvent later =
                event(
                        "later",
                        "request.context",
                        "p",
                        Map.of("messages", List.of(), "tools", List.of(), "parameters", Map.of()));
        assertEquals(
                "REQUEST_CONTEXT_MISSING",
                assertThrows(
                                HarnessException.class,
                                () -> journal.reconstruct(List.of(target, later), "request"))
                        .code());
        SessionEvent wrong = event("later", "request.context", "other-step", later.payload());
        assertEquals(
                "REQUEST_CONTEXT_MISSING",
                assertThrows(
                                HarnessException.class,
                                () -> journal.reconstruct(List.of(wrong, target), "request"))
                        .code());
        assertEquals(
                "REQUEST_NOT_FOUND",
                assertThrows(
                                HarnessException.class,
                                () -> journal.reconstruct(List.of(later), "absent"))
                        .code());
        assertEquals(
                "INVALID_EVENT",
                assertThrows(
                                HarnessException.class,
                                () -> journal.reconstruct(List.of(later), "later"))
                        .code());
    }

    @Test
    void toolResultsAndFinalEnvelopeReplayIntoMessagesWithoutAuthorityDuplication() {
        ChatRequest request = request("task", 0.1);
        SessionEvent context =
                event(
                        "ctx",
                        "request.context",
                        "p",
                        Map.of(
                                "messages",
                                request.messages(),
                                "tools",
                                request.tools(),
                                "parameters",
                                request.parameters()));
        SessionEvent assistant =
                event(
                        "a",
                        "assistant.message",
                        "p",
                        Map.of("role", "assistant", "content", "call"));
        SessionEvent result =
                new SessionEvent(
                        "r",
                        "s",
                        "tool.completed",
                        Instant.EPOCH,
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "c",
                        Map.of("content", "DENIED", "status", "DENIED"));
        SessionProjection projection =
                new EventSessionProjection(
                        List.of(
                                context,
                                event(
                                        "request",
                                        "model.requested",
                                        "p",
                                        Map.of("contextEventId", "ctx")),
                                assistant,
                                result));
        assertEquals(4, projection.messages().size());
        assertEquals(
                Map.of("role", "tool", "tool_call_id", "c", "content", "DENIED"),
                projection.messages().getLast());
    }

    @Test
    void allInputFactsAreSanitizedAndDurabilityDoesNotImproveByWrapping() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        try (SessionWriter writer =
                new SanitizingSessionWriter(
                        repo.openWriter("s", WriteOwnership.EXCLUSIVE),
                        new SecretRedactor(List.of("secret-sentinel")))) {
            writer.append(
                    SessionEvent.session(
                            "s",
                            "user.message",
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    "secret-sentinel",
                                    "api_key",
                                    "raw")));
            assertFalse(writer.durable());
            assertEquals(
                    "[REDACTED]",
                    repo.openReader("s").events().getFirst().payload().get("content"));
            assertFalse(repo.openReader("s").events().toString().contains("raw"));
        }
    }

    private static ChatRequest request(String content, double temperature) {
        return new ChatRequest(
                List.of(
                        Map.of("role", "system", "content", "rules"),
                        Map.of("role", "user", "content", content)),
                List.of(
                        Map.of(
                                "type",
                                "function",
                                "function",
                                Map.of("name", "read", "parameters", Map.of("type", "object")))),
                Map.of("temperature", temperature));
    }

    private static SessionEvent event(
            String id, String type, String step, Map<String, Object> payload) {
        return new SessionEvent(
                id, "s", type, Instant.EPOCH, 1, EventLevel.STEP, "t", step, null, payload);
    }
}
