package io.jharness.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3CompactionBoundaryTest {
    @Test
    void completedToolGroupsAndRemovalAuditMustStayAtomic() {
        var messages =
                List.<Map<String, Object>>of(
                        Map.of("role", "system", "content", "policy"),
                        Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "c1"))),
                        Map.of("role", "tool", "tool_call_id", "c1", "content", "result"),
                        Map.of("role", "user", "content", "current"));
        var request = new ChatRequest(messages, List.of(), Map.of());
        for (var removed : List.of(List.of(1), List.of(2))) {
            var retained = new ArrayList<>(messages);
            retained.remove((int) removed.getFirst());
            var actual = new ChatRequest(retained, List.of(), Map.of());
            assertEquals(
                    "INVALID_COMPACTION",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            ContextCompactionPlugin.validateCompaction(
                                                    request,
                                                    new ContextCompactor.Compaction(
                                                            actual, removed, "")))
                            .code());
        }
        var retained =
                new ChatRequest(
                        List.of(messages.getFirst(), messages.getLast()), List.of(), Map.of());
        assertEquals(
                "INVALID_COMPACTION",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ContextCompactionPlugin.validateCompaction(
                                                request,
                                                new ContextCompactor.Compaction(
                                                        retained, List.of(), "")))
                        .code());
        assertThrows(
                HarnessException.class,
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request,
                                new ContextCompactor.Compaction(retained, List.of(1, 2), "")));
        var summary =
                new ChatRequest(
                        List.of(
                                messages.getFirst(),
                                Map.of("role", "user", "content", "summary"),
                                messages.getLast()),
                        List.of(),
                        Map.of());
        assertThrows(
                HarnessException.class,
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request,
                                new ContextCompactor.Compaction(
                                        summary, List.of(1, 2), "summary")));
    }

    @Test
    void malformedOrDuplicateToolIdsUseCompactionError() {
        for (var calls :
                List.of(
                        List.of(Map.of("id", 7)),
                        List.of(Map.of("name", "missing")),
                        List.of(Map.of("id", "same"), Map.of("id", "same")))) {
            var request =
                    new ChatRequest(
                            List.of(Map.of("role", "assistant", "tool_calls", calls)),
                            List.of(),
                            Map.of());
            assertEquals(
                    "INVALID_COMPACTION",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            ContextCompactionPlugin.validateCompaction(
                                                    request,
                                                    new ContextCompactor.Compaction(
                                                            request, List.of(), "")))
                            .code());
        }
    }

    @Test
    void replacementCompactorCannotDropConfigOrProtectedMessages() {
        var request =
                new ChatRequest(
                        List.of(
                                Map.of("role", "system", "content", "policy"),
                                Map.of("role", "user", "content", "current")),
                        List.of(),
                        Map.of("model", "frozen"));
        assertThrows(
                HarnessException.class,
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request,
                                new ContextCompactor.Compaction(request, List.of(2), "bad")));
        assertThrows(
                HarnessException.class,
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request,
                                new ContextCompactor.Compaction(request, List.of(0), "bad")));
        assertThrows(
                HarnessException.class,
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request,
                                new ContextCompactor.Compaction(
                                        new ChatRequest(request.messages(), List.of(), Map.of()),
                                        List.of(),
                                        "bad")));
        assertDoesNotThrow(
                () ->
                        ContextCompactionPlugin.validateCompaction(
                                request, new ContextCompactor.Compaction(request, List.of(), "")));
    }
}
