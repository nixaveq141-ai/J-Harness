package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.ConfiguredTokenCostGuard;
import io.jharness.api.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliContractsTest {
    @TempDir Path root;

    @Test
    void parserRejectsUnknownAndDuplicateFlagsButPreservesChineseAndSpaces() {
        var args =
                new Arguments(
                        new String[] {
                            "--workspace",
                            "中文 path",
                            "run",
                            "实现功能",
                            "--require-read",
                            "pom.xml",
                            "--require-read",
                            "src/A.java"
                        });
        assertEquals("中文 path", args.get("workspace", ""));
        assertEquals(2, args.all("require-read").size());
        assertThrows(HarnessException.class, () -> new Arguments(new String[] {"--yes"}));
        assertThrows(
                HarnessException.class,
                () -> new Arguments(new String[] {"--session", "a", "--session", "b"}));
    }

    @Test
    void configurationRejectsInlineSecretsWrongTypesAndUnavailableCostAccounting() {
        assertThrows(HarnessException.class, () -> new CliConfig(Map.of("apiKey", "private")));
        assertThrows(HarnessException.class, () -> new CliConfig(Map.of("maxSteps", 1.5)));
        assertThrows(
                HarnessException.class,
                () -> new CliConfig(Map.of("enabledPlugins", List.of("unknown"))));
        assertThrows(HarnessException.class, () -> new CliConfig(Map.of("maxCost", "1")).costs());
    }

    @Test
    void CnyUsageSettlementIsConservativeAndMissingUsageRetainsFullBound() {
        var costs =
                new ConfiguredTokenCostGuard(
                        "fixture",
                        1000000,
                        4096,
                        new BigDecimal("0.000002"),
                        new BigDecimal("0.000008"),
                        "CNY");
        var request =
                new ChatRequest(
                        List.of(), List.of(), Map.of("model", "fixture", "max_tokens", 4096));
        assertEquals("CNY", costs.currency());
        assertEquals(new BigDecimal("2.032768"), costs.maximumCost(request));
        assertEquals(
                new BigDecimal("0.000360"),
                costs.actualCost(
                        request,
                        new ChatResponse(
                                "ok",
                                List.of(),
                                Map.of("prompt_tokens", 100, "completion_tokens", 20))));
        assertEquals(
                costs.maximumCost(request),
                costs.actualCost(request, new ChatResponse("ok", List.of(), Map.of())));
        assertThrows(
                HarnessException.class,
                () ->
                        costs.actualCost(
                                request,
                                new ChatResponse(
                                        "ok",
                                        List.of(),
                                        Map.of("prompt_tokens", 1000001, "completion_tokens", 1))));
    }

    @Test
    void modelClaimsWithoutToolFactsNeverBecomeChangesOrTestEvidence() {
        var narration =
                event(
                        "assistant.message",
                        EventLevel.STEP,
                        "p",
                        null,
                        Map.of(
                                "role",
                                "assistant",
                                "content",
                                "Changed fake.java and passed tests"));
        var report = EvidenceReport.build("task", List.of(narration), new SecretRedactor());
        var facts = Values.object(report.get("recordedFacts"));
        assertEquals(List.of(), facts.get("changes"));
        assertEquals(List.of(), facts.get("commands"));
        assertTrue(report.get("modelExplanation").toString().contains("fake.java"));
        assertTrue(EvidenceReport.markdown(report).contains("不作为执行证据"));
    }

    @Test
    void interruptedIntentRequiresOperatorVerificationAndVerifiedFactClearsOnlyThatCall() {
        var intent =
                event(
                        "tool.intent",
                        EventLevel.TOOL,
                        "p",
                        "c",
                        Map.of("sideEffectLevel", "WORKSPACE_WRITE"));
        var unknown =
                event(
                        "tool.completed",
                        EventLevel.TOOL,
                        "p",
                        "c",
                        ToolResults.event(ToolResults.failure("c", ToolStatus.UNKNOWN, "UNKNOWN")));
        var verified =
                event(
                        "tool.verified",
                        EventLevel.TOOL,
                        "p",
                        "c",
                        Map.of("outcome", "PARTIAL", "reason", "inspected disk", "actor", "user"));
        assertEquals(
                List.of("c"),
                new io.jharness.session.EventSessionProjection(List.of(intent, unknown))
                        .uncertainCalls());
        assertEquals(
                List.of(),
                new io.jharness.session.EventSessionProjection(List.of(intent, unknown, verified))
                        .uncertainCalls());
    }

    @Test
    void offlineDryRunDoesNotNeedApiKeyAndDoesNotCreatePlannedFiles() throws Exception {
        Path plan = root.resolve("plan.json");
        Files.writeString(
                plan,
                Json.text(
                        Map.of(
                                "calls",
                                List.of(
                                        Map.of(
                                                "tool",
                                                "write_file",
                                                "arguments",
                                                Map.of("path", "new.txt", "content", "new")),
                                        Map.of(
                                                "tool",
                                                "execute_command",
                                                "arguments",
                                                Map.of(
                                                        "argv",
                                                        List.of("echo x > new.txt & start cmd"),
                                                        "mode",
                                                        "CMD"))))));
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                new CliApplication(Map.of())
                        .execute(
                                new String[] {
                                    "run",
                                    "plan",
                                    "--workspace",
                                    root.toString(),
                                    "--dry-run",
                                    "--plan",
                                    plan.toString()
                                },
                                new ByteArrayInputStream(new byte[0]),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err));
        assertEquals(4, code, err.toString());
        assertFalse(Files.exists(root.resolve("new.txt")));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("无法预览"));
    }

    @Test
    void helpVersionAndMissingCancelHaveStableExitCodes() {
        var output = new PrintStream(new ByteArrayOutputStream());
        var app = new CliApplication(Map.of());
        var input = new ByteArrayInputStream(new byte[0]);
        assertEquals(0, app.execute(new String[] {"--help"}, input, output, output));
        assertEquals(0, app.execute(new String[] {"--version"}, input, output, output));
        assertEquals(
                2,
                app.execute(
                        new String[] {
                            "cancel",
                            "--run",
                            UUID.randomUUID().toString(),
                            "--state-dir",
                            root.toString()
                        },
                        input,
                        output,
                        output));
        assertEquals(64, app.execute(new String[] {"--unknown"}, input, output, output));
    }

    @Test
    void configCannotPersistKnownSecretUnderOtherwiseAllowedModelField() {
        var app = new CliApplication(Map.of("TEST_API_KEY", "fixture-private-value"));
        var sink = new PrintStream(new ByteArrayOutputStream());
        assertEquals(
                5,
                app.execute(
                        new String[] {
                            "config",
                            "set",
                            "model",
                            "fixture-private-value",
                            "--workspace",
                            root.toString()
                        },
                        new ByteArrayInputStream(new byte[0]),
                        sink,
                        sink));
        assertFalse(Files.exists(root.resolve(".jharness/config.json")));
    }

    @Test
    void futureFormatIsReadOnlyAndDamagedTailIsNeverAutoRepairedByCli() throws Exception {
        Path directory = root.resolve(".jharness/sessions");
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("future.jsonl"),
                "{\"format\":\"jharness-jsonl\",\"version\":99,\"sessionId\":\"future\"}\n");
        var out = new ByteArrayOutputStream();
        var sink = new PrintStream(out);
        var app = new CliApplication(Map.of());
        assertEquals(
                6,
                app.execute(
                        new String[] {
                            "sessions",
                            "inspect",
                            "--workspace",
                            root.toString(),
                            "--session",
                            "future"
                        },
                        new ByteArrayInputStream(new byte[0]),
                        sink,
                        sink));
        assertTrue(out.toString().contains("\"writable\":false"));
        String damaged =
                "{\"format\":\"jharness-jsonl\",\"version\":1,\"sessionId\":\"tail\"}\n{half";
        Files.writeString(directory.resolve("tail.jsonl"), damaged);
        assertEquals(
                6,
                app.execute(
                        new String[] {
                            "sessions",
                            "inspect",
                            "--workspace",
                            root.toString(),
                            "--session",
                            "tail"
                        },
                        new ByteArrayInputStream(new byte[0]),
                        sink,
                        sink));
        assertEquals(damaged, Files.readString(directory.resolve("tail.jsonl")));
    }

    static SessionEvent event(
            String type, EventLevel level, String step, String call, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                "s",
                type,
                Instant.now(),
                1,
                level,
                "t",
                step,
                call,
                payload);
    }
}
