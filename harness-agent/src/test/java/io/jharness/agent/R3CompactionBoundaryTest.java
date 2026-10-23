package io.jharness.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3CompactionBoundaryTest {
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
