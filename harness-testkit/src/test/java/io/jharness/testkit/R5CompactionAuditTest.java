package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R5CompactionAuditTest {
    @Test
    void auditCapsAssociationsAndDoesNotGuessAmbiguousSourceEvents() throws Exception {
        var repo = new InMemorySessionRepository();
        var scope = new PluginScope("s", "agent");
        var messages = new ArrayList<Map<String, Object>>();
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            for (int i = 0; i < 300; i++) {
                var message =
                        Map.<String, Object>of(
                                "role", "user", "content", "m" + i + "x".repeat(100));
                messages.add(message);
                writer.append(SessionEvent.session("s", "user.message", message));
                if (i == 0) writer.append(SessionEvent.session("s", "user.message", message));
            }
            messages.add(Map.of("role", "user", "content", "current"));
            try (var container =
                    new PluginContainer(
                            scope,
                            new DefaultServiceRegistry(),
                            List.of(
                                    new ContextCompactionPlugin(
                                            new ExtractiveContextCompactor(),
                                            writer,
                                            repo.openReader("s"),
                                            500)),
                            Set.of("context-compaction"),
                            Map.of(),
                            List.of(),
                            failure -> {})) {
                container.start();
                var result =
                        container
                                .events()
                                .invoke(
                                        StandardExtensionPoints.BEFORE_MODEL,
                                        new ChatRequest(messages, List.of(), Map.of()),
                                        value -> value);
                assertTrue(
                        result.messages().toString().length() + result.tools().toString().length()
                                <= 500);
                var audit = repo.openReader("s").events().getLast().payload();
                assertEquals(256, ((List<?>) audit.get("sourceEvents")).size());
                assertEquals(256, ((List<?>) audit.get("sourceAssociations")).size());
                assertEquals(true, audit.get("sourceEventsTruncated"));
                assertEquals(1, audit.get("unmappedMessages"));
                assertTrue(((Number) audit.get("sourceEventCount")).intValue() > 256);
            }
        }
    }
}
