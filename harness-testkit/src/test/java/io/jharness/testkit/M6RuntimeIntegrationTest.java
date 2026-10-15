package io.jharness.testkit;

import static io.jharness.testkit.M4CliIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.jharness.api.*;
import io.jharness.session.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M6RuntimeIntegrationTest {
    @TempDir Path root;
    private static final ObjectMapper JSON = new ObjectMapper();

    static final class Server implements AutoCloseable {
        final HttpServer server;
        final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();

        Server(Function<List<Map<String, Object>>, String> replies) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/chat/completions",
                    exchange -> {
                        requests.add(
                                Values.object(
                                        JSON.readValue(exchange.getRequestBody(), Map.class)));
                        String body = replies.apply(requests);
                        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                        exchange.sendResponseHeaders(200, 0);
                        try {
                            for (byte b : body.getBytes(StandardCharsets.UTF_8)) {
                                exchange.getResponseBody().write(b);
                                exchange.getResponseBody().flush();
                            }
                        } catch (IOException ignored) {
                            /* cancelled stream */
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

    static String delta(Map<String, Object> value, String finish) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("index", 0);
        choice.put("delta", value);
        choice.put("finish_reason", finish);
        return "data: " + json(Map.of("choices", List.of(choice))) + "\n\n";
    }

    static String tool(String id, String name, Map<String, Object> arguments) {
        String args = json(arguments);
        int split = args.length() / 2;
        return delta(
                        Map.of(
                                "tool_calls",
                                List.of(
                                        Map.of(
                                                "index",
                                                0,
                                                "id",
                                                id,
                                                "type",
                                                "function",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        name,
                                                        "arguments",
                                                        args.substring(0, split))))),
                        null)
                + delta(
                        Map.of(
                                "tool_calls",
                                List.of(
                                        Map.of(
                                                "index",
                                                0,
                                                "function",
                                                Map.of("arguments", args.substring(split))))),
                        null)
                + delta(Map.of(), "tool_calls")
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}\n\ndata: [DONE]\n\n";
    }

    static String textStream(String value) {
        return delta(Map.of("content", value), null) + delta(Map.of(), "stop") + "data: [DONE]\n\n";
    }

    static List<Map<String, Object>> normalizedMessages(Object raw) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            Map<String, Object> message = new LinkedHashMap<>(Values.object(item));
            if (message.get("tool_calls") instanceof List<?> calls) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Object entry : calls) {
                    Map<String, Object> call = new LinkedHashMap<>(Values.object(entry));
                    Map<String, Object> function =
                            new LinkedHashMap<>(Values.object(call.get("function")));
                    function.put(
                            "arguments",
                            JSON.readValue((String) function.get("arguments"), Map.class));
                    call.put("function", function);
                    normalized.add(call);
                }
                message.put("tool_calls", normalized);
            }
            result.add(message);
        }
        return result;
    }

    private M4CliIntegrationTest fixture() {
        var fixture = new M4CliIntegrationTest();
        fixture.root = root;
        return fixture;
    }

    private Path config(Server server, Map<String, Object> overrides) throws Exception {
        var config = new LinkedHashMap<String, Object>();
        config.put("baseUrl", server.url());
        config.put("model", "fixture");
        config.put("apiKeyEnv", "JHARNESS_TEST_KEY");
        config.put("streaming", true);
        config.put("streamSupported", true);
        config.put("disableThinking", true);
        config.putAll(overrides);
        Path path = root.resolve("config.json");
        Files.writeString(path, json(config));
        return path;
    }

    private List<SessionEvent> events() throws Exception {
        return new JsonlSessionRepository(root.resolve(".jharness/sessions"))
                .openReader("s")
                .events();
    }

    @Test
    void cliStreamsCompactsOneLongTurnAndEveryWireRequestReplaysExactly() throws Exception {
        Files.writeString(root.resolve("note.txt"), "evidence " + "x".repeat(1100));
        try (var server =
                new Server(
                        requests ->
                                requests.size() <= 8
                                        ? tool(
                                                "read-" + requests.size(),
                                                "read_file",
                                                Map.of("path", "note.txt"))
                                        : textStream("Finished using recorded reads"))) {
            var result =
                    fixture()
                            .execute(
                                    config(
                                            server,
                                            Map.of(
                                                    "contextTokens",
                                                    8192,
                                                    "compactionCharacters",
                                                    4500,
                                                    "compactContext",
                                                    true,
                                                    "enabledTools",
                                                    List.of("read_file"))),
                                    "",
                                    "run",
                                    "inspect note repeatedly",
                                    "--session",
                                    "s",
                                    "--require-read",
                                    "note.txt");
            assertEquals(0, result.code(), result.stderr() + result.stdout());
            var events = events();
            assertTrue(events.stream().anyMatch(e -> e.type().equals("context.compacted")));
            int index = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested")) {
                    var reconstructed =
                            new EventRequestJournal()
                                    .reconstruct(events.subList(0, i + 1), events.get(i).eventId());
                    var wire = server.requests.get(index++);
                    assertEquals(
                            reconstructed.messages(), normalizedMessages(wire.get("messages")));
                    assertEquals(reconstructed.tools(), wire.get("tools"));
                    assertEquals(true, wire.get("stream"));
                    assertEquals(reconstructed.parameters().get("model"), wire.get("model"));
                }
            assertEquals(9, index);
            assertEquals(9, events.stream().filter(e -> e.type().equals("model.observed")).count());
            assertTrue(
                    events.stream()
                            .filter(e -> e.type().equals("model.observed"))
                            .allMatch(
                                    e ->
                                            !e.payload().containsKey("messages")
                                                    && !e.payload().containsKey("content")));
            assertTrue(events.stream().anyMatch(e -> e.type().equals("config.selected")));
            assertFalse(
                    Files.readString(root.resolve(".jharness/sessions/s.jsonl"))
                            .contains("fixture-private-key"));
        }
    }

    @Test
    void truncatedToolArgumentsNeverReachApprovalIntentOrFileBody() throws Exception {
        String partial =
                delta(
                        Map.of(
                                "tool_calls",
                                List.of(
                                        Map.of(
                                                "index",
                                                0,
                                                "id",
                                                "partial",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        "write_file",
                                                        "arguments",
                                                        "{\"path\":\"unsafe.txt\"")))),
                        null);
        try (var server = new Server(requests -> partial)) {
            var config = config(server, Map.of());
            var result =
                    fixture()
                            .execute(
                                    config,
                                    "y\n",
                                    "run",
                                    "write",
                                    "--session",
                                    "s",
                                    "--expect-file",
                                    "unsafe.txt");
            assertEquals(2, result.code(), result.stderr() + result.stdout());
            assertEquals(1, server.requests.size());
            assertFalse(Files.exists(root.resolve("unsafe.txt")));
            assertTrue(
                    events().stream()
                            .noneMatch(
                                    e ->
                                            e.type().equals("tool.intent")
                                                    || e.type().equals("tool.started")
                                                    || e.type().equals("approval.requested")));
            assertEquals(
                    6,
                    fixture().execute(config, "", "sessions", "recover", "--session", "s").code());
        }
    }

    @Test
    void approvedBackgroundToolsPersistFinalStateAndColdTailArtifact() throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        try (var server =
                new Server(
                        requests -> {
                            if (requests.size() == 1)
                                return tool(
                                        "start",
                                        "terminal_start",
                                        Map.of(
                                                "mode",
                                                windows ? "CMD" : "SH",
                                                "argv",
                                                List.of(
                                                        windows
                                                                ? "echo BACKGROUND & ping -n 10 127.0.0.1 >nul"
                                                                : "echo BACKGROUND; sleep 10"),
                                                "timeoutMs",
                                                5000));
                            if (requests.size() == 2) {
                                var messages = (List<?>) requests.getLast().get("messages");
                                String content =
                                        (String) Values.object(messages.getLast()).get("content");
                                var matcher =
                                        Pattern.compile("[0-9a-f]{8}-[0-9a-f-]{27}")
                                                .matcher(content);
                                if (!matcher.find()) throw new IllegalStateException(content);
                                return tool("stop", "terminal_stop", Map.of("id", matcher.group()));
                            }
                            return textStream("Background task stopped; no verification claimed");
                        })) {
            var result =
                    fixture()
                            .execute(
                                    config(server, Map.of()),
                                    "y\ny\n",
                                    "run",
                                    "run background and stop",
                                    "--session",
                                    "s");
            assertEquals(2, result.code(), result.stderr() + result.stdout());
            var events = events();
            var snapshot =
                    events.stream()
                            .filter(e -> e.type().equals("terminal.snapshot"))
                            .reduce((a, b) -> b)
                            .orElseThrow();
            var state = Values.object(((List<?>) snapshot.payload().get("tasks")).getFirst());
            assertEquals("STOPPED", state.get("state"));
            assertEquals(false, state.get("alive"));
            assertTrue(((List<?>) state.get("residualPids")).isEmpty());
            String ref = (String) Values.object(state.get("logArtifact")).get("id");
            var artifacts =
                    new LocalArtifactStore(root.resolve(".jharness/artifacts"), data -> data);
            assertNotNull(artifacts.read("s", ref, 0, 100));
            assertTrue(SessionRecovery.inspect(events).ready());
            assertEquals(2, events.stream().filter(e -> e.type().equals("tool.intent")).count());
        }
    }

    @Test
    void readOnlyConfigurationDeniesTerminalBeforeProcessCreation() throws Exception {
        try (var server =
                new Server(
                        requests ->
                                requests.size() == 1
                                        ? tool(
                                                "denied",
                                                "terminal_start",
                                                Map.of("argv", javaCommand("block")))
                                        : textStream("Denied"))) {
            var result =
                    fixture()
                            .execute(
                                    config(server, Map.of("policy", "read-only")),
                                    "y\n",
                                    "run",
                                    "try background",
                                    "--session",
                                    "s");
            assertEquals(2, result.code(), result.stderr());
            assertTrue(
                    events().stream()
                            .noneMatch(
                                    e ->
                                            e.type().equals("tool.intent")
                                                    || e.type().equals("tool.started")));
            assertTrue(
                    events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "DENIED".equals(e.payload().get("status"))));
            assertTrue(events().stream().noneMatch(e -> e.type().equals("terminal.snapshot")));
        }
    }

    @Test
    void hiddenToolsCannotBeInvokedByGuessingTheirNames() throws Exception {
        try (var server =
                new Server(
                        requests ->
                                requests.size() == 1
                                        ? tool(
                                                "hidden",
                                                "write_file",
                                                Map.of("path", "hidden.txt", "content", "unsafe"))
                                        : textStream("Hidden tool is unavailable"))) {
            var result =
                    fixture()
                            .execute(
                                    config(server, Map.of("enabledTools", List.of("read_file"))),
                                    "y\n",
                                    "run",
                                    "try hidden tool",
                                    "--session",
                                    "s");
            assertEquals(2, result.code(), result.stderr() + result.stdout());
            assertFalse(Files.exists(root.resolve("hidden.txt")));
            assertTrue(
                    events().stream()
                            .noneMatch(
                                    e ->
                                            e.type().equals("tool.intent")
                                                    || e.type().equals("tool.started")));
            assertEquals(2, server.requests.size());
            assertEquals(1, ((List<?>) server.requests.getFirst().get("tools")).size());
        }
    }

    @Test
    void oneReplOwnsTheSameLiveTerminalAcrossTwoUserTurnsAndClosesIt() throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        try (var server =
                        new Server(
                                requests -> {
                                    if (requests.size() == 1)
                                        return tool(
                                                "start-live",
                                                "terminal_start",
                                                Map.of(
                                                        "mode",
                                                        windows ? "CMD" : "SH",
                                                        "argv",
                                                        List.of(
                                                                windows
                                                                        ? "ping -n 30 127.0.0.1 >nul"
                                                                        : "sleep 30"),
                                                        "timeoutMs",
                                                        15000));
                                    if (requests.size() == 2)
                                        return textStream("first turn finished");
                                    if (requests.size() == 5)
                                        return textStream("second turn finished");
                                    var messages = (List<?>) requests.getLast().get("messages");
                                    String toolText =
                                            messages.stream()
                                                    .map(Values::object)
                                                    .filter(m -> "tool".equals(m.get("role")))
                                                    .map(m -> (String) m.get("content"))
                                                    .filter(t -> t.contains("pty="))
                                                    .findFirst()
                                                    .orElseThrow();
                                    var matcher =
                                            Pattern.compile("[0-9a-f]{8}-[0-9a-f-]{27}")
                                                    .matcher(toolText);
                                    if (!matcher.find()) throw new IllegalStateException(toolText);
                                    return tool(
                                            requests.size() == 3 ? "status-live" : "stop-live",
                                            requests.size() == 3
                                                    ? "terminal_status"
                                                    : "terminal_stop",
                                            Map.of("id", matcher.group()));
                                });
                var input = new PipedInputStream();
                var feed = new PipedOutputStream(input);
                var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Path path = config(server, Map.of());
            var output = new ByteArrayOutputStream();
            var executing =
                    executor.submit(
                            () ->
                                    new io.jharness.cli.CliApplication(fixture().environment())
                                            .execute(
                                                    new String[] {
                                                        "--workspace",
                                                        root.toString(),
                                                        "--config",
                                                        path.toString(),
                                                        "--session",
                                                        "s"
                                                    },
                                                    input,
                                                    new PrintStream(
                                                            output, true, StandardCharsets.UTF_8),
                                                    new PrintStream(
                                                            output, true, StandardCharsets.UTF_8)));
            feed.write("start terminal\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            await(() -> output.toString(StandardCharsets.UTF_8).contains("APPROVAL"));
            feed.write("y\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            await(() -> output.toString(StandardCharsets.UTF_8).contains("REPORT "));
            feed.write("inspect and stop terminal\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            await(() -> output.toString(StandardCharsets.UTF_8).split("APPROVAL", -1).length >= 3);
            feed.write("y\n:quit\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            assertEquals(
                    2,
                    executing.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    output.toString(StandardCharsets.UTF_8));
            var history = events();
            assertEquals(2, history.stream().filter(e -> e.type().equals("turn.started")).count());
            var status =
                    history.stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "status-live".equals(e.callId()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("RUNNING", Values.object(status.payload().get("value")).get("state"));
            assertTrue(SessionRecovery.inspect(history).ready());
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.getAsBoolean(), "interactive fixture timed out");
    }
}
