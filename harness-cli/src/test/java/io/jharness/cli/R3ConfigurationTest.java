package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R3ConfigurationTest {
    @TempDir Path root;

    @Test
    void configurationBoundariesAndWholeObjectReplacementAreExact() {
        var upper =
                Map.<String, Object>of(
                        "enabled",
                        true,
                        "maxAttempts",
                        4,
                        "backoffMillis",
                        10000,
                        "totalTimeoutMillis",
                        300000,
                        "minIntervalMillis",
                        60000,
                        "maxWaiters",
                        32);
        var configured =
                new CliConfig(
                        Map.of(
                                "modelResilience",
                                upper,
                                "profiles",
                                Map.of(
                                        "p",
                                        Map.of(
                                                "settings",
                                                Map.of(
                                                        "modelResilience",
                                                        Map.of("enabled", false))))));
        assertEquals(
                new ModelResilienceOptions(true, 4, 10000, 300000, 60000, 32),
                configured.resilience());
        assertEquals(
                ModelResilienceOptions.disabled(),
                ConfigurationSelection.resolve(
                                configured, new Arguments(new String[] {"--profile", "p"}))
                        .resilience());
        assertEquals(
                new ModelResilienceOptions(false, 1, 0, 50, 0, 0),
                ModelResilienceOptions.from(Map.of("backoffMillis", 0, "totalTimeoutMillis", 50)));
        assertEquals(
                "INVALID_MODEL_RESILIENCE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new CliConfig(
                                                Map.of(
                                                        "enabledPlugins",
                                                        List.of("registry"),
                                                        "modelResilience",
                                                        Map.of("enabled", true))))
                        .code());
        assertTrue(
                new CliConfig(Map.of("modelResilience", Map.of("enabled", true)))
                        .resilience()
                        .enabled());
        var scheduling =
                ToolSchedulingOptions.from(
                        Map.of(
                                "mode",
                                "readonly-parallel",
                                "maxConcurrency",
                                8,
                                "timeoutSeconds",
                                60));
        assertEquals(8, scheduling.maxConcurrency());
        assertEquals(java.time.Duration.ofSeconds(60), scheduling.timeout());
        assertEquals(ToolSchedulingOptions.serial(), new CliConfig(Map.of()).scheduling());
        for (int n : List.of(0, -1))
            assertEquals(
                    "INVALID_TOOL_SCHEDULING",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            ToolSchedulingOptions.from(
                                                    Map.of(
                                                            "mode",
                                                            "readonly-parallel",
                                                            "maxConcurrency",
                                                            n)))
                            .code());
        assertEquals(
                "INVALID_TOOL_SCHEDULING",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ToolSchedulingOptions.from(
                                                Map.of(
                                                        "mode",
                                                        "readonly-parallel",
                                                        "unknown",
                                                        true)))
                        .code());
        assertEquals(
                "INVALID_CONFIG",
                assertThrows(
                                HarnessException.class,
                                () -> new CliConfig(Map.of("apiKeyEnv", "INVALID-NAME")))
                        .code());
        assertEquals("PATH", new CliConfig(Map.of("apiKeyEnv", "PATH")).string("apiKeyEnv", ""));
    }

    @Test
    void filterGrammarAndSelectionFailuresHavePreciseCodes() {
        assertEquals(
                List.of("/etc/passwd"),
                io.jharness.workspace.WorkspaceSearchFilter.from(
                                Map.of("rules", List.of("# comment", "", "/etc/passwd")))
                        .rules());
        for (Object bad :
                List.of(
                        Map.of("rules", "bad"),
                        Map.of("useGitignore", "yes"),
                        Map.of("rules", List.of("[bad]"))))
            assertEquals(
                    "INVALID_SEARCH_FILTER",
                    assertThrows(
                                    HarnessException.class,
                                    () -> io.jharness.workspace.WorkspaceSearchFilter.from(bad))
                            .code());
        var source =
                new CliConfig(
                        Map.of(
                                "profiles",
                                Map.of("safe", Map.of("settings", Map.of("policy", "read-only"))),
                                "bundles",
                                Map.of("normal", Map.of("policy", "default"))));
        assertEquals(
                "CONFIG_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ConfigurationSelection.resolve(
                                                source,
                                                new Arguments(
                                                        new String[] {
                                                            "--profile",
                                                            "safe",
                                                            "--bundle",
                                                            "normal"
                                                        })))
                        .code());
        assertEquals(
                "CONFIG_SELECTION_NOT_FOUND",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ConfigurationSelection.resolve(
                                                source,
                                                new Arguments(
                                                        new String[] {"--profile", "missing"})))
                        .code());
    }

    @Test
    void offlinePreviewNeedsNoCredentialAndExplicitMissingConfigCannotFallBack() throws Exception {
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
                                                Map.of(
                                                        "path",
                                                        "new.txt",
                                                        "content",
                                                        "preview only"))))));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        var app = new CliApplication(Map.of());
        assertEquals(
                4,
                app.execute(
                        new String[] {
                            "run",
                            "goal",
                            "--workspace",
                            root.toString(),
                            "--dry-run",
                            "--plan",
                            plan.toString()
                        },
                        InputStream.nullInputStream(),
                        new PrintStream(output, true, StandardCharsets.UTF_8),
                        new PrintStream(errors, true, StandardCharsets.UTF_8)));
        var report = Values.object(Json.parse(output.toString(StandardCharsets.UTF_8)));
        assertEquals(true, report.get("dryRun"));
        assertEquals(false, report.get("executed"));
        assertEquals(
                true, Values.object(((List<?>) report.get("plans")).getFirst()).get("previewable"));
        assertFalse(Files.exists(root.resolve("new.txt")));
        assertFalse(Files.exists(root.resolve(".jharness/sessions")));
        output.reset();
        assertEquals(
                5,
                app.execute(
                        new String[] {
                            "run",
                            "goal",
                            "--workspace",
                            root.toString(),
                            "--config",
                            root.resolve("absent.json").toString()
                        },
                        InputStream.nullInputStream(),
                        new PrintStream(output),
                        new PrintStream(errors, true, StandardCharsets.UTF_8)));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("CONFIG_NOT_FOUND"));
        assertFalse(Files.exists(root.resolve(".jharness/sessions")));
    }

    @Test
    void exactFrameLimitAndShutdownAreTerminalAndCorrelated() throws Exception {
        String init =
                Json.text(
                                Map.of(
                                        "jsonrpc",
                                        "2.0",
                                        "id",
                                        "init",
                                        "method",
                                        "initialize",
                                        "params",
                                        Map.of("protocolVersion", "1.0")))
                        + "\n";
        String prefix =
                Json.text(
                        Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                "max",
                                "method",
                                "unknown",
                                "params",
                                Map.of("text", "")));
        int padding = 65536 - prefix.getBytes(StandardCharsets.UTF_8).length;
        String exact =
                Json.text(
                        Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                "max",
                                "method",
                                "unknown",
                                "params",
                                Map.of("text", "x".repeat(padding))));
        assertEquals(65536, exact.getBytes(StandardCharsets.UTF_8).length);
        String shutdown =
                Json.text(
                                Map.of(
                                        "jsonrpc",
                                        "2.0",
                                        "id",
                                        "stop",
                                        "method",
                                        "shutdown",
                                        "params",
                                        Map.of()))
                        + "\n";
        var output = new ByteArrayOutputStream();
        assertEquals(
                0,
                new StdioSdkServer(URI.create("http://127.0.0.1:1"), "fixture")
                        .run(
                                new ByteArrayInputStream(
                                        (init + exact + "\n" + shutdown + init)
                                                .getBytes(StandardCharsets.UTF_8)),
                                new PrintStream(output, true, StandardCharsets.UTF_8)));
        var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).contains("METHOD_NOT_FOUND"));
        assertEquals(
                Map.of("closing", true), Values.object(Json.parse(lines.get(2))).get("result"));
        output.reset();
        assertEquals(
                2,
                new StdioSdkServer(URI.create("http://127.0.0.1:1"), "fixture")
                        .run(
                                new ByteArrayInputStream(
                                        (exact + " " + "\n" + init)
                                                .getBytes(StandardCharsets.UTF_8)),
                                new PrintStream(output, true, StandardCharsets.UTF_8)));
        assertEquals(1, output.toString(StandardCharsets.UTF_8).lines().count());
        assertTrue(output.toString(StandardCharsets.UTF_8).endsWith("\n"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("RPC_REQUEST_LIMIT"));
    }

    @Test
    void nestedDuplicateParamsDoNotInitializeProtocol() throws Exception {
        String bad =
                "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1.0\",\"protocolVersion\":\"1.0\"}}\n";
        String good =
                Json.text(
                                Map.of(
                                        "jsonrpc",
                                        "2.0",
                                        "id",
                                        "init",
                                        "method",
                                        "initialize",
                                        "params",
                                        Map.of("protocolVersion", "1.0")))
                        + "\n";
        var output = new ByteArrayOutputStream();
        assertEquals(
                0,
                new StdioSdkServer(URI.create("http://127.0.0.1:1"), "fixture")
                        .run(
                                new ByteArrayInputStream(
                                        (bad + good).getBytes(StandardCharsets.UTF_8)),
                                new PrintStream(output, true, StandardCharsets.UTF_8)));
        var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().contains("INVALID_JSON"));
        assertTrue(Values.object(Json.parse(lines.get(1))).containsKey("result"));
    }

    @Test
    void invalidProfileAndPresetFiltersFailAfterSelectionWithoutMutatingCatalog() {
        for (String kind : List.of("profiles", "presets")) {
            var settings = new LinkedHashMap<String, Object>();
            settings.put("searchFilter", Map.of("unknown", true));
            var source = new CliConfig(Map.of(kind, Map.of("bad", Map.of("settings", settings))));
            settings.clear();
            assertEquals(
                    "INVALID_SEARCH_FILTER",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            ConfigurationSelection.resolve(
                                                    source,
                                                    new Arguments(
                                                            new String[] {
                                                                kind.equals("profiles")
                                                                        ? "--profile"
                                                                        : "--preset",
                                                                "bad"
                                                            })))
                            .code());
            assertTrue(
                    Values.object(
                                    Values.object(Values.object(source.values.get(kind)).get("bad"))
                                            .get("settings"))
                            .containsKey("searchFilter"));
        }
    }

    @Test
    void optionValuesCannotConsumeFlagsAndEqualsProvidesLiteralValues() {
        assertEquals(
                "CLI_USAGE",
                assertThrows(
                                HarnessException.class,
                                () -> new Arguments(new String[] {"--workspace", "--dry-run"}))
                        .code());
        assertEquals("--dry-run", new Arguments(new String[] {"--text=--dry-run"}).get("text", ""));
        var args = new Arguments(new String[] {"--bundle", "a"});
        var snapshot = args.all("bundle");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("b"));
        assertEquals(
                List.of("--literal"), new Arguments(new String[] {"--", "--literal"}).positional);
    }

    @Test
    void policyOnlyBundlesPreserveToolsAndBaseRouteAndFingerprintScope() {
        var source =
                new CliConfig(
                        Map.of(
                                "model",
                                "base-model",
                                "enabledTools",
                                List.of("read_file"),
                                "prompts",
                                List.of("base prompt"),
                                "bundles",
                                Map.of("safe", Map.of("policy", "read-only")),
                                "models",
                                Map.of("unused", Map.of("model", "other"))));
        var result =
                ConfigurationSelection.resolve(
                        source,
                        new Arguments(new String[] {"--bundle", "safe", "--model-route", "base"}));
        assertEquals("base-model", result.values.get("model"));
        assertEquals(List.of("read_file"), result.values.get("enabledTools"));
        assertEquals(List.of("base prompt"), result.values.get("prompts"));
        var changedCatalog = new LinkedHashMap<>(source.values);
        changedCatalog.put("models", Map.of("unused", Map.of("model", "changed")));
        var same =
                ConfigurationSelection.resolve(
                        new CliConfig(changedCatalog),
                        new Arguments(new String[] {"--bundle", "safe"}));
        assertEquals(result.selection.get("resolvedSha256"), same.selection.get("resolvedSha256"));
        assertNotEquals(
                result.selection.get("resolvedSha256"),
                ConfigurationSelection.resolve(source, new Arguments(new String[] {}))
                        .selection
                        .get("resolvedSha256"));
        assertEquals(
                "INVALID_CONFIG",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ConfigurationSelection.resolve(
                                                new CliConfig(
                                                        Map.of(
                                                                "profiles",
                                                                Map.of("p", Map.of("preset", 7)))),
                                                new Arguments(new String[] {"--profile", "p"})))
                        .code());
    }

    @Test
    void validRequestIdSurvivesBadEnvelopeAndInvalidApprovalHasNoHttpCall() throws Exception {
        var frames =
                List.of(
                        Map.of(
                                "jsonrpc",
                                "wrong",
                                "id",
                                "correlated",
                                "method",
                                "initialize",
                                "params",
                                Map.of()),
                        Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                "init",
                                "method",
                                "initialize",
                                "params",
                                Map.of("protocolVersion", "1.0")),
                        Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                "approve",
                                "method",
                                "approval.decide",
                                "params",
                                Map.of(
                                        "sessionId",
                                        "s",
                                        "approvalId",
                                        "a",
                                        "callId",
                                        "c",
                                        "approved",
                                        "yes",
                                        "reason",
                                        "reason")));
        var input = String.join("\n", frames.stream().map(Json::text).toList()) + "\n";
        var output = new ByteArrayOutputStream();
        assertEquals(
                0,
                new StdioSdkServer(URI.create("http://127.0.0.1:1"), "synthetic-test")
                        .run(
                                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                                new PrintStream(output, true, StandardCharsets.UTF_8)));
        var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(3, lines.size());
        assertEquals("correlated", Values.object(Json.parse(lines.getFirst())).get("id"));
        assertTrue(lines.get(2).contains("INVALID_PARAMS"));
    }
}
