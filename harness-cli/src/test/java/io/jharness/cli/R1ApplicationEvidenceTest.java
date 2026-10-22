package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.EventSessionProjection;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1ApplicationEvidenceTest {
    @TempDir Path root;
    final ChatResponse response = new ChatResponse("claim", List.of(), Map.of());

    EvidenceCompletion evaluator(String... args) throws Exception {
        return new EvidenceCompletion(new Arguments(args), new LocalWorkspace(root));
    }

    CompletionEvaluator.CompletionDecision result(EvidenceCompletion e, SessionEvent... facts) {
        var all = new ArrayList<SessionEvent>();
        all.add(EvidenceCompletionTest.fact("t", "turn.started", null, Map.of()));
        all.addAll(List.of(facts));
        return e.evaluate(response, new EventSessionProjection(all));
    }

    SessionEvent command(String status, Object exit, boolean entered) {
        var value = new HashMap<String, Object>();
        value.put("argv", List.of("mvn", "test"));
        value.put("mode", "ARGV");
        value.put("cwd", "./");
        value.put("exitCode", exit);
        return EvidenceCompletionTest.fact(
                "t",
                "tool.completed",
                UUID.randomUUID().toString(),
                Map.of(
                        "tool",
                        "execute_command",
                        "status",
                        status,
                        "bodyEntered",
                        entered,
                        "value",
                        value));
    }

    @Test
    void invalidVerificationCriteriaAreUsageErrors() throws Exception {
        for (String invalid : List.of("null", "{}", "[1]", "[]", "garbage")) {
            var error =
                    assertThrows(HarnessException.class, () -> evaluator("--verify-argv", invalid));
            assertEquals("CLI_USAGE", error.code());
        }
    }

    @Test
    void criteriaRejectAbsoluteAndParentTraversal() throws Exception {
        for (String path : List.of("../a", "C:/a", "/a"))
            assertEquals(
                    "CLI_USAGE",
                    assertThrows(HarnessException.class, () -> evaluator("--require-read", path))
                            .code());
    }

    @Test
    void nullableForeignExitCodeIsNegativeEvidence() throws Exception {
        var e = evaluator("--verify-argv", "[\"mvn\",\"test\"]");
        for (Object value : Arrays.asList(null, "0", 0.5)) {
            var decision = result(e, command("SUCCESS", value, true));
            assertFalse(decision.achieved());
            assertEquals("VERIFICATION_FAILED", decision.reason());
        }
        var missingStatus =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "missing-status",
                        Map.of(
                                "tool",
                                "execute_command",
                                "bodyEntered",
                                true,
                                "value",
                                Map.of(
                                        "argv",
                                        List.of("mvn", "test"),
                                        "mode",
                                        "ARGV",
                                        "cwd",
                                        ".",
                                        "exitCode",
                                        0)));
        assertEquals("VERIFICATION_FAILED", result(e, missingStatus).reason());
    }

    @Test
    void latestExecutedVerificationMustPass() throws Exception {
        var e = evaluator("--verify-argv", "[\"mvn\",\"test\"]");
        assertEquals(
                "VERIFICATION_FAILED",
                result(e, command("SUCCESS", 0, true), command("FAILED", 1, true)).reason());
        assertEquals("VERIFICATION_FAILED", result(e, command("SUCCESS", 1, true)).reason());
        assertTrue(result(e, command("FAILED", 1, true), command("SUCCESS", 0, true)).achieved());
    }

    @Test
    void deniedUnenteredCommandCannotReplaceExecutedVerification() throws Exception {
        assertTrue(
                result(
                                evaluator("--verify-argv", "[\"mvn\",\"test\"]"),
                                command("SUCCESS", 0, true),
                                command("DENIED", -1, false))
                        .achieved());
    }

    @Test
    void deniedWriteDoesNotInvalidateButUnknownWriteDoes() throws Exception {
        var e = evaluator("--verify-argv", "[\"mvn\",\"test\"]");
        var denied =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "w",
                        Map.of(
                                "tool",
                                "write_file",
                                "status",
                                "DENIED",
                                "sideEffectLevel",
                                "WORKSPACE_WRITE",
                                "bodyEntered",
                                false));
        assertTrue(result(e, command("SUCCESS", 0, true), denied).achieved());
        var unknown =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "u",
                        Map.of(
                                "tool",
                                "write_file",
                                "status",
                                "UNKNOWN",
                                "sideEffectLevel",
                                "WORKSPACE_WRITE",
                                "bodyEntered",
                                true));
        assertEquals(
                "VERIFICATION_MISSING_OR_STALE",
                result(e, command("SUCCESS", 0, true), unknown).reason());
    }

    @Test
    void readEvidenceNormalizesBothSidesAndRejectsUnknown() throws Exception {
        var e = evaluator("--require-read", "a.txt");
        var success =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "r",
                        Map.of(
                                "tool",
                                "read_file",
                                "status",
                                "SUCCESS",
                                "value",
                                Map.of("path", "./a.txt")));
        assertTrue(result(e, success).achieved());
        for (String status : List.of("UNKNOWN", "FAILED")) {
            var f =
                    EvidenceCompletionTest.fact(
                            "t",
                            "tool.completed",
                            "r",
                            Map.of(
                                    "tool",
                                    "read_file",
                                    "status",
                                    status,
                                    "value",
                                    Map.of("path", "a.txt")));
            assertEquals("READ_EVIDENCE_MISSING:a.txt", result(e, f).reason());
        }
    }

    @Test
    void malformedChangesAreNegativeWithoutCrashing() throws Exception {
        var e = evaluator("--expect-file", "a.txt");
        for (Object changes : List.of("bad", List.of(1))) {
            var f =
                    EvidenceCompletionTest.fact(
                            "t",
                            "tool.completed",
                            "w",
                            Map.of(
                                    "tool",
                                    "write_file",
                                    "status",
                                    "SUCCESS",
                                    "sideEffectLevel",
                                    "WORKSPACE_WRITE",
                                    "value",
                                    Map.of("changes", changes)));
            assertEquals("EVIDENCE_INVALID", result(e, f).reason());
        }
    }

    @Test
    void malformedAfterVersionIsNegativeWithoutCrashing() throws Exception {
        var e = evaluator("--expect-file", "a.txt");
        var f =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "w",
                        Map.of(
                                "tool",
                                "write_file",
                                "status",
                                "SUCCESS",
                                "sideEffectLevel",
                                "WORKSPACE_WRITE",
                                "display",
                                Map.of(
                                        "path",
                                        "a.txt",
                                        "beforeVersion",
                                        "old",
                                        "afterVersion",
                                        1)));
        assertEquals("CHANGE_EVIDENCE_INVALID:a.txt", result(e, f).reason());
    }

    @Test
    void missingStatusReadAndWriteCannotAuthorizeCompletion() throws Exception {
        Files.writeString(root.resolve("a.txt"), "actual");
        String version = new LocalWorkspace(root).versionOf(new ExecPath("a.txt"));
        var read =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "read-missing-status",
                        Map.of("tool", "read_file", "value", Map.of("path", "a.txt")));
        var write =
                EvidenceCompletionTest.fact(
                        "t",
                        "tool.completed",
                        "write-missing-status",
                        Map.of(
                                "tool",
                                "write_file",
                                "sideEffectLevel",
                                "WORKSPACE_WRITE",
                                "display",
                                Map.of(
                                        "path",
                                        "a.txt",
                                        "beforeVersion",
                                        "old",
                                        "afterVersion",
                                        version)));
        assertEquals(
                "READ_EVIDENCE_MISSING:a.txt",
                result(evaluator("--require-read", "a.txt"), read).reason());
        assertEquals(
                "CHANGE_EVIDENCE_MISSING:a.txt",
                result(evaluator("--expect-file", "a.txt"), write).reason());
        var successfulPayload = new HashMap<String, Object>(write.payload());
        successfulPayload.put("status", "SUCCESS");
        var acceptedWrite =
                new SessionEvent(
                        write.eventId(),
                        write.sessionId(),
                        write.type(),
                        write.timestamp(),
                        write.schemaVersion(),
                        write.level(),
                        write.turnId(),
                        write.stepId(),
                        write.callId(),
                        successfulPayload);
        assertTrue(result(evaluator("--expect-file", "a.txt"), acceptedWrite).achieved());
    }
}
