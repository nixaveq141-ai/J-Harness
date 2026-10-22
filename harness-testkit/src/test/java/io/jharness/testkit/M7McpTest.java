package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.session.SessionRecovery;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M7McpTest {
    @TempDir Path base;

    private McpPlugin stdio(Path directory, String mode, Map<String, SideEffectLevel> effects)
            throws Exception {
        Files.createDirectories(directory);
        return new McpPlugin(
                "peer",
                () -> {
                    try {
                        return new StdioMcpTransport(
                                List.of(
                                        M3RuntimeIntegrationTest.java(),
                                        "-cp",
                                        System.getProperty("java.class.path"),
                                        M7McpServer.class.getName(),
                                        directory.resolve("trace.jsonl").toString(),
                                        directory.resolve("marker.txt").toString(),
                                        mode),
                                directory,
                                Duration.ofSeconds(3));
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                },
                effects);
    }

    @Test
    void stdioDiscoversButNeverTrustsReadonlyHintAndRevokesOnStop() throws Exception {
        Path peer = base.resolve("peer");
        try (var fixture = new M7EcosystemFixture(base, stdio(peer, "normal", Map.of()))) {
            fixture.start();
            var tool = fixture.tools.require("mcp_peer__echo", M7EcosystemFixture.SCOPE);
            assertEquals(SideEffectLevel.SYSTEM_IMPACT, tool.sideEffectLevel());
            assertTrue(tool.description().contains("stdio-host"));
            assertEquals(
                    ToolStatus.DENIED,
                    fixture.execute(
                                    tool.name(),
                                    Map.of("text", "input"),
                                    ApprovalService.rejectAll())
                            .status());
            assertFalse(Files.exists(peer.resolve("marker.txt")));
            assertEquals(
                    "SCHEMA_MISMATCH",
                    fixture.execute(tool.name(), Map.of("text", 7), M7EcosystemFixture.approve())
                            .error()
                            .orElseThrow()
                            .code());
            assertEquals(
                    "UNKNOWN_TOOL",
                    fixture.execute(
                                    tool.name(),
                                    Map.of("text", "input"),
                                    M7EcosystemFixture.approve(),
                                    CancellationToken.NONE,
                                    new PluginScope("s", "other"))
                            .error()
                            .orElseThrow()
                            .code());
            var answer =
                    fixture.execute(
                            tool.name(),
                            Map.of("text", "input"),
                            (call, context) -> {
                                assertFalse(Files.exists(peer.resolve("marker.txt")));
                                assertTrue(
                                        fixture.history().stream()
                                                .anyMatch(
                                                        event ->
                                                                event.type()
                                                                        .equals(
                                                                                "approval.requested")));
                                return new ApprovalService.ApprovalDecision(
                                        true, "approved fixture", "operator");
                            });
            assertEquals(ToolStatus.SUCCESS, answer.status());
            assertEquals("peer", Values.object(answer.value().get("source")).get("server"));
            assertEquals("called", Files.readString(peer.resolve("marker.txt")));
            var history = fixture.history();
            assertTrue(
                    history.stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("tool.intent")
                                                    && event.callId().equals(answer.callId())));
            assertFalse(history.toString().contains("mcp-secret-token"));
            fixture.container.stop();
            assertTrue(fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
        }
        awaitExit(peer.resolve("pid.txt"));
    }

    @Test
    void disconnectWrongIdChangedListAndInvalidOutputRemainUnknownAndNeverReplay()
            throws Exception {
        for (String mode : List.of("drop", "wrong-id", "changed", "bad-output", "many-nodes")) {
            Path directory = base.resolve(mode), peer = directory.resolve("peer");
            try (var fixture = new M7EcosystemFixture(directory, stdio(peer, mode, Map.of()))) {
                fixture.start();
                var result =
                        fixture.execute(
                                "mcp_peer__echo",
                                Map.of("text", "input"),
                                M7EcosystemFixture.approve());
                assertEquals(ToolStatus.UNKNOWN, result.status(), mode);
                assertEquals(true, result.value().get("sideEffectUncertain"));
                assertFalse(SessionRecovery.inspect(fixture.history()).ready());
                assertEquals(
                        1,
                        Files.readAllLines(peer.resolve("trace.jsonl")).stream()
                                .filter(line -> line.contains("tools/call"))
                                .count());
            }
            awaitExit(peer.resolve("pid.txt"));
        }
    }

    @Test
    void unsupportedSchemaRollsBackWholeScopeAndClosesPeer() throws Exception {
        Path peer = base.resolve("peer");
        try (var fixture = new M7EcosystemFixture(base, stdio(peer, "bad-schema", Map.of()))) {
            assertThrows(HarnessException.class, fixture::start);
            assertTrue(fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
        }
        awaitExit(peer.resolve("pid.txt"));
    }

    @Test
    void explicitHostReadonlyOverrideDoesNotRequireApproval() throws Exception {
        try (var fixture =
                new M7EcosystemFixture(
                        base,
                        stdio(
                                base.resolve("peer"),
                                "normal",
                                Map.of("echo", SideEffectLevel.READ_ONLY)))) {
            fixture.start();
            assertEquals(
                    ToolStatus.SUCCESS,
                    fixture.execute(
                                    "mcp_peer__echo",
                                    Map.of("text", "input"),
                                    ApprovalService.rejectAll())
                            .status());
        }
    }

    @Test
    void failedIntentFlushNeverCallsExternalTool() throws Exception {
        Path peer = base.resolve("peer");
        try (var fixture = new M7EcosystemFixture(base, stdio(peer, "normal", Map.of()))) {
            fixture.start();
            var failing =
                    new SessionWriter() {
                        @Override
                        public boolean durable() {
                            return fixture.writer.durable();
                        }

                        @Override
                        public String append(SessionEvent event) {
                            return fixture.writer.append(event);
                        }

                        @Override
                        public void flush() {
                            throw new HarnessException("CHECKPOINT_FAILED", "fixture disk failure");
                        }

                        @Override
                        public void close() {}
                    };
            var call =
                    new ToolInvocation(
                            "failed-checkpoint",
                            "mcp_peer__echo",
                            Map.of("text", "input"),
                            SideEffectLevel.READ_ONLY);
            var result =
                    fixture.pipeline.execute(
                            call,
                            new ToolPipelineContext(
                                    M7EcosystemFixture.SCOPE,
                                    "turn",
                                    "step",
                                    fixture.tools,
                                    failing,
                                    fixture.events,
                                    new io.jharness.policy.DefaultPolicyEngine(),
                                    M7EcosystemFixture.approve(),
                                    new io.jharness.session.DefaultCheckpointPolicy(),
                                    CancellationToken.NONE));
            assertNotEquals(ToolStatus.SUCCESS, result.status());
            assertEquals("CHECKPOINT_FAILED", result.error().orElseThrow().code());
            assertTrue(
                    fixture.history().stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("tool.intent")
                                                    && "failed-checkpoint".equals(event.callId())));
            assertFalse(Files.exists(peer.resolve("marker.txt")));
            assertEquals(
                    0,
                    Files.readAllLines(peer.resolve("trace.jsonl")).stream()
                            .filter(line -> line.contains("tools/call"))
                            .count());
        }
    }

    @Test
    void sdkMcpLoopPersistsSourcesAndReconstructsEveryRequest() throws Exception {
        Path peer = base.resolve("peer"), workspace = base.resolve("workspace");
        Files.createDirectories(workspace);
        var model =
                new FakeLlm(
                        List.of(
                                new ChatResponse(
                                        "call",
                                        List.of(
                                                new ToolInvocation(
                                                        "external-call",
                                                        "mcp_peer__echo",
                                                        Map.of("text", "input"),
                                                        SideEffectLevel.READ_ONLY)),
                                        Map.of()),
                                new ChatResponse("done", List.of(), Map.of())),
                        32768);
        var repository = new io.jharness.session.JsonlSessionRepository(base.resolve("sessions"));
        try (var sdk =
                new HarnessSdk(
                        "sdk",
                        workspace.toString(),
                        repository,
                        new LocalExecutionEnvironment("local", workspace, List.of()),
                        model,
                        new io.jharness.session.LocalArtifactStore(
                                base.resolve("artifacts"), new SecretRedactor()),
                        new SecretRedactor(),
                        new io.jharness.policy.DefaultPolicyEngine(),
                        M7EcosystemFixture.approve(),
                        new Budget(3, Duration.ofSeconds(15)),
                        (response, projection) ->
                                new CompletionEvaluator.CompletionDecision(
                                        Files.exists(peer.resolve("marker.txt")),
                                        "actual external call evidence"),
                        ApiCostGuard.unavailable(),
                        List.of(stdio(peer, "normal", Map.of())))) {
            var snapshot = sdk.run("Call the approved MCP tool", CancellationToken.NONE);
            assertEquals(TaskStatus.COMPLETED, snapshot.status());
            int requests = 0;
            for (int index = 0; index < snapshot.events().size(); index++) {
                var event = snapshot.events().get(index);
                if (event.type().equals("model.requested"))
                    assertEquals(
                            model.requests().get(requests++),
                            new io.jharness.session.EventRequestJournal()
                                    .reconstruct(
                                            snapshot.events().subList(0, index + 1),
                                            event.eventId()));
            }
            assertEquals(2, requests);
        }
        awaitExit(peer.resolve("pid.txt"));
    }

    @Test
    void streamableHttpSupportsJsonAndSseHeadersCredentialsAndSessionDelete() throws Exception {
        for (boolean sseCall : List.of(false, true)) {
            try (var server = new PeerHttp(sseCall ? "sse" : "json")) {
                var plugin =
                        new McpPlugin(
                                "http",
                                () ->
                                        new HttpMcpTransport(
                                                server.uri(),
                                                Duration.ofSeconds(3),
                                                () -> "mcp-secret-token"),
                                Map.of());
                try (var fixture =
                        new M7EcosystemFixture(base.resolve(sseCall ? "sse" : "json"), plugin)) {
                    fixture.start();
                    var result =
                            fixture.execute(
                                    "mcp_http__echo",
                                    Map.of("text", "input"),
                                    M7EcosystemFixture.approve());
                    assertEquals(ToolStatus.SUCCESS, result.status());
                    assertFalse(result.toString().contains("mcp-secret-token"));
                    assertFalse(fixture.history().toString().contains("mcp-secret-token"));
                }
                assertEquals(1, server.calls.get());
                assertTrue(server.deleted.get());
                assertFalse(server.invalidHeader.get());
            }
        }
    }

    @Test
    void httpDisconnectAndExpiryDoNotReplayOrReinitialize() throws Exception {
        for (String mode : List.of("drop", "expired"))
            try (var server = new PeerHttp(mode)) {
                var plugin =
                        new McpPlugin(
                                "http",
                                () ->
                                        new HttpMcpTransport(
                                                server.uri(),
                                                Duration.ofSeconds(2),
                                                () -> "mcp-secret-token"),
                                Map.of());
                try (var fixture = new M7EcosystemFixture(base.resolve(mode), plugin)) {
                    fixture.start();
                    assertEquals(
                            ToolStatus.UNKNOWN,
                            fixture.execute(
                                            "mcp_http__echo",
                                            Map.of("text", "input"),
                                            M7EcosystemFixture.approve())
                                    .status());
                    assertFalse(SessionRecovery.inspect(fixture.history()).ready());
                    assertEquals(1, server.calls.get());
                    assertEquals(1, server.initializations.get());
                }
            }
    }

    @Test
    void cancellationInterruptsOpenSseAndKeepsUnknownOutcome() throws Exception {
        try (var server = new PeerHttp("hang")) {
            var plugin =
                    new McpPlugin(
                            "http",
                            () ->
                                    new HttpMcpTransport(
                                            server.uri(),
                                            Duration.ofSeconds(3),
                                            () -> "mcp-secret-token"),
                            Map.of());
            try (var fixture = new M7EcosystemFixture(base, plugin)) {
                fixture.start();
                AtomicBoolean cancelled = new AtomicBoolean();
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        server.entered.await(2, TimeUnit.SECONDS);
                                        Thread.sleep(80);
                                        cancelled.set(true);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                long started = System.nanoTime();
                var result =
                        fixture.execute(
                                "mcp_http__echo",
                                Map.of("text", "input"),
                                M7EcosystemFixture.approve(),
                                cancelled::get,
                                M7EcosystemFixture.SCOPE);
                assertEquals(ToolStatus.UNKNOWN, result.status());
                assertEquals(1, server.calls.get());
                assertTrue(
                        Duration.ofNanos(System.nanoTime() - started)
                                        .compareTo(Duration.ofSeconds(3))
                                < 0);
            }
        }
    }

    @Test
    void remotePlainHttpAndEmbeddedCredentialsAreRejected() {
        for (String endpoint :
                List.of(
                        "http://example.com/mcp",
                        "https://user:secret@example.com/mcp",
                        "https://example.com/mcp#fragment"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new HttpMcpTransport(
                                    URI.create(endpoint), Duration.ofSeconds(1), () -> null));
    }

    @Test
    void paginatedDiscoveryFreezesAllToolsAndRepeatedCursorFailsClosed() throws Exception {
        for (boolean repeat : List.of(false, true)) {
            AtomicBoolean closed = new AtomicBoolean();
            AtomicInteger pages = new AtomicInteger();
            McpTransport peer =
                    new McpTransport() {
                        @Override
                        public String kind() {
                            return "contract-fixture";
                        }

                        @Override
                        public Map<String, Object> exchange(
                                Map<String, Object> message, CancellationToken token) {
                            String method = (String) message.get("method");
                            if (method.equals("notifications/initialized")) return Map.of();
                            Map<String, Object> result;
                            if (method.equals("initialize"))
                                result =
                                        Map.of(
                                                "protocolVersion",
                                                "2025-11-25",
                                                "capabilities",
                                                Map.of("tools", Map.of()));
                            else {
                                int page = pages.incrementAndGet();
                                var values = new LinkedHashMap<String, Object>();
                                values.put(
                                        "tools",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        page == 1 ? "math.add" : "echo" + page,
                                                        "inputSchema",
                                                        M7McpServer.schema())));
                                if (page == 1 || repeat) values.put("nextCursor", "next");
                                result = values;
                            }
                            return Map.of(
                                    "jsonrpc", "2.0", "id", message.get("id"), "result", result);
                        }

                        @Override
                        public void close() {
                            closed.set(true);
                        }
                    };
            var plugin = new McpPlugin("paged", () -> peer, Map.of());
            try (var fixture =
                    new M7EcosystemFixture(base.resolve(repeat ? "repeat" : "valid"), plugin)) {
                if (repeat) {
                    assertEquals(
                            "MCP_DISCOVERY_LIMIT",
                            assertThrows(HarnessException.class, fixture::start).code());
                    assertTrue(fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
                } else {
                    fixture.start();
                    assertTrue(
                            fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).stream()
                                    .anyMatch(
                                            tool ->
                                                    tool.name().startsWith("mcp_paged__math_add_")
                                                            && tool.name()
                                                                    .matches("[a-zA-Z0-9_-]{1,64}")
                                                            && tool.description()
                                                                    .contains("math.add")));
                    assertTrue(
                            fixture.tools
                                    .resolve("mcp_paged__echo2", M7EcosystemFixture.SCOPE)
                                    .isPresent());
                }
                assertEquals(2, pages.get());
            }
            assertTrue(closed.get());
        }
    }

    private static void awaitExit(Path pidFile) throws Exception {
        long pid = Long.parseLong(Files.readString(pidFile));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    static final class PeerHttp implements AutoCloseable {
        final HttpServer server;
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger calls = new AtomicInteger(), initializations = new AtomicInteger();
        final AtomicBoolean deleted = new AtomicBoolean(), invalidHeader = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final ObjectMapper json = new ObjectMapper();

        PeerHttp(String mode) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext(
                    "/mcp",
                    exchange -> {
                        try (exchange) {
                            if (!"Bearer mcp-secret-token"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("Authorization"))
                                    || !"2025-11-25"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("MCP-Protocol-Version")))
                                invalidHeader.set(true);
                            if (exchange.getRequestMethod().equals("DELETE")) {
                                deleted.set(true);
                                exchange.sendResponseHeaders(204, -1);
                                return;
                            }
                            Map<?, ?> request =
                                    json.readValue(
                                            exchange.getRequestBody().readNBytes(1024 * 1024),
                                            Map.class);
                            String method = (String) request.get("method");
                            if (!"initialize".equals(method)
                                    && !"fixture-session"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("MCP-Session-Id")))
                                invalidHeader.set(true);
                            if (!exchange.getRequestHeaders()
                                    .getFirst("Accept")
                                    .contains("text/event-stream")) invalidHeader.set(true);
                            if (method == null || method.equals("notifications/initialized")) {
                                exchange.sendResponseHeaders(202, -1);
                                return;
                            }
                            Object result;
                            boolean sse = method.equals("tools/list") || mode.equals("sse");
                            if (method.equals("initialize")) {
                                initializations.incrementAndGet();
                                exchange.getResponseHeaders()
                                        .set("MCP-Session-Id", "fixture-session");
                                result =
                                        Map.of(
                                                "protocolVersion",
                                                "2025-11-25",
                                                "capabilities",
                                                Map.of("tools", Map.of()));
                            } else if (method.equals("tools/list"))
                                result =
                                        Map.of(
                                                "tools",
                                                List.of(
                                                        Map.of(
                                                                "name",
                                                                "echo",
                                                                "inputSchema",
                                                                M7McpServer.schema(),
                                                                "outputSchema",
                                                                M7McpServer.output())));
                            else {
                                calls.incrementAndGet();
                                entered.countDown();
                                if (mode.equals("drop")) return;
                                if (mode.equals("expired")) {
                                    exchange.sendResponseHeaders(404, -1);
                                    return;
                                }
                                if (mode.equals("hang")) {
                                    exchange.getResponseHeaders()
                                            .set("Content-Type", "text/event-stream");
                                    exchange.sendResponseHeaders(200, 0);
                                    exchange.getResponseBody()
                                            .write(
                                                    ": waiting\n\n"
                                                            .getBytes(StandardCharsets.UTF_8));
                                    exchange.getResponseBody().flush();
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return;
                                }
                                result =
                                        Map.of(
                                                "content",
                                                List.of(
                                                        Map.of(
                                                                "type",
                                                                "text",
                                                                "text",
                                                                "mcp-secret-token")),
                                                "structuredContent",
                                                Map.of("answer", "input"));
                            }
                            byte[] body =
                                    json.writeValueAsBytes(
                                            Map.of(
                                                    "jsonrpc",
                                                    "2.0",
                                                    "id",
                                                    request.get("id"),
                                                    "result",
                                                    result));
                            if (sse)
                                body =
                                        ("data: "
                                                        + new String(body, StandardCharsets.UTF_8)
                                                        + "\n\n")
                                                .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders()
                                    .set(
                                            "Content-Type",
                                            sse ? "text/event-stream" : "application/json");
                            exchange.sendResponseHeaders(200, body.length);
                            exchange.getResponseBody().write(body);
                        } catch (RuntimeException ignored) {
                            invalidHeader.set(true);
                        }
                    });
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
