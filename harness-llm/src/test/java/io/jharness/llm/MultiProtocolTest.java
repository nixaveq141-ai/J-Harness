package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jharness.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MultiProtocolTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String KEY = "synthetic-protocol-key";

    static ChatRequest request(boolean stream) {
        return new ChatRequest(
                List.of(Map.of("role", "user", "content", "read")),
                List.of(
                        Map.of(
                                "type",
                                "function",
                                "function",
                                Map.of(
                                        "name",
                                        "read_file",
                                        "parameters",
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of("path", Map.of("type", "string")))))),
                Map.of("max_tokens", 256, "stream", stream));
    }

    static Map<String, Object> response(ModelProtocol protocol, boolean tool) {
        var usage = Map.of("input_tokens", 10, "output_tokens", 4);
        if (protocol == ModelProtocol.RESPONSES)
            return Map.of(
                    "id",
                    "r1",
                    "model",
                    "resolved-model-snapshot",
                    "status",
                    "completed",
                    "output",
                    tool
                            ? List.of(
                                    Map.of(
                                            "type",
                                            "reasoning",
                                            "id",
                                            "reason1",
                                            "encrypted_content",
                                            "opaque-state",
                                            "summary",
                                            List.of()),
                                    Map.of(
                                            "type",
                                            "function_call",
                                            "id",
                                            "fc1",
                                            "call_id",
                                            "c1",
                                            "name",
                                            "read_file",
                                            "arguments",
                                            "{\"path\":\"note.txt\"}"))
                            : List.of(
                                    Map.of(
                                            "type",
                                            "message",
                                            "id",
                                            "m1",
                                            "role",
                                            "assistant",
                                            "content",
                                            List.of(
                                                    Map.of(
                                                            "type",
                                                            "output_text",
                                                            "text",
                                                            "done")))),
                    "usage",
                    usage);
        if (protocol == ModelProtocol.ANTHROPIC)
            return Map.of(
                    "id",
                    "a1",
                    "type",
                    "message",
                    "role",
                    "assistant",
                    "model",
                    "resolved-model-snapshot",
                    "stop_reason",
                    tool ? "tool_use" : "end_turn",
                    "content",
                    tool
                            ? List.of(
                                    Map.of(
                                            "type",
                                            "tool_use",
                                            "id",
                                            "c1",
                                            "name",
                                            "read_file",
                                            "input",
                                            Map.of("path", "note.txt")))
                            : List.of(Map.of("type", "text", "text", "done")),
                    "usage",
                    usage);
        return Map.of(
                "choices",
                List.of(
                        Map.of(
                                "finish_reason",
                                tool ? "tool_calls" : "stop",
                                "message",
                                tool
                                        ? Map.of(
                                                "role",
                                                "assistant",
                                                "tool_calls",
                                                List.of(
                                                        Map.of(
                                                                "id",
                                                                "c1",
                                                                "type",
                                                                "function",
                                                                "function",
                                                                Map.of(
                                                                        "name",
                                                                        "read_file",
                                                                        "arguments",
                                                                        "{\"path\":\"note.txt\"}"))))
                                        : Map.of("role", "assistant", "content", "done"))),
                "usage",
                Map.of("prompt_tokens", 10, "completion_tokens", 4, "total_tokens", 14));
    }

    static String event(Map<String, Object> value) throws Exception {
        return "data: " + JSON.writeValueAsString(value) + "\n\n";
    }

    static String stream(ModelProtocol protocol, boolean tool, boolean terminal) throws Exception {
        if (protocol == ModelProtocol.RESPONSES)
            return event(
                            Map.of(
                                    "type",
                                    "response.created",
                                    "sequence_number",
                                    0,
                                    "response",
                                    Map.of("id", "r1")))
                    + (terminal
                            ? event(
                                    Map.of(
                                            "type",
                                            "response.completed",
                                            "sequence_number",
                                            1,
                                            "response",
                                            response(protocol, tool)))
                            : "");
        if (protocol == ModelProtocol.ANTHROPIC) {
            var start =
                    Map.<String, Object>of(
                            "id",
                            "a1",
                            "type",
                            "message",
                            "role",
                            "assistant",
                            "content",
                            List.of(),
                            "usage",
                            Map.of("input_tokens", 10, "output_tokens", 0));
            String result = event(Map.of("type", "message_start", "message", start));
            result +=
                    event(
                            Map.of(
                                    "type",
                                    "content_block_start",
                                    "index",
                                    0,
                                    "content_block",
                                    tool
                                            ? Map.of(
                                                    "type",
                                                    "tool_use",
                                                    "id",
                                                    "c1",
                                                    "name",
                                                    "read_file",
                                                    "input",
                                                    Map.of())
                                            : Map.of("type", "text", "text", "")));
            result +=
                    event(
                            Map.of(
                                    "type",
                                    "content_block_delta",
                                    "index",
                                    0,
                                    "delta",
                                    tool
                                            ? Map.of(
                                                    "type",
                                                    "input_json_delta",
                                                    "partial_json",
                                                    "{\"path\":\"note.txt\"}")
                                            : Map.of("type", "text_delta", "text", "done")));
            result += event(Map.of("type", "content_block_stop", "index", 0));
            result +=
                    event(
                            Map.of(
                                    "type",
                                    "message_delta",
                                    "delta",
                                    Map.of("stop_reason", tool ? "tool_use" : "end_turn"),
                                    "usage",
                                    Map.of("output_tokens", 4)));
            return result + (terminal ? event(Map.of("type", "message_stop")) : "");
        }
        var delta =
                tool
                        ? Map.of(
                                "tool_calls",
                                List.of(
                                        Map.of(
                                                "index",
                                                0,
                                                "id",
                                                "c1",
                                                "type",
                                                "function",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        "read_file",
                                                        "arguments",
                                                        "{\"path\":\"note.txt\"}"))))
                        : Map.of("role", "assistant", "content", "done");
        return event(Map.of("choices", List.of(Map.of("index", 0, "delta", delta))))
                + event(
                        Map.of(
                                "choices",
                                List.of(
                                        Map.of(
                                                "index",
                                                0,
                                                "delta",
                                                Map.of(),
                                                "finish_reason",
                                                tool ? "tool_calls" : "stop")),
                                "usage",
                                Map.of(
                                        "prompt_tokens",
                                        10,
                                        "completion_tokens",
                                        4,
                                        "total_tokens",
                                        14)))
                + (terminal ? "data: [DONE]\n\n" : "");
    }

    static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final OpenAiCompatibleLlmClient client;
        final AtomicReference<Map<String, Object>> wire = new AtomicReference<>();
        final AtomicReference<String> authorization = new AtomicReference<>(),
                path = new AtomicReference<>();

        Fixture(ModelProtocol protocol, String body, boolean stream, int status, int limit)
                throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    x -> {
                        try {
                            wire.set(
                                    JSON.readValue(
                                            x.getRequestBody(),
                                            new com.fasterxml.jackson.core.type.TypeReference<
                                                    Map<String, Object>>() {}));
                            path.set(x.getRequestURI().getPath());
                            authorization.set(
                                    x.getRequestHeaders()
                                            .getFirst(
                                                    protocol == ModelProtocol.ANTHROPIC
                                                            ? "x-api-key"
                                                            : "Authorization"));
                            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                            x.getResponseHeaders()
                                    .set(
                                            "Content-Type",
                                            stream ? "text/event-stream" : "application/json");
                            x.sendResponseHeaders(status, bytes.length);
                            x.getResponseBody().write(bytes);
                        } finally {
                            x.close();
                        }
                    });
            server.start();
            client =
                    new OpenAiCompatibleLlmClient(
                            new OpenAiCompatibleConfig(
                                    URI.create(
                                            "http://127.0.0.1:"
                                                    + server.getAddress().getPort()
                                                    + "/v1"),
                                    "logical-model",
                                    KEY,
                                    Duration.ofSeconds(3),
                                    limit,
                                    0,
                                    new ModelCapabilities(true, true, false, false, 8192),
                                    protocol));
        }

        public void close() {
            client.close();
            server.stop(0);
        }
    }

    @ParameterizedTest
    @EnumSource(ModelProtocol.class)
    void jsonToolWireAndUsage(ModelProtocol protocol) throws Exception {
        try (var f =
                new Fixture(
                        protocol,
                        JSON.writeValueAsString(response(protocol, true)),
                        false,
                        200,
                        65536)) {
            var prepared = f.client.prepare(request(false));
            assertEquals(protocol.id(), prepared.request().parameters().get("protocol"));
            var result = f.client.complete(prepared.request(), CancellationToken.NONE);
            assertEquals(Map.of("path", "note.txt"), result.toolCalls().getFirst().arguments());
            assertEquals(14, ((Number) result.usage().get("total_tokens")).intValue());
            assertEquals("/v1" + protocol.path, f.path.get());
            assertEquals(
                    protocol == ModelProtocol.ANTHROPIC ? KEY : "Bearer " + KEY,
                    f.authorization.get());
            assertFalse(f.wire.get().containsKey("protocol"));
            if (protocol == ModelProtocol.RESPONSES) {
                assertEquals(false, f.wire.get().get("store"));
                assertEquals(256, f.wire.get().get("max_output_tokens"));
                assertEquals("logical-model", result.providerState().get("model"));
            }
            if (protocol == ModelProtocol.ANTHROPIC)
                assertEquals(
                        "read_file",
                        ProtocolCodec.obj(ProtocolCodec.list(f.wire.get().get("tools")).getFirst())
                                .get("name"));
        }
    }

    @ParameterizedTest
    @EnumSource(ModelProtocol.class)
    void sseTextAndToolCompleteOnlyAtTerminal(ModelProtocol protocol) throws Exception {
        for (boolean tool : List.of(false, true))
            try (var f = new Fixture(protocol, stream(protocol, tool, true), true, 200, 65536);
                    var handle = f.client.stream(request(true), CancellationToken.NONE)) {
                var previews = new ArrayList<ChatResponse>();
                handle.consume(previews::add);
                var result = handle.finalResponse();
                assertEquals(tool ? 1 : 0, result.toolCalls().size());
                assertTrue(previews.stream().allMatch(p -> p.toolCalls().isEmpty()));
                if (!tool) assertEquals("done", result.text());
                assertEquals(14, ((Number) result.usage().get("total_tokens")).intValue());
            }
    }

    @ParameterizedTest
    @EnumSource(ModelProtocol.class)
    void truncatedSseNeverReleasesCalls(ModelProtocol protocol) throws Exception {
        try (var f = new Fixture(protocol, stream(protocol, true, false), true, 200, 65536);
                var handle = f.client.stream(request(true), CancellationToken.NONE)) {
            var previews = new ArrayList<ChatResponse>();
            assertThrows(HarnessException.class, () -> handle.consume(previews::add));
            assertThrows(HarnessException.class, handle::finalResponse);
            assertTrue(previews.stream().allMatch(p -> p.toolCalls().isEmpty()));
        }
    }

    @ParameterizedTest
    @EnumSource(ModelProtocol.class)
    void limitsErrorsAndPreCancelledDoNotProduceResult(ModelProtocol protocol) throws Exception {
        try (var f = new Fixture(protocol, "x".repeat(256), false, 200, 128)) {
            assertEquals(
                    "MODEL_RESPONSE_TOO_LARGE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> f.client.complete(request(false), CancellationToken.NONE))
                            .code());
        }
        try (var f = new Fixture(protocol, "{}", false, 401, 1024)) {
            assertEquals(
                    "MODEL_AUTH_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> f.client.complete(request(false), CancellationToken.NONE))
                            .code());
        }
    }

    @ParameterizedTest
    @EnumSource(ModelProtocol.class)
    void cancellationDuringBodyIsBoundedAndNeverReplayed(ModelProtocol protocol) throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                x -> {
                    calls.incrementAndGet();
                    x.getRequestBody().readAllBytes();
                    x.getResponseHeaders().set("Content-Type", "text/event-stream");
                    x.sendResponseHeaders(200, 0);
                    x.getResponseBody().flush();
                    entered.countDown();
                    try {
                        release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        x.close();
                    }
                });
        server.start();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var client =
                new OpenAiCompatibleLlmClient(
                        new OpenAiCompatibleConfig(
                                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                                "m",
                                KEY,
                                Duration.ofSeconds(4),
                                65536,
                                0,
                                new ModelCapabilities(true, true, false, false, 8192),
                                protocol))) {
            var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            var pending = executor.submit(() -> client.complete(request(true), cancelled::get));
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            cancelled.set(true);
            var failure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> pending.get(1, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("CANCELLED", ((HarnessException) failure.getCause()).code());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    void reasoningStateReplaysOnlyOriginalRouteAndRejectsUnsafeCiphertext() throws Exception {
        var codec = new ProtocolCodec(ModelProtocol.RESPONSES, "logical-model", JSON);
        var decoded = codec.decode(response(ModelProtocol.RESPONSES, true));
        var state = ProtocolCodec.obj(decoded.get("provider_state"));
        var messages =
                List.<Map<String, Object>>of(
                        Map.of("role", "user", "content", "read"),
                        Map.of("role", "assistant", "content", "hello", "provider_state", state));
        var request = new ChatRequest(messages, List.of(), Map.of("max_tokens", 256));
        var canonical = Map.<String, Object>of("messages", messages, "max_tokens", 256);
        assertEquals(3, ProtocolCodec.list(codec.encode(canonical, request).get("input")).size());
        assertEquals(
                2,
                ProtocolCodec.list(
                                new ProtocolCodec(ModelProtocol.RESPONSES, "other", JSON)
                                        .encode(canonical, request)
                                        .get("input"))
                        .size());
        String body =
                JSON.writeValueAsString(response(ModelProtocol.RESPONSES, true))
                        .replace("opaque-state", KEY);
        try (var f = new Fixture(ModelProtocol.RESPONSES, body, false, 200, 65536)) {
            assertEquals(
                    "MODEL_STATE_UNSAFE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> f.client.complete(request(false), CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void malformedArgumentsAndUnsupportedReasoningFailClosed() throws Exception {
        for (var p : List.of(ModelProtocol.RESPONSES, ModelProtocol.ANTHROPIC)) {
            String raw = JSON.writeValueAsString(response(p, true));
            if (p == ModelProtocol.RESPONSES)
                raw = raw.replace("{\\\"path\\\":\\\"note.txt\\\"}", "[]");
            else raw = raw.replace("\"input\":{\"path\":\"note.txt\"}", "\"input\":[]");
            try (var f = new Fixture(p, raw, false, 200, 65536)) {
                assertThrows(
                        HarnessException.class,
                        () -> f.client.complete(request(false), CancellationToken.NONE));
            }
        }
        var codec = new ProtocolCodec(ModelProtocol.ANTHROPIC, "m", JSON);
        assertThrows(
                HarnessException.class,
                () ->
                        codec.decode(
                                Map.of(
                                        "type",
                                        "message",
                                        "role",
                                        "assistant",
                                        "stop_reason",
                                        "end_turn",
                                        "content",
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "thinking",
                                                        "thinking",
                                                        "secret")))));
    }
}
