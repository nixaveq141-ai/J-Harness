package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.EventSessionProjection;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceCompletionTest {
    @TempDir Path root;
    final ChatResponse response = new ChatResponse("claims success", List.of(), Map.of());

    @Test
    void currentTurnCannotReusePriorTurnsReadEvidence() throws Exception {
        var old =
                fact(
                        "old",
                        "tool.completed",
                        "read",
                        Map.of(
                                "tool",
                                "read_file",
                                "status",
                                "SUCCESS",
                                "value",
                                Map.of("path", "a.txt")));
        var current = fact("new", "turn.started", null, Map.of());
        var evaluator =
                new EvidenceCompletion(
                        new Arguments(new String[] {"--require-read", "a.txt"}),
                        new LocalWorkspace(root));
        assertFalse(
                evaluator
                        .evaluate(response, new EventSessionProjection(List.of(old, current)))
                        .achieved());
    }

    @Test
    void failedBuildThenLatestSuccessfulVerificationPassesAndSubsequentSystemCommandInvalidatesIt()
            throws Exception {
        var start = fact("t", "turn.started", null, Map.of());
        var failure = command("failure", "FAILED", 1, List.of("mvn", "test"), ".");
        var success = command("pass", "SUCCESS", 0, List.of("mvn", "test"), ".");
        var evaluator =
                new EvidenceCompletion(
                        new Arguments(new String[] {"--verify-argv", "[\"mvn\",\"test\"]"}),
                        new LocalWorkspace(root));
        assertTrue(
                evaluator
                        .evaluate(
                                response,
                                new EventSessionProjection(List.of(start, failure, success)))
                        .achieved());
        var later = command("later", "SUCCESS", 0, List.of("other-command"), ".");
        assertFalse(
                evaluator
                        .evaluate(
                                response,
                                new EventSessionProjection(List.of(start, failure, success, later)))
                        .achieved());
        assertFalse(
                evaluator
                        .evaluate(
                                response,
                                new EventSessionProjection(
                                        List.of(
                                                start,
                                                command(
                                                        "other-cwd",
                                                        "SUCCESS",
                                                        0,
                                                        List.of("mvn", "test"),
                                                        "other-module"))))
                        .achieved());
    }

    @Test
    void expectedFileMustStillHaveRecordedVersionAndVerificationAfterLastWrite() throws Exception {
        Files.writeString(root.resolve("a.txt"), "actual");
        var files = new LocalWorkspace(root);
        String version = files.versionOf(new ExecPath("a.txt"));
        var start = fact("t", "turn.started", null, Map.of());
        var write =
                fact(
                        "t",
                        "tool.completed",
                        "write",
                        Map.of(
                                "status",
                                "SUCCESS",
                                "tool",
                                "write_file",
                                "sideEffectLevel",
                                "WORKSPACE_WRITE",
                                "value",
                                Map.of(),
                                "display",
                                Map.of(
                                        "path",
                                        "a.txt",
                                        "beforeVersion",
                                        "MISSING",
                                        "afterVersion",
                                        version)));
        var verify = command("test", "SUCCESS", 0, List.of("mvn", "test"), ".");
        var evaluator =
                new EvidenceCompletion(
                        new Arguments(
                                new String[] {
                                    "--expect-file", "a.txt", "--verify-argv", "[\"mvn\",\"test\"]"
                                }),
                        files);
        assertTrue(
                evaluator
                        .evaluate(
                                response, new EventSessionProjection(List.of(start, write, verify)))
                        .achieved());
        assertFalse(
                evaluator
                        .evaluate(
                                response, new EventSessionProjection(List.of(start, verify, write)))
                        .achieved());
        Files.writeString(root.resolve("a.txt"), "user changed");
        assertFalse(
                evaluator
                        .evaluate(
                                response, new EventSessionProjection(List.of(start, write, verify)))
                        .achieved());
    }

    static SessionEvent command(String id, String status, int exit, List<String> argv, String cwd) {
        return fact(
                "t",
                "tool.completed",
                id,
                Map.of(
                        "tool",
                        "execute_command",
                        "status",
                        status,
                        "bodyEntered",
                        true,
                        "value",
                        Map.of("argv", argv, "mode", "ARGV", "cwd", cwd, "exitCode", exit)));
    }

    static SessionEvent fact(String turn, String type, String call, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                "s",
                type,
                Instant.now(),
                1,
                call == null ? EventLevel.TURN : EventLevel.TOOL,
                turn,
                call == null ? null : "p",
                call,
                payload);
    }
}
