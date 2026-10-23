package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.jharness.api.*;
import io.jharness.cli.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M4CliIntegrationTest {
    @TempDir Path root;

    @BeforeEach
    void canonicalTemporaryRoot() throws java.io.IOException {
        root = root.toRealPath();
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void terminalApprovedFileAndCommandProduceEvidenceReportAndEverySentRequestReplays()
            throws Exception {
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "write",
                                                "write_file",
                                                Map.of("path", "new.txt", "content", "delivered"))),
                                calls(
                                        call(
                                                "command",
                                                "execute_command",
                                                Map.of("argv", javaCommand("echo")))),
                                text("Model explanation; actually delivered new.txt")))) {
            Path config = config(server);
            var result =
                    execute(
                            config,
                            "y\ny\n",
                            "run",
                            "create file and verify",
                            "--session",
                            "s",
                            "--expect-file",
                            "new.txt",
                            "--verify-argv",
                            JSON.writeValueAsString(javaCommand("echo")));
            assertEquals(0, result.code, result.stderr + result.stdout);
            assertEquals("delivered", Files.readString(root.resolve("new.txt")));
            List<SessionEvent> events = events();
            int sent = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested")) {
                    ChatRequest replay =
                            new EventRequestJournal()
                                    .reconstruct(events.subList(0, i + 1), events.get(i).eventId());
                    var wire = server.requests.get(sent++);
                    assertEquals(replay.parameters().get("model"), wire.get("model"));
                    assertEquals(Map.of("type", "disabled"), wire.get("thinking"));
                    assertEquals(replay.messages().size(), ((List<?>) wire.get("messages")).size());
                }
            assertEquals(3, sent);
            assertFalse(
                    Files.readString(root.resolve(".jharness/sessions/s.jsonl"))
                            .contains("fixture-private-key"));
            var delivery =
                    events.stream()
                            .filter(e -> e.type().equals("delivery.registered"))
                            .findFirst()
                            .orElseThrow();
            Path reportPath = root.resolve((String) delivery.payload().get("path"));
            var report = JSON.readValue(Files.readString(reportPath), Map.class);
            assertEquals("COMPLETED", report.get("status"));
            var facts = Values.object(report.get("recordedFacts"));
            assertEquals(1, ((List<?>) facts.get("changes")).size());
            assertEquals(1, ((List<?>) facts.get("commands")).size());
            assertTrue(
                    Values.object(((List<?>) facts.get("changes")).getFirst())
                            .containsKey("eventId"));
            assertEquals(
                    0, Values.object(((List<?>) facts.get("commands")).getFirst()).get("exitCode"));
            assertTrue(result.stdout.contains("APPROVAL"));
        }
    }

    @Test
    void stdinEofDefaultsToRefusalAndCannotCreateFileOrClaimCompletion() throws Exception {
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "denied",
                                                "write_file",
                                                Map.of("path", "unapproved.txt", "content", "x"))),
                                text("I made unapproved.txt")))) {
            var result =
                    execute(
                            config(server),
                            "",
                            "run",
                            "write file",
                            "--session",
                            "s",
                            "--expect-file",
                            "unapproved.txt");
            assertEquals(2, result.code);
            assertFalse(Files.exists(root.resolve("unapproved.txt")));
            assertTrue(events().stream().noneMatch(e -> e.type().equals("tool.intent")));
            assertTrue(
                    events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("approval.decided")
                                                    && Boolean.FALSE.equals(
                                                            e.payload().get("approved"))));
            assertTrue(result.stdout.contains("modelExplanation"));
        }
    }

    @Test
    void modelDryRunPreviewsExplicitFileAndMarksShellUnpreviewableWithoutEnteringBodies()
            throws Exception {
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "preview",
                                                "write_file",
                                                Map.of("path", "planned.txt", "content", "new")),
                                        call(
                                                "shell",
                                                "execute_command",
                                                Map.of(
                                                        "mode",
                                                        "CMD",
                                                        "argv",
                                                        List.of(
                                                                "echo x > planned.txt & start cmd")))),
                                text("Plan only")))) {
            var result =
                    execute(
                            config(server),
                            "",
                            "run",
                            "plan changes",
                            "--session",
                            "s",
                            "--dry-run");
            assertEquals(4, result.code, result.stderr);
            assertFalse(Files.exists(root.resolve("planned.txt")));
            assertEquals(2, events().stream().filter(e -> e.type().equals("tool.preview")).count());
            assertTrue(
                    events().stream()
                            .noneMatch(
                                    e -> Set.of("tool.intent", "tool.started").contains(e.type())));
            assertTrue(result.stdout.contains("无法预览"));
        }
    }

    @Test
    void disabledProviderAndDoctorFailureNeverSendModelRequest() throws Exception {
        try (var server = new ScriptedServer(List.of())) {
            Path config = config(server);
            Map<String, Object> data =
                    new LinkedHashMap<>(
                            Values.object(JSON.readValue(Files.readString(config), Object.class)));
            data.put(
                    "enabledPlugins",
                    List.of(
                            "registry",
                            "session-jsonl",
                            "workspace-local",
                            "process-local",
                            "policy",
                            "tools-pipeline",
                            "agent-default"));
            Files.writeString(config, JSON.writeValueAsString(data));
            var run = execute(config, "", "run", "should fail");
            assertEquals(5, run.code);
            assertTrue(server.requests.isEmpty());
            var doctor = execute(config, "", "doctor");
            assertEquals(5, doctor.code);
            assertTrue(doctor.stdout.contains("\"pluginAssembly\":false"));
            assertFalse(doctor.stdout.contains("fixture-private-key"));
        }
    }

    @Test
    void unknownCrashCallInspectionAndOperatorVerificationNeverReplayTheTool() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve(".jharness/sessions"));
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            "s",
                            "session.created",
                            Map.of("workspace", root.toRealPath().toString())));
            writer.append(
                    new SessionEvent(
                            "intent",
                            "s",
                            "tool.intent",
                            java.time.Instant.now(),
                            1,
                            EventLevel.TOOL,
                            "t",
                            "p",
                            "crash",
                            Map.of(
                                    "tool",
                                    "write_file",
                                    "sideEffectLevel",
                                    "WORKSPACE_WRITE",
                                    "arguments",
                                    Map.of("path", "already.txt", "content", "done"))));
        }
        Files.writeString(root.resolve("already.txt"), "done");
        try (var server = new ScriptedServer(List.of(text("No more action")))) {
            Path config = config(server);
            var inspect = execute(config, "", "sessions", "inspect", "--session", "s");
            assertEquals(0, inspect.code);
            assertTrue(inspect.stdout.contains("crash"));
            assertEquals(6, execute(config, "", "run", "continue", "--session", "s").code);
            assertTrue(server.requests.isEmpty());
            var verify =
                    execute(
                            config,
                            "",
                            "verify",
                            "--session",
                            "s",
                            "--call",
                            "crash",
                            "--outcome",
                            "APPLIED",
                            "--reason",
                            "Read actual file; done");
            assertEquals(0, verify.code, verify.stderr);
            assertEquals("done", Files.readString(root.resolve("already.txt")));
            assertEquals(
                    List.of(),
                    new EventSessionProjection(repo.openReader("s").events()).uncertainCalls());
            assertTrue(
                    repo.openReader("s").events().stream()
                            .noneMatch(e -> e.type().equals("tool.started")));
        }
    }

    @Test
    void replUsesSameSessionAndAcceptsTwoTurnsWithoutSpawningAnotherInputReaderPerTurn()
            throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(call("read1", "read_file", Map.of("path", "a.txt"))),
                                text("First"),
                                calls(call("read2", "read_file", Map.of("path", "a.txt"))),
                                text("Second")))) {
            var result =
                    execute(
                            config(server),
                            "first\nsecond\n:quit\n",
                            "--session",
                            "s",
                            "--require-read",
                            "a.txt");
            assertEquals(0, result.code, result.stderr);
            assertEquals(2, events().stream().filter(e -> e.type().equals("turn.ended")).count());
            assertEquals(4, server.requests.size());
        }
    }

    @Test
    void replacingAgentFactoryPluginRunsThroughSameCliWithoutAnyModelRequest() throws Exception {
        try (var server = new ScriptedServer(List.of())) {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            var app =
                    new CliApplication(
                            environment(),
                            services ->
                                    new HarnessPlugin() {
                                        @Override
                                        public String id() {
                                            return "agent-default";
                                        }

                                        @Override
                                        public String version() {
                                            return "test";
                                        }

                                        @Override
                                        public void start(PluginContext context) {
                                            context.register(
                                                    AgentLoopFactory.class,
                                                    "replacement",
                                                    deps ->
                                                            cancellation -> {
                                                                deps.session()
                                                                        .append(
                                                                                new SessionEvent(
                                                                                        UUID.randomUUID()
                                                                                                .toString(),
                                                                                        services.scope()
                                                                                                .sessionId(),
                                                                                        "turn.started",
                                                                                        java.time
                                                                                                .Instant
                                                                                                .now(),
                                                                                        1,
                                                                                        EventLevel
                                                                                                .TURN,
                                                                                        "replacement",
                                                                                        null,
                                                                                        null,
                                                                                        Map.of()));
                                                                deps.session()
                                                                        .append(
                                                                                new SessionEvent(
                                                                                        UUID.randomUUID()
                                                                                                .toString(),
                                                                                        services.scope()
                                                                                                .sessionId(),
                                                                                        "turn.ended",
                                                                                        java.time
                                                                                                .Instant
                                                                                                .now(),
                                                                                        1,
                                                                                        EventLevel
                                                                                                .TURN,
                                                                                        "replacement",
                                                                                        null,
                                                                                        null,
                                                                                        Map.of(
                                                                                                "status",
                                                                                                "FAILED",
                                                                                                "reason",
                                                                                                "REPLACEMENT_PROOF")));
                                                            });
                                        }

                                        @Override
                                        public void stop() {}
                                    });
            int code =
                    app.execute(
                            new String[] {
                                "run",
                                "replacement",
                                "--workspace",
                                root.toString(),
                                "--config",
                                config(server).toString(),
                                "--session",
                                "s"
                            },
                            new ByteArrayInputStream(new byte[0]),
                            new PrintStream(out, true, StandardCharsets.UTF_8),
                            new PrintStream(err));
            assertEquals(2, code, err.toString());
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("REPLACEMENT_PROOF"));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void realCliSubprocessCancelsThroughAnotherCliEvenWithStdinStillOpen() throws Exception {
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "blocking",
                                                "execute_command",
                                                Map.of("argv", javaCommand("block"))))))) {
            Path config = config(server);
            var builder =
                    new ProcessBuilder(
                                    java(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    Main.class.getName(),
                                    "run",
                                    "block",
                                    "--workspace",
                                    root.toString(),
                                    "--config",
                                    config.toString(),
                                    "--session",
                                    "s")
                            .redirectErrorStream(true);
            builder.environment().put("JHARNESS_TEST_KEY", "fixture-private-key");
            Process process = builder.start();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var lines = new CopyOnWriteArrayList<String>();
                var collector =
                        executor.submit(
                                () -> {
                                    try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                                        String line;
                                        while ((line = reader.readLine()) != null) lines.add(line);
                                    }
                                    return null;
                                });
                process.getOutputStream().write("y\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                while (!Files.exists(root.resolve("started")) && System.nanoTime() < deadline)
                    Thread.sleep(10);
                assertTrue(Files.exists(root.resolve("started")), lines.toString());
                String id =
                        lines.stream()
                                .filter(l -> l.startsWith("SESSION "))
                                .findFirst()
                                .orElseThrow()
                                .split(" RUN ")[1]
                                .split(" WORKSPACE ")[0];
                Process cancel =
                        new ProcessBuilder(
                                        java(),
                                        "-cp",
                                        System.getProperty("java.class.path"),
                                        Main.class.getName(),
                                        "cancel",
                                        "--workspace",
                                        root.toString(),
                                        "--run",
                                        id)
                                .start();
                try {
                    assertTrue(cancel.waitFor(5, TimeUnit.SECONDS));
                    assertEquals(0, cancel.exitValue());
                } finally {
                    cancel.destroyForcibly();
                }
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), lines.toString());
                collector.get(2, TimeUnit.SECONDS);
                assertEquals(3, process.exitValue(), lines.toString());
                assertTrue(
                        events().stream()
                                .anyMatch(
                                        e ->
                                                e.type().equals("turn.ended")
                                                        && "CANCELLED"
                                                                .equals(
                                                                        e.payload()
                                                                                .get("status"))));
            } finally {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    record Result(int code, String stdout, String stderr) {}

    @Test
    void actualCliCrashAfterCommitRequiresVerificationAndColdResumeOnlyReadsExistingFile()
            throws Exception {
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "crash",
                                                "write_file",
                                                Map.of(
                                                        "path",
                                                        "committed.txt",
                                                        "content",
                                                        "real commit"))),
                                calls(
                                        call(
                                                "after-verify",
                                                "read_file",
                                                Map.of("path", "committed.txt"))),
                                text("Verified existing committed file")))) {
            Path config = config(server);
            var builder =
                    new ProcessBuilder(
                                    java(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    M4CliCrashProbe.class.getName(),
                                    "run",
                                    "write",
                                    "--workspace",
                                    root.toString(),
                                    "--config",
                                    config.toString(),
                                    "--session",
                                    "s")
                            .redirectErrorStream(true)
                            .redirectOutput(root.resolve("crash.log").toFile());
            builder.environment().put("JHARNESS_TEST_KEY", "fixture-private-key");
            Process process = builder.start();
            try {
                process.getOutputStream().write("y\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                assertTrue(process.waitFor(8, TimeUnit.SECONDS));
                assertEquals(23, process.exitValue());
            } finally {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            assertEquals("real commit", Files.readString(root.resolve("committed.txt")));
            assertTrue(events().stream().noneMatch(e -> e.type().equals("tool.completed")));
            assertEquals(6, execute(config, "", "run", "continue", "--session", "s").code);
            assertEquals(1, server.requests.size());
            assertEquals(
                    0,
                    execute(
                                    config,
                                    "",
                                    "verify",
                                    "--session",
                                    "s",
                                    "--call",
                                    "crash",
                                    "--outcome",
                                    "APPLIED",
                                    "--reason",
                                    "Actual file checked")
                            .code);
            assertEquals(
                    0,
                    execute(
                                    config,
                                    "",
                                    "run",
                                    "Read restored file",
                                    "--session",
                                    "s",
                                    "--require-read",
                                    "committed.txt")
                            .code);
            assertEquals(
                    1,
                    events().stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.started")
                                                    && "crash".equals(e.callId()))
                            .count());
            assertEquals("real commit", Files.readString(root.resolve("committed.txt")));
        }
    }

    @Test
    void terminalCancelAndSecretCallIdsAreHandledWithoutBlockingOrDisclosure() throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "fixture-private-key",
                                                "read_file",
                                                Map.of("path", "a.txt"))),
                                text("Read")))) {
            var result =
                    execute(
                            config(server),
                            "",
                            "run",
                            "read",
                            "--session",
                            "s",
                            "--require-read",
                            "a.txt");
            // Sensitive provider identities are rejected before acceptance, rather than
            // silently redacted into potentially colliding correlation identifiers.
            assertEquals(2, result.code);
            assertEquals(1, server.requests.size());
            assertTrue(events().stream().noneMatch(e -> e.type().equals("tool.started")));
            assertTrue(
                    new io.jharness.session.EventSessionProjection(events())
                            .uncertainCalls()
                            .isEmpty());
            assertFalse(result.stdout.contains("fixture-private-key"));
            assertFalse(result.stderr.contains("fixture-private-key"));
            try (var reports = Files.walk(root.resolve(".jharness/reports"))) {
                for (var report : reports.filter(Files::isRegularFile).toList())
                    assertFalse(Files.readString(report).contains("fixture-private-key"));
            }
            assertFalse(
                    Files.readString(root.resolve(".jharness/sessions/s.jsonl"))
                            .contains("fixture-private-key"));
        }
        try (var server =
                new ScriptedServer(
                        List.of(
                                calls(
                                        call(
                                                "block",
                                                "execute_command",
                                                Map.of("argv", javaCommand("block"))))))) {
            var result =
                    execute(
                            config(server),
                            "y\n:cancel\n",
                            "run",
                            "block",
                            "--session",
                            "cancel-session");
            assertEquals(3, result.code, result.stderr);
        }
    }

    Result execute(Path config, String input, String... args) {
        List<String> argv = new ArrayList<>(List.of(args));
        argv.addAll(List.of("--workspace", root.toString(), "--config", config.toString()));
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code =
                new CliApplication(environment())
                        .execute(
                                argv.toArray(String[]::new),
                                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(
                code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    Path config(ScriptedServer server) throws Exception {
        Path config = root.resolve("config.json");
        Files.writeString(
                config,
                JSON.writeValueAsString(
                        Map.of(
                                "baseUrl",
                                server.url(),
                                "model",
                                "fixture",
                                "apiKeyEnv",
                                "JHARNESS_TEST_KEY",
                                "disableThinking",
                                true,
                                "contextTokens",
                                65536,
                                "requestTimeoutSeconds",
                                10)));
        return config;
    }

    List<SessionEvent> events() throws Exception {
        return new JsonlSessionRepository(root.resolve(".jharness/sessions"))
                .openReader("s")
                .events();
    }

    Map<String, String> environment() {
        var env = new HashMap<>(System.getenv());
        env.put("JHARNESS_TEST_KEY", "fixture-private-key");
        return env;
    }

    static String java() {
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
    }

    static List<String> javaCommand(String mode) {
        return List.of(
                java(),
                "-cp",
                System.getProperty("java.class.path"),
                M3CommandFixture.class.getName(),
                mode);
    }

    static Map<String, Object> call(String id, String name, Map<String, Object> args) {
        return Map.of(
                "id",
                id,
                "type",
                "function",
                "function",
                Map.of("name", name, "arguments", json(args)));
    }

    static Map<String, Object> calls(Object... calls) {
        return response(
                Map.of("role", "assistant", "content", "", "tool_calls", List.of(calls)),
                "tool_calls");
    }

    static Map<String, Object> text(String text) {
        return response(Map.of("role", "assistant", "content", text), "stop");
    }

    static Map<String, Object> response(Map<String, Object> message, String finish) {
        return Map.of(
                "choices",
                List.of(Map.of("message", message, "finish_reason", finish)),
                "usage",
                Map.of("prompt_tokens", 100, "completion_tokens", 20));
    }

    static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static final class ScriptedServer implements AutoCloseable {
        final HttpServer server;
        final Queue<Map<String, Object>> responses;
        final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();

        ScriptedServer(List<Map<String, Object>> responses) throws Exception {
            this.responses = new ConcurrentLinkedQueue<>(responses);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/chat/completions",
                    exchange -> {
                        try {
                            requests.add(
                                    Values.object(
                                            JSON.readValue(
                                                    exchange.getRequestBody()
                                                            .readNBytes(1024 * 1024),
                                                    Object.class)));
                            var next = this.responses.poll();
                            byte[] bytes =
                                    json(next == null ? Map.of("error", "exhausted") : next)
                                            .getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(next == null ? 500 : 200, bytes.length);
                            exchange.getResponseBody().write(bytes);
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
