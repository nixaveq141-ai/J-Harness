package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.jharness.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class M6StreamingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "secret-stream-key";

    static String chunk(Map<String, Object> delta, String finish) throws Exception {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finish);
        return "data: " + JSON.writeValueAsString(Map.of("choices", List.of(choice))) + "\r\n\r\n";
    }

    static ChatRequest request() {
        return new ChatRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                Map.of("stream", true));
    }

    static final class Server implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger requests = new AtomicInteger();
        final List<Map<String, Object>> wire = new ArrayList<>();

        Server(String response, int delay) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/chat/completions",
                    exchange -> {
                        requests.incrementAndGet();
                        synchronized (wire) {
                            wire.add(
                                    Values.object(
                                            JSON.readValue(exchange.getRequestBody(), Map.class)));
                        }
                        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                        exchange.sendResponseHeaders(200, 0);
                        try {
                            for (byte b : response.getBytes(StandardCharsets.UTF_8)) {
                                exchange.getResponseBody().write(b);
                                exchange.getResponseBody().flush();
                            }
                            if (delay > 0)
                                try {
                                    Thread.sleep(delay);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                }
                        } catch (IOException ignored) {
                            /* expected on cancellation */
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
        }

        OpenAiCompatibleLlmClient client(int maxBytes, Duration timeout) {
            return new OpenAiCompatibleLlmClient(
                    new OpenAiCompatibleConfig(
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                            "fixture",
                            KEY,
                            timeout,
                            maxBytes,
                            0,
                            new ModelCapabilities(true, true, false, false, 65536)));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @Test
    void fragmentedUtf8MultiToolCallsAndUsageAssembleOnlyAfterDone() throws Exception {
        String body =
                ": heartbeat\r\n\r\n"
                        + chunk(Map.of("role", "assistant", "content", "中文 hello"), null)
                        + chunk(
                                Map.of(
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
                                                                "{\"path\":")),
                                                Map.of(
                                                        "index",
                                                        1,
                                                        "id",
                                                        "c2",
                                                        "type",
                                                        "function",
                                                        "function",
                                                        Map.of(
                                                                "name",
                                                                "read_file",
                                                                "arguments",
                                                                "{\"path\":\"two\"}")))),
                                null)
                        + chunk(
                                Map.of(
                                        "tool_calls",
                                        List.of(
                                                Map.of(
                                                        "index",
                                                        0,
                                                        "function",
                                                        Map.of("arguments", "\"one\"}")))),
                                null)
                        + chunk(Map.of(), "tool_calls")
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}\n\n"
                        + "data: [DONE]\n\n";
        try (var server = new Server(body, 0);
                var client = server.client(8192, Duration.ofSeconds(4));
                var handle = client.stream(request(), CancellationToken.NONE)) {
            List<ChatResponse> frames = new ArrayList<>();
            handle.consume(frames::add);
            assertTrue(frames.stream().allMatch(f -> f.toolCalls().isEmpty()));
            var finalResponse = handle.finalResponse();
            assertEquals("中文 hello", finalResponse.text());
            assertEquals(2, finalResponse.toolCalls().size());
            assertEquals(Map.of("path", "one"), finalResponse.toolCalls().getFirst().arguments());
            assertEquals(10, finalResponse.usage().get("prompt_tokens"));
            assertEquals(Boolean.TRUE, server.wire.getFirst().get("stream"));
            assertThrows(HarnessException.class, () -> handle.consume(f -> {}));
        }
    }

    @Test
    void incompleteMalformedAndDuplicateStreamsNeverExposeFinalToolsOrRetry() throws Exception {
        List<String> bodies =
                List.of(
                        chunk(Map.of("content", "partial"), null),
                        chunk(Map.of("content", "partial"), "length") + "data: [DONE]\n\n",
                        chunk(
                                        Map.of(
                                                "tool_calls",
                                                List.of(
                                                        Map.of(
                                                                "index",
                                                                0,
                                                                "id",
                                                                "same",
                                                                "function",
                                                                Map.of(
                                                                        "name",
                                                                        "x",
                                                                        "arguments",
                                                                        "{}")),
                                                        Map.of(
                                                                "index",
                                                                1,
                                                                "id",
                                                                "same",
                                                                "function",
                                                                Map.of(
                                                                        "name",
                                                                        "y",
                                                                        "arguments",
                                                                        "{}")))),
                                        "tool_calls")
                                + "data: [DONE]\n\n",
                        "data: {bad}\n\n",
                        chunk(
                                        Map.of(
                                                "tool_calls",
                                                List.of(
                                                        Map.of(
                                                                "index",
                                                                0,
                                                                "id",
                                                                "c",
                                                                "function",
                                                                Map.of(
                                                                        "name",
                                                                        "x",
                                                                        "arguments",
                                                                        "{partial")))),
                                        "tool_calls")
                                + "data: [DONE]\n\n");
        for (String body : bodies)
            try (var server = new Server(body, 0);
                    var client = server.client(8192, Duration.ofSeconds(3));
                    var handle = client.stream(request(), CancellationToken.NONE)) {
                assertThrows(
                        HarnessException.class,
                        () -> handle.consume(f -> assertTrue(f.toolCalls().isEmpty())));
                assertThrows(HarnessException.class, handle::finalResponse);
                assertEquals(1, server.requests.get());
            }
    }

    @Test
    void cancellationAndTimeoutInterruptBodyWithoutASecondRequest() throws Exception {
        String body = chunk(Map.of("content", "enough preview text to trigger observer"), null);
        AtomicBoolean cancel = new AtomicBoolean();
        try (var server = new Server(body, 500);
                var client = server.client(8192, Duration.ofSeconds(3));
                var handle = client.stream(request(), cancel::get)) {
            var failure =
                    assertThrows(
                            HarnessException.class, () -> handle.consume(f -> cancel.set(true)));
            assertEquals("CANCELLED", failure.code());
            assertEquals(1, server.requests.get());
        }
        try (var server = new Server(body, 500);
                var client = server.client(8192, Duration.ofMillis(100))) {
            assertEquals(
                    "MODEL_TIMEOUT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void streamLimitsAndSecretsSplitAcrossDeltasAreSafe() throws Exception {
        String body =
                chunk(Map.of("content", "before secret-stream-"), null)
                        + chunk(Map.of("content", "key after"), null)
                        + chunk(Map.of(), "stop")
                        + "data: [DONE]\n\n";
        try (var server = new Server(body, 0);
                var client = server.client(8192, Duration.ofSeconds(3))) {
            List<String> display = new ArrayList<>();
            var streaming = new StreamingLlmClient(client, display::add, List.of(KEY));
            assertEquals(
                    "before [REDACTED] after",
                    streaming.complete(request(), CancellationToken.NONE).text());
            assertTrue(
                    display.stream()
                            .noneMatch(v -> v.contains("secret-stream-") || v.contains(KEY)));
        }
        try (var server =
                        new Server(
                                chunk(Map.of("content", "x".repeat(1000)), "stop")
                                        + "data: [DONE]\n\n",
                                0);
                var client = server.client(128, Duration.ofSeconds(3))) {
            assertEquals(
                    "MODEL_RESPONSE_TOO_LARGE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void routerLazilySelectsCapabilitiesAndClosesOnlyInstantiatedAdapters() {
        AtomicInteger constructed = new AtomicInteger(), closed = new AtomicInteger();
        java.util.function.Supplier<LlmClient> factory =
                () -> {
                    constructed.incrementAndGet();
                    return new LlmClient() {
                        @Override
                        public ModelCapabilities capabilities() {
                            return new ModelCapabilities(false, false, false, false, 2048);
                        }

                        @Override
                        public PreparedRequest prepare(ChatRequest request) {
                            return new PreparedRequest(request);
                        }

                        @Override
                        public ChatResponse complete(ChatRequest request, CancellationToken token) {
                            return new ChatResponse("done", List.of(), Map.of());
                        }

                        @Override
                        public StreamHandle stream(ChatRequest request, CancellationToken token) {
                            throw new HarnessException("UNSUPPORTED_STREAM", "no stream");
                        }

                        @Override
                        public void close() {
                            closed.incrementAndGet();
                        }
                    };
                };
        var router = new LazyModelRouter(Map.of("a", factory, "b", factory));
        assertEquals(0, constructed.get());
        assertSame(router.select("a"), router.select("a"));
        assertEquals(1, constructed.get());
        var fallback =
                new StreamingLlmClient(
                        router.select("a"),
                        text -> fail("fallback must not emit deltas"),
                        List.of());
        assertEquals(false, fallback.prepare(request()).request().parameters().get("stream"));
        assertEquals("done", fallback.complete(request(), CancellationToken.NONE).text());
        router.close();
        router.close();
        assertEquals(1, closed.get());
        assertThrows(HarnessException.class, () -> router.select("b"));
    }
}
