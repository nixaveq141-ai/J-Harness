package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.cli.CliApplication;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M8FilterIntegrationTest {
    @TempDir Path root;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void classicAndEcosystemCliUseIdenticalFiltersAndRecordTheConfiguration() throws Exception {
        for (boolean ecology : List.of(false, true)) {
            Path workspace = Files.createDirectories(root.resolve(ecology ? "eco" : "classic"));
            Files.writeString(workspace.resolve("input.txt"), "evidence fixture-key");
            Files.writeString(workspace.resolve("hidden.txt"), "not discovered");
            try (var server =
                    new M4CliIntegrationTest.ScriptedServer(
                            List.of(
                                    M4CliIntegrationTest.calls(
                                            M4CliIntegrationTest.call(
                                                    "search",
                                                    "search_files",
                                                    Map.of("glob", "*.txt", "limit", 10)),
                                            M4CliIntegrationTest.call(
                                                    "read",
                                                    "read_file",
                                                    Map.of("path", "input.txt"))),
                                    M4CliIntegrationTest.text("done")))) {
                var config = new LinkedHashMap<String, Object>();
                config.put("baseUrl", "http://127.0.0.1:" + server.server.getAddress().getPort());
                config.put("model", "fixture");
                config.put("apiKeyEnv", "FIXTURE_API_KEY");
                config.put("searchFilter", Map.of("rules", List.of("hidden.txt")));
                config.put(
                        "toolScheduling", Map.of("mode", "readonly-parallel", "maxConcurrency", 2));
                if (ecology) config.put("ecosystem", Map.of());
                Files.writeString(
                        workspace.resolve("config.json"), JSON.writeValueAsString(config));
                var output = new ByteArrayOutputStream();
                try (var out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                    int code =
                            new CliApplication(Map.of("FIXTURE_API_KEY", "fixture-key"))
                                    .execute(
                                            new String[] {
                                                "run",
                                                "discover",
                                                "--workspace",
                                                workspace.toString(),
                                                "--config",
                                                workspace.resolve("config.json").toString(),
                                                "--session",
                                                "s",
                                                "--require-read",
                                                "input.txt"
                                            },
                                            new ByteArrayInputStream(new byte[0]),
                                            out,
                                            out);
                    assertEquals(0, code, output.toString(StandardCharsets.UTF_8));
                }
                var events =
                        new JsonlSessionRepository(workspace.resolve(".jharness/sessions"))
                                .openReader("s")
                                .events();
                assertFalse(events.toString().contains("fixture-key"));
                assertFalse(
                        Files.readString(workspace.resolve(".jharness/sessions/s.jsonl"))
                                .contains("fixture-key"));
                assertFalse(output.toString(StandardCharsets.UTF_8).contains("fixture-key"));
                assertTrue(
                        events.stream()
                                .filter(
                                        e ->
                                                e.type().equals("tool.completed")
                                                        && "read".equals(e.callId()))
                                .anyMatch(e -> e.payload().toString().contains("[REDACTED]")));
                var search =
                        events.stream()
                                .filter(
                                        e ->
                                                e.type().equals("tool.completed")
                                                        && "search".equals(e.callId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(
                        List.of("input.txt"),
                        Values.object(search.payload().get("value")).get("paths"));
                assertTrue(
                        events.stream()
                                .filter(e -> e.type().equals("config.selected"))
                                .anyMatch(e -> e.payload().toString().contains("hidden.txt")));
                assertEquals(
                        2, events.stream().filter(e -> e.type().equals("model.requested")).count());
                assertEquals(
                        2,
                        events.stream()
                                .filter(
                                        e ->
                                                e.type().equals("tool.scheduled")
                                                        && "readonly-parallel"
                                                                .equals(e.payload().get("mode")))
                                .count());
            }
        }
    }

    @Test
    void parallelConfigurationCannotBypassClassicDryRun() throws Exception {
        Files.writeString(root.resolve("input.txt"), "evidence");
        try (var server =
                new M4CliIntegrationTest.ScriptedServer(
                        List.of(
                                M4CliIntegrationTest.calls(
                                        M4CliIntegrationTest.call(
                                                "read", "read_file", Map.of("path", "input.txt")),
                                        M4CliIntegrationTest.call(
                                                "write",
                                                "write_file",
                                                Map.of(
                                                        "path",
                                                        "marker.txt",
                                                        "content",
                                                        "must not execute"))),
                                M4CliIntegrationTest.text("done")))) {
            Files.writeString(
                    root.resolve("config.json"),
                    JSON.writeValueAsString(
                            Map.of(
                                    "baseUrl",
                                    "http://127.0.0.1:" + server.server.getAddress().getPort(),
                                    "model",
                                    "fixture",
                                    "apiKeyEnv",
                                    "FIXTURE_API_KEY",
                                    "toolScheduling",
                                    Map.of("mode", "readonly-parallel"))));
            var output = new ByteArrayOutputStream();
            try (var out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                assertEquals(
                        4,
                        new CliApplication(Map.of("FIXTURE_API_KEY", "fixture-key"))
                                .execute(
                                        new String[] {
                                            "run",
                                            "preview",
                                            "--workspace",
                                            root.toString(),
                                            "--config",
                                            root.resolve("config.json").toString(),
                                            "--session",
                                            "dry",
                                            "--dry-run"
                                        },
                                        new ByteArrayInputStream(new byte[0]),
                                        out,
                                        out),
                        output.toString(StandardCharsets.UTF_8));
            }
            assertFalse(Files.exists(root.resolve("marker.txt")));
            var events =
                    new JsonlSessionRepository(root.resolve(".jharness/sessions"))
                            .openReader("dry")
                            .events();
            assertTrue(
                    events.stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.preview")
                                                    && "write".equals(e.callId())));
            assertFalse(
                    events.stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.intent")
                                                    && "write".equals(e.callId())));
            assertTrue(
                    events.stream()
                            .filter(e -> e.type().equals("tool.scheduled"))
                            .allMatch(e -> "serial".equals(e.payload().get("mode"))));
        }
    }

    @Test
    void workerProcessAcknowledgesAndAppliesHostFilterAndOldWorkerIsRejected() throws Exception {
        Files.writeString(root.resolve("keep.txt"), "evidence");
        Files.writeString(root.resolve("hidden.txt"), "private");
        var filter = new WorkspaceSearchFilter(true, false, List.of("hidden.txt"));
        var request =
                Map.of("op", "search", "glob", "*.txt", "searchFilter", filter.configuration());
        var answer =
                new NativeCommandTransport()
                        .call(
                                List.of(
                                        M3RuntimeIntegrationTest.java(),
                                        "-cp",
                                        System.getProperty("java.class.path"),
                                        EnvironmentWorker.class.getName(),
                                        root.toString()),
                                JSON.writeValueAsBytes(request),
                                Duration.ofSeconds(10),
                                CancellationToken.NONE);
        assertEquals(0, answer.exitCode());
        var body = JSON.readValue(answer.stdout(), new TypeReference<Map<String, Object>>() {});
        assertEquals(filter.configuration(), body.get("searchFilter"));
        assertEquals(List.of("keep.txt"), Values.object(body.get("value")).get("paths"));
        var calls = new ArrayList<List<String>>();
        Path jar = root.resolve("worker.jar");
        Files.writeString(jar, "fixture");
        CommandTransport old =
                (argv, input, timeout, token) -> {
                    calls.add(argv);
                    return new CommandTransport.Reply(
                            0,
                            "{\"value\":{}}".getBytes(StandardCharsets.UTF_8),
                            new byte[0],
                            true,
                            false);
                };
        assertEquals(
                "ENVIRONMENT_FILTER_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new DockerExecutionEnvironment(
                                                "docker",
                                                "temurin@sha256:" + "a".repeat(64),
                                                root,
                                                jar,
                                                old,
                                                filter))
                        .code());
        assertEquals(3, calls.size());
        assertEquals("run", calls.get(0).get(1));
        assertEquals("exec", calls.get(1).get(1));
        assertEquals("rm", calls.get(2).get(1));
    }

    @Test
    void sdkBindingFreezesNondefaultFilterWhileDefaultDescriptorsStayCompatible() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var filter = new WorkspaceSearchFilter(true, true, List.of("hidden.txt"));
        try (var env = new LocalExecutionEnvironment("one", root, List.of(), filter);
                var sdk = sdk(repo, env)) {
            assertEquals(filter.configuration(), env.descriptor().get("searchFilter"));
            assertNotNull(sdk.snapshot());
        }
        try (var env = new LocalExecutionEnvironment("two", root, List.of())) {
            assertFalse(env.descriptor().containsKey("searchFilter"));
            assertEquals(
                    "ENVIRONMENT_MISMATCH",
                    assertThrows(HarnessException.class, () -> sdk(repo, env)).code());
        }
    }

    private HarnessSdk sdk(JsonlSessionRepository repo, ExecutionEnvironment env) throws Exception {
        return new HarnessSdk(
                "s",
                "binding",
                repo,
                env,
                new FakeLlm(List.of()),
                new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor()),
                new SecretRedactor(),
                new DefaultPolicyEngine(),
                ApprovalService.rejectAll(),
                new Budget(2, Duration.ofSeconds(10)),
                (reply, projection) -> new CompletionEvaluator.CompletionDecision(false, "no task"),
                ApiCostGuard.unavailable(),
                List.of());
    }
}
