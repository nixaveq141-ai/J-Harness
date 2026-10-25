package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.web.TaskDiagnosis;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class M105DiagnosisTest {
    @Test
    void uncertainEffectsTakePriorityOverContradictoryNotStartedFlags() {
        var result =
                issue(
                        List.of(
                                tool(
                                        "tool.completed",
                                        "conflict",
                                        Map.of(
                                                "status",
                                                "UNKNOWN",
                                                "bodyEntered",
                                                false,
                                                "value",
                                                Map.of(
                                                        "cleanupStatus",
                                                        "not_started",
                                                        "sideEffectUncertain",
                                                        true)))));
        assertEquals("possible", result.get("effects"));
        assertEquals("UNKNOWN", result.get("category"));
    }

    @Test
    void blankProviderModeCannotEraseTheRecordedRequestMode() {
        var result =
                issue(
                        List.of(
                                tool(
                                        "tool.requested",
                                        "mode",
                                        Map.of("arguments", Map.of("mode", "ARGV"))),
                                tool(
                                        "tool.completed",
                                        "mode",
                                        Map.of("status", "FAILED", "value", Map.of("mode", "")))));
        assertEquals("ARGV", result.get("mode"));
    }

    @Test
    void redactionPrecedesTruncationAndDoesNotExposeSecretPrefixes() {
        String secret = "synthetic-long-secret-" + "x".repeat(400);
        var result =
                tool(
                        "tool.completed",
                        "call",
                        Map.of("status", "FAILED", "error", Map.of("code", secret)));
        var diagnosis = TaskDiagnosis.inspect(List.of(result), new SecretRedactor(List.of(secret)));
        var row = Values.object(((List<?>) diagnosis.get("issues")).getFirst());
        assertEquals("[REDACTED]", row.get("code"));
        assertFalse(diagnosis.toString().contains("synthetic-long-secret"));
    }

    @Test
    void freeEventAndCallIdentifiersAreSanitizedAndBoundedForDisplay() {
        String id = "synthetic-id-private";
        var event =
                new SessionEvent(
                        id,
                        "session",
                        "tool.completed",
                        Instant.now(),
                        1,
                        EventLevel.TOOL,
                        "turn",
                        "step",
                        id,
                        Map.of("status", "FAILED", "error", Map.of("code", "fixture")));
        var row =
                Values.object(
                        ((List<?>)
                                        TaskDiagnosis.inspect(
                                                        List.of(event),
                                                        new SecretRedactor(List.of(id)))
                                                .get("issues"))
                                .getFirst());
        assertFalse(row.toString().contains(id));
        assertEquals("[REDACTED]", row.get("eventId"));
        assertEquals("[REDACTED]", row.get("callId"));
    }

    private static SessionEvent tool(String type, String call, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                "session",
                type,
                Instant.now(),
                1,
                EventLevel.TOOL,
                "turn",
                "step",
                call,
                payload);
    }

    private static Map<String, Object> issue(List<SessionEvent> events) {
        return Values.object(((List<?>) TaskDiagnosis.inspect(events).get("issues")).getFirst());
    }

    @Test
    void shellValidationIsNotConfusedWithCommandStartOrMissingFacts() {
        var result =
                tool(
                        "tool.completed",
                        "shell",
                        Map.of(
                                "tool",
                                "execute_command",
                                "status",
                                "FAILED",
                                "bodyEntered",
                                true,
                                "durationMs",
                                1,
                                "error",
                                Map.of("code", "SHELL_REQUIRED"),
                                "value",
                                Map.of(
                                        "cleanupStatus",
                                        "not_started",
                                        "sideEffectUncertain",
                                        false)));
        var row = issue(List.of(result));
        assertEquals("VALIDATION", row.get("category"));
        assertEquals(true, row.get("bodyEntered"));
        assertEquals("no", row.get("commandStarted"));
        assertEquals("not_started", row.get("effects"));
        assertFalse(row.containsKey("timeoutMs"));
        assertEquals(result.eventId(), row.get("eventId"));
        var legacy = issue(List.of(tool("tool.completed", "old", Map.of("status", "FAILED"))));
        assertEquals("unknown", legacy.get("bodyEntered"));
        assertEquals("unknown", legacy.get("effects"));
        assertFalse(legacy.containsKey("durationMs"));
    }

    @Test
    void timeoutPartialCleanupAndClosedEnvironmentNeverClearUncertainty() {
        var requested =
                tool(
                        "tool.requested",
                        "timeout",
                        Map.of("arguments", Map.of("mode", "ARGV", "timeoutMs", 120000)));
        var result =
                tool(
                        "tool.completed",
                        "timeout",
                        Map.of(
                                "tool",
                                "execute_command",
                                "status",
                                "TIMEOUT",
                                "bodyEntered",
                                true,
                                "value",
                                Map.of(
                                        "durationMs",
                                        120308,
                                        "cleanupStatus",
                                        "partial",
                                        "observedClean",
                                        true,
                                        "sideEffectsMayHaveOccurred",
                                        true,
                                        "sideEffectUncertain",
                                        true),
                                "error",
                                Map.of("code", "TIMEOUT")));
        var events =
                List.of(
                        requested,
                        tool("tool.intent", "timeout", Map.of()),
                        result,
                        SessionEvent.session(
                                "session", "environment.closed", Map.of("status", "SUCCESS")));
        var diagnosis = TaskDiagnosis.inspect(events);
        var row = issue(events);
        assertEquals("TIMEOUT", row.get("category"));
        assertEquals(120000, row.get("timeoutMs"));
        assertEquals(120308, row.get("commandDurationMs"));
        assertEquals("yes", row.get("commandStarted"));
        assertEquals("possible", row.get("effects"));
        assertEquals("partial", row.get("cleanupStatus"));
        assertEquals(false, diagnosis.get("sdkFactsSettled"));
        assertEquals(
                List.of("timeout"), Values.object(diagnosis.get("pending")).get("uncertainCalls"));
    }

    @Test
    void unresolvedModelToolsAndTerminalsKeepExactRecoveryAssociations() {
        var events =
                List.of(
                        new SessionEvent(
                                "request",
                                "session",
                                "model.requested",
                                Instant.now(),
                                1,
                                EventLevel.STEP,
                                "turn",
                                "pending-step",
                                null,
                                Map.of()),
                        new SessionEvent(
                                "reply",
                                "session",
                                "assistant.message",
                                Instant.now(),
                                1,
                                EventLevel.STEP,
                                "turn",
                                "other-step",
                                null,
                                Map.of("tool_calls", List.of(Map.of("id", "unsettled")))),
                        SessionEvent.session(
                                "session",
                                "terminal.snapshot",
                                Map.of(
                                        "tasks",
                                        List.of(Map.of("id", "terminal", "state", "UNKNOWN")))),
                        SessionEvent.session(
                                "session", "environment.closed", Map.of("status", "SUCCESS")));
        var result = TaskDiagnosis.inspect(events);
        var pending = Values.object(result.get("pending"));
        assertEquals(false, result.get("sdkFactsSettled"));
        assertEquals(List.of("pending-step"), pending.get("pendingRequests"));
        assertEquals(List.of("unsettled"), pending.get("unsettledCalls"));
        assertEquals(List.of("terminal"), pending.get("pendingTerminals"));
        assertEquals(
                List.of(
                        Map.of(
                                "callId",
                                "unsettled",
                                "eventId",
                                "reply",
                                "turnId",
                                "turn",
                                "stepId",
                                "other-step")),
                pending.get("unsettledSources"));
    }

    @Test
    void distinguishesDenialCancellationNetworkAndBoundedHistory() {
        for (var pair :
                Map.of(
                                "INVALID_ARGUMENT",
                                "VALIDATION",
                                "PERMISSION_DENIED",
                                "DENIED",
                                "CANCELLED",
                                "CANCELLED",
                                "HTTP_503",
                                "MODEL_NETWORK",
                                "UNKNOWN_SIDE_EFFECT",
                                "UNKNOWN")
                        .entrySet()) {
            var e =
                    tool(
                            "tool.completed",
                            "call",
                            Map.of(
                                    "status",
                                    "FAILED",
                                    "bodyEntered",
                                    false,
                                    "error",
                                    Map.of("code", pair.getKey())));
            assertEquals(pair.getValue(), issue(List.of(e)).get("category"));
        }
        var events = new ArrayList<SessionEvent>();
        for (int i = 0; i < 80; i++)
            events.add(tool("tool.completed", "call" + i, Map.of("status", "FAILED")));
        var result = TaskDiagnosis.inspect(events);
        assertEquals(12, ((List<?>) result.get("issues")).size());
        assertEquals(true, result.get("issuesLimited"));
        assertEquals(
                java.util.stream.IntStream.range(68, 80).mapToObj(i -> "call" + i).toList(),
                ((List<?>) result.get("issues"))
                        .stream().map(v -> Values.object(v).get("callId")).toList());
    }

    @Test
    void expiredApprovalAndCancellationRaceUseRecordedOutcome() {
        var decision =
                tool(
                        "approval.decided",
                        "expired",
                        Map.of("approved", false, "reason", "Approval expired"));
        var denied =
                tool(
                        "tool.completed",
                        "expired",
                        Map.of(
                                "status",
                                "DENIED",
                                "bodyEntered",
                                false,
                                "error",
                                Map.of("code", "PERMISSION_DENIED")));
        var row = issue(List.of(decision, denied));
        assertEquals("Approval expired", row.get("approvalReason"));
        assertEquals("DENIED", row.get("category"));
        // Cancellation requested by a host must not override a completed result in this projection.
        var completed = tool("tool.completed", "won", Map.of("status", "SUCCESS"));
        var cancelled =
                new SessionEvent(
                        "cancelled",
                        "session",
                        "turn.ended",
                        Instant.now(),
                        1,
                        EventLevel.TURN,
                        completed.turnId(),
                        null,
                        null,
                        Map.of("status", "CANCELLED", "reason", "USER_CANCELLED"));
        for (var events : List.of(List.of(cancelled, completed), List.of(completed, cancelled))) {
            var diagnosis = TaskDiagnosis.inspect(events);
            assertEquals(1, ((List<?>) diagnosis.get("issues")).size());
            assertTrue(
                    ((List<?>) diagnosis.get("issues"))
                            .stream()
                                    .noneMatch(
                                            candidate ->
                                                    "won"
                                                            .equals(
                                                                    Values.object(candidate)
                                                                            .get("callId"))));
            assertEquals(List.of(), Values.object(diagnosis.get("pending")).get("uncertainCalls"));
            assertEquals("SUCCESS", completed.payload().get("status"));
        }
    }
}
