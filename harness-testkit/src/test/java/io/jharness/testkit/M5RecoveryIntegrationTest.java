package io.jharness.testkit;

import static io.jharness.testkit.M4CliIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M5RecoveryIntegrationTest {
    @TempDir Path root;

    @Test
    void coldForkResumeKeepsRequestHistoryAndRejectsStaleFileVersion() throws Exception {
        Path note = root.resolve("note.txt");
        Files.writeString(note, "initial");
        String version =
                (String)
                        new LocalWorkspace(root)
                                .readPage(new ExecPath("note.txt"), 1, 1, CancellationToken.NONE)
                                .get("version");
        assertNotNull(version);
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(call("read", "read_file", Map.of("path", "note.txt"))),
                                text("read complete"),
                                calls(
                                        call(
                                                "stale-write",
                                                "write_file",
                                                Map.of(
                                                        "path",
                                                        "note.txt",
                                                        "content",
                                                        "agent",
                                                        "overwrite",
                                                        true,
                                                        "expectedVersion",
                                                        version))),
                                text("version conflict reported")))) {
            var fixture = new M4CliIntegrationTest();
            fixture.root = root;
            Path config = fixture.config(server);
            var first =
                    fixture.execute(
                            config,
                            "",
                            "run",
                            "inspect note",
                            "--session",
                            "s",
                            "--require-read",
                            "note.txt");
            assertEquals(0, first.code(), first.stderr() + first.stdout());
            var repo = new JsonlSessionRepository(root.resolve(".jharness/sessions"));
            var parent = repo.openReader("s").events();
            String child = repo.fork("s", parent.getLast().eventId());
            Files.writeString(note, "user changed");
            var resumed =
                    fixture.execute(
                            config,
                            "y\n",
                            "resume",
                            "continue safely",
                            "--session",
                            child,
                            "--expect-file",
                            "note.txt");
            assertEquals(2, resumed.code(), resumed.stderr() + resumed.stdout());
            assertEquals("user changed", Files.readString(note));
            assertEquals(parent, repo.openReader("s").events());
            var childEvents =
                    new JsonlSessionRepository(root.resolve(".jharness/sessions"))
                            .openReader(child)
                            .events();
            assertTrue(
                    childEvents.stream()
                            .anyMatch(
                                    e ->
                                            "stale-write".equals(e.callId())
                                                    && e.type().equals("tool.completed")
                                                    && "FAILED".equals(e.payload().get("status"))));
            for (var event : childEvents)
                if (event.type().equals("model.requested"))
                    assertNotNull(
                            new EventRequestJournal().reconstruct(childEvents, event.eventId()));
            assertEquals(4, server.requests.size());
            assertTrue(
                    ((List<?>) server.requests.get(2).get("messages")).size()
                            > ((List<?>) server.requests.getFirst().get("messages")).size());
        }
    }
}
