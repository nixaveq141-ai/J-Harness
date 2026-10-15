package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class SessionEventTest {
    @Test
    void cyclicPayloadIsRejectedByTheNestingBound() {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> Values.snapshot(cyclic));
        SecretRedactor redactor = new SecretRedactor(List.of("sentinel"));
        assertEquals(
                Map.of("api_key", "[REDACTED]", "nested", List.of("[REDACTED]")),
                redactor.sanitize(Map.of("api_key", "other", "nested", List.of("sentinel"))));
    }

    @Test
    void associationFieldsFollowLevelAndKnownType() {
        SessionEvent session = SessionEvent.session("s", "session.created", Map.of());
        assertNull(session.turnId());
        assertNull(session.stepId());
        assertThrows(
                IllegalArgumentException.class,
                () -> event("tool.started", EventLevel.STEP, "t", "p", null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> event("tool.started", EventLevel.TOOL, "t", "p", null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> event("turn.started", EventLevel.TURN, "t", "p", null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> event("session.created", EventLevel.STEP, "t", "p", null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> event("extension", EventLevel.SESSION, "t", null, null, Map.of()));
        assertEquals("c", event("tool.started", EventLevel.TOOL, "t", "p", "c", Map.of()).callId());
    }

    @Test
    void payloadIsDeepImmutableAndDetached() {
        List<Object> list = new ArrayList<>(List.of("original"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("list", list);
        SessionEvent event = SessionEvent.session("s", "session.created", Map.of("nested", nested));
        list.add("later");
        nested.put("secret", "later");
        assertEquals(Map.of("nested", Map.of("list", List.of("original"))), event.payload());
        assertThrows(
                UnsupportedOperationException.class, () -> event.payload().put("other", "value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionEvent.session("s", "session.created", Map.of("bad", new Object())));
        assertThrows(
                IllegalArgumentException.class, () -> Values.snapshot(Map.of("nan", Double.NaN)));
    }

    private static SessionEvent event(
            String type,
            EventLevel level,
            String turn,
            String step,
            String call,
            Map<String, Object> payload) {
        return new SessionEvent("e", "s", type, Instant.EPOCH, 1, level, turn, step, call, payload);
    }
}
