package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M5CliTest {
    @TempDir Path root;
    private String output;

    private int cli(String... args) {
        var all = new ArrayList<>(List.of("--workspace", root.toString()));
        all.addAll(List.of(args));
        var out = new ByteArrayOutputStream();
        int status =
                new CliApplication(Map.of())
                        .execute(
                                all.toArray(String[]::new),
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(out));
        output = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        return status;
    }

    private JsonlSessionRepository repo() throws IOException {
        return new JsonlSessionRepository(root.resolve(".jharness/sessions"));
    }

    private SessionEvent init() throws IOException {
        var event =
                SessionEvent.session(
                        "s", "session.created", Map.of("workspace", root.toRealPath().toString()));
        try (var writer = repo().openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(event);
        }
        return event;
    }

    @Test
    void queryExportForkAndColdArtifactCommandsWorkWithoutModelConfiguration() throws Exception {
        var event = init();
        var artifacts = new LocalArtifactStore(root.resolve(".jharness/artifacts"), data -> data);
        var artifact = artifacts.put("s", "历史产物");
        assertEquals(0, cli("sessions", "query", "--session", "s", "--type", "session.created"));
        assertTrue(output.contains(event.eventId()));
        assertEquals(
                0,
                cli(
                        "sessions",
                        "query",
                        "--session-workspace",
                        root.toRealPath().toString(),
                        "--status",
                        "CREATED"));
        assertTrue(output.contains("\"session\":\"s\""));
        assertEquals(0, cli("sessions", "fork", "--session", "s", "--event", event.eventId()));
        String child = (String) Values.object(Json.parse(output)).get("session");
        assertEquals(
                0, cli("sessions", "artifact", "--session", child, "--artifact", artifact.id()));
        assertTrue(output.contains("历史产物"));
        assertEquals(
                0,
                cli(
                        "sessions",
                        "export",
                        "--session",
                        child,
                        "--output",
                        root.resolve("export.jsonl").toString()));
        assertEquals(
                5,
                cli(
                        "sessions",
                        "export",
                        "--session",
                        child,
                        "--output",
                        root.resolve("export.jsonl").toString()));
    }

    @Test
    void recoveryAndResumeRefuseUnknownBeforeLookingForApiKeyAndNeverAppend() throws Exception {
        init();
        try (var writer = repo().openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    new SessionEvent(
                            "intent",
                            "s",
                            "tool.intent",
                            Instant.now(),
                            1,
                            EventLevel.TOOL,
                            "turn",
                            "step",
                            "call",
                            Map.of("sideEffectLevel", "WORKSPACE_WRITE")));
        }
        byte[] before = Files.readAllBytes(root.resolve(".jharness/sessions/s.jsonl"));
        assertEquals(6, cli("sessions", "recover", "--session", "s"));
        assertTrue(output.contains("call"));
        assertEquals(6, cli("resume", "--session", "s"));
        assertArrayEquals(before, Files.readAllBytes(root.resolve(".jharness/sessions/s.jsonl")));
        assertEquals(
                0,
                cli(
                        "verify",
                        "--session",
                        "s",
                        "--call",
                        "call",
                        "--outcome",
                        "NOT_APPLIED",
                        "--reason",
                        "checked file SHA"));
        assertEquals(0, cli("sessions", "recover", "--session", "s"));
    }

    @Test
    void unresolvedModelRequestRequiresExplicitAbandonmentWithoutReplay() throws Exception {
        init();
        try (var writer = repo().openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    new SessionEvent(
                            "request",
                            "s",
                            "model.requested",
                            Instant.now(),
                            1,
                            EventLevel.STEP,
                            "turn",
                            "step",
                            null,
                            Map.of("contextEventId", "context")));
            writer.append(
                    new SessionEvent(
                            "cost",
                            "s",
                            "budget.settled",
                            Instant.now(),
                            1,
                            EventLevel.STEP,
                            "turn",
                            "step",
                            null,
                            Map.of("chargedUpperBound", "1")));
        }
        assertEquals(6, cli("resume", "--session", "s"));
        assertTrue(output.contains("pendingRequests"));
        assertEquals(
                0,
                cli(
                        "sessions",
                        "verify-request",
                        "--session",
                        "s",
                        "--event",
                        "request",
                        "--reason",
                        "provider checked; abandon missing response"));
        assertEquals(0, cli("sessions", "recover", "--session", "s"));
        assertEquals(
                5,
                cli(
                        "sessions",
                        "verify-request",
                        "--session",
                        "s",
                        "--event",
                        "request",
                        "--reason",
                        "duplicate"));
        assertEquals(
                1,
                repo().openReader("s").events().stream()
                        .filter(e -> e.type().equals("model.requested"))
                        .count());
    }

    @Test
    void futureAndMiddleDamageReturnReadOnlyExitCodeAndNeverAlterLog() throws Exception {
        init();
        Path log = root.resolve(".jharness/sessions/s.jsonl");
        String valid = Files.readString(log);
        Files.writeString(log, valid.replace("\"version\":1", "\"version\":2"));
        byte[] before = Files.readAllBytes(log);
        assertEquals(6, cli("sessions", "inspect", "--session", "s"));
        assertTrue(output.contains("FUTURE_VERSION_READ_ONLY"));
        assertEquals(6, cli("sessions", "migrate", "--session", "s"));
        assertArrayEquals(before, Files.readAllBytes(log));
        Files.writeString(log, valid + "{broken}\n{}\n");
        before = Files.readAllBytes(log);
        assertEquals(6, cli("sessions", "repair-tail", "--session", "s"));
        assertTrue(output.contains("MID_LOG_DAMAGED"));
        assertArrayEquals(before, Files.readAllBytes(log));
    }

    @Test
    void explicitMigrationAndTailRepairReportBackupsAndBecomeRecoverable() throws Exception {
        init();
        Path log = root.resolve(".jharness/sessions/s.jsonl");
        Files.writeString(log, Files.readString(log).replace("\"version\":1", "\"version\":0"));
        assertEquals(6, cli("sessions", "recover", "--session", "s"));
        assertEquals(0, cli("sessions", "migrate", "--session", "s"));
        assertTrue(output.contains("backup-"));
        Files.writeString(log, "{unfinished", StandardOpenOption.APPEND);
        assertEquals(6, cli("sessions", "recover", "--session", "s"));
        assertEquals(0, cli("sessions", "repair-tail", "--session", "s"));
        assertTrue(output.contains("backup-"));
        assertEquals(0, cli("sessions", "recover", "--session", "s"));
    }

    @Test
    void invalidPagesTimesAndTraversalAreUsageErrors() throws Exception {
        init();
        assertEquals(64, cli("sessions", "query", "--session", "s", "--limit", "1001"));
        assertEquals(64, cli("sessions", "query", "--after", "yesterday"));
        assertEquals(64, cli("sessions", "query", "--offset", "-1"));
        assertEquals(64, cli("sessions", "fork", "--session", "../s", "--event", "absent"));
        assertEquals(64, cli("resume"));
    }

    @Test
    void assistantCallsInterruptedBeforeToolRequestedCanBeReconciledWithoutExecution()
            throws Exception {
        init();
        try (var writer = repo().openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    new SessionEvent(
                            "assistant",
                            "s",
                            "assistant.message",
                            Instant.now(),
                            1,
                            EventLevel.STEP,
                            "turn",
                            "step",
                            null,
                            Map.of(
                                    "role",
                                    "assistant",
                                    "content",
                                    "",
                                    "tool_calls",
                                    List.of(
                                            Map.of(
                                                    "id",
                                                    "not-started",
                                                    "type",
                                                    "function",
                                                    "function",
                                                    Map.of(
                                                            "name",
                                                            "write_file",
                                                            "arguments",
                                                            "{}"))))));
        }
        assertEquals(6, cli("resume", "--session", "s"));
        assertEquals(
                0,
                cli(
                        "verify",
                        "--session",
                        "s",
                        "--call",
                        "not-started",
                        "--outcome",
                        "NOT_APPLIED",
                        "--reason",
                        "no tool body entered"));
        assertEquals(0, cli("sessions", "recover", "--session", "s"));
        assertTrue(
                repo().openReader("s").events().stream()
                        .noneMatch(
                                e ->
                                        e.type().equals("tool.started")
                                                || e.type().equals("tool.intent")));
        assertEquals(
                "assistant",
                repo().openReader("s").events().stream()
                        .filter(e -> e.type().equals("tool.verified"))
                        .findFirst()
                        .orElseThrow()
                        .payload()
                        .get("sourceEvent"));
    }

    @Test
    void unresolvedColdTerminalRequiresManualVerificationAndIsNeverRestarted() throws Exception {
        init();
        try (var writer = repo().openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            "s",
                            "terminal.snapshot",
                            Map.of(
                                    "tasks",
                                    List.of(Map.of("id", "lost-task", "state", "RUNNING")))));
        }
        assertEquals(6, cli("sessions", "recover", "--session", "s"));
        assertTrue(output.contains("pendingTerminals"));
        assertEquals(6, cli("resume", "--session", "s"));
        assertEquals(
                0,
                cli(
                        "sessions",
                        "verify-terminal",
                        "--session",
                        "s",
                        "--terminal-id",
                        "lost-task",
                        "--reason",
                        "checked process and files"));
        assertEquals(0, cli("sessions", "recover", "--session", "s"));
        assertTrue(
                repo().openReader("s").events().stream()
                        .noneMatch(
                                e ->
                                        e.type().equals("tool.intent")
                                                || e.type().equals("tool.started")));
    }
}
