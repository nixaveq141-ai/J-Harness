package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R123TurnQueueBoundaryTest {
    @Test
    void malformedAdmissionCannotPoisonItsHashOrPublishPartialSelection() {
        for (Map<String, Object> invalid :
                List.<Map<String, Object>>of(
                        Map.of("goalHash", 7), Map.of("selection", Map.of("modelRoute", 7)))) {
            var queue = new TurnQueue();
            var payload =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "requestHash",
                                    "a".repeat(64),
                                    "goalHash",
                                    "b".repeat(64),
                                    "kind",
                                    "message",
                                    "content",
                                    "goal"));
            payload.putAll(invalid);
            assertEquals(
                    "INVALID_WEB_CATALOG",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            queue.apply(
                                                    SessionEvent.session(
                                                            "s", "web.turn.accepted", payload)))
                            .code());
            assertTrue(queue.inputs.isEmpty());
            payload.put("goalHash", "b".repeat(64));
            payload.remove("selection");
            assertDoesNotThrow(
                    () -> queue.apply(SessionEvent.session("s", "web.turn.accepted", payload)));
            assertEquals(1, queue.inputs.size());
        }
    }
}
