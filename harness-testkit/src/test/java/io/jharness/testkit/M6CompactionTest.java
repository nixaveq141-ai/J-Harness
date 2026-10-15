package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class M6CompactionTest {
    static Map<String, Object> assistant(String id) {
        return Map.of(
                "role",
                "assistant",
                "content",
                "",
                "tool_calls",
                List.of(
                        Map.of(
                                "id",
                                id,
                                "type",
                                "function",
                                "function",
                                Map.of("name", "read_file", "arguments", Map.of("path", "a")))));
    }

    static List<Map<String, Object>> messages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "system instruction"));
        messages.add(Map.of("role", "user", "content", "current task"));
        for (int i = 0; i < 8; i++) {
            messages.add(assistant("c" + i));
            messages.add(
                    Map.of(
                            "role",
                            "tool",
                            "tool_call_id",
                            "c" + i,
                            "content",
                            "evidence " + "x".repeat(600)));
        }
        return messages;
    }

    @Test
    void longCurrentTurnCompactsWholeToolGroupsAndKeepsSystemGoalAndLatestResults() {
        var original = new ChatRequest(messages(), List.of(), Map.of());
        var compacted = new ExtractiveContextCompactor().compact(original, 2000);
        assertFalse(compacted.removedMessageIndices().isEmpty());
        assertTrue(compacted.request().messages().toString().length() < 2000);
        assertEquals(original.messages().getFirst(), compacted.request().messages().getFirst());
        assertTrue(
                compacted
                        .request()
                        .messages()
                        .contains(Map.of("role", "user", "content", "current task")));
        assertEquals(original.messages().getLast(), compacted.request().messages().getLast());
        for (int i = 2; i < original.messages().size(); i += 2)
            assertEquals(
                    compacted.removedMessageIndices().contains(i),
                    compacted.removedMessageIndices().contains(i + 1));
        assertEquals(original.messages(), messages());
    }

    @Test
    void unresolvedCallsAndOversizedProtectedContextRefuseCompaction() {
        var messages = messages();
        messages.add(assistant("pending"));
        assertThrows(
                HarnessException.class,
                () ->
                        new ExtractiveContextCompactor()
                                .compact(new ChatRequest(messages, List.of(), Map.of()), 2000));
        assertThrows(
                HarnessException.class,
                () ->
                        new ExtractiveContextCompactor()
                                .compact(
                                        new ChatRequest(
                                                List.of(
                                                        Map.of(
                                                                "role",
                                                                "system",
                                                                "content",
                                                                "x".repeat(3000)),
                                                        Map.of("role", "user", "content", "goal")),
                                                List.of(),
                                                Map.of()),
                                        2000));
    }

    @Test
    void pluginRecordsCompactionAndFinalRequestReplaysFromColdEventsAndRevokesOnStop()
            throws Exception {
        var repo = new InMemorySessionRepository();
        var scope = new PluginScope("s", "agent");
        var registry = new DefaultServiceRegistry();
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
                var container =
                        new PluginContainer(
                                scope,
                                registry,
                                List.of(
                                        new ContextCompactionPlugin(
                                                new ExtractiveContextCompactor(),
                                                writer,
                                                repo.openReader("s"),
                                                2000)),
                                Set.of("context-compaction"),
                                Map.of(),
                                List.of(),
                                failure -> {})) {
            container.start();
            var original = new ChatRequest(messages(), List.of(), Map.of("model", "fixture"));
            var result =
                    container
                            .events()
                            .invoke(StandardExtensionPoints.BEFORE_MODEL, original, value -> value);
            assertEquals("context.compacted", repo.openReader("s").events().getFirst().type());
            var journal = new EventRequestJournal();
            String requested = journal.record(result, "s", "t", "step", writer, container.events());
            assertEquals(result, journal.reconstruct(repo.openReader("s").events(), requested));
            assertEquals(
                    result.messages(),
                    new EventSessionProjection(repo.openReader("s").events()).messages());
            container.stop();
            assertEquals(
                    original,
                    container
                            .events()
                            .invoke(
                                    StandardExtensionPoints.BEFORE_MODEL,
                                    original,
                                    value -> value));
            container.events().awaitNotifications(Duration.ofSeconds(2));
        }
    }
}
