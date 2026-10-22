package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.jharness.api.*;
import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleLlmClientTest {
    private static final String KEY = "sentinel-private-api-key";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void sensitiveToolIdentityIsRejectedRatherThanRenamedIntoCollision() throws Exception {
        for (String response :
                List.of(
                        toolResponse("{}", false).replace("c1", KEY),
                        toolResponse("{}", false).replace("read", KEY))) {
            try (Server server = new Server(exchange -> reply(exchange, 200, response));
                    var client = server.client(Duration.ofSeconds(3), 4096, 0)) {
                var failure =
                        assertThrows(
                                HarnessException.class,
                                () -> client.complete(request(), CancellationToken.NONE));
                assertEquals("UNSAFE_TOOL_IDENTITY", failure.code());
                assertFalse(failure.getMessage().contains(KEY));
            }
        }
    }

    @Test
    void textRequestHasConfiguredModelHeaderAndPreparedEnvelopeWithoutSecret() throws Exception {
        try (Server server = new Server(exchange -> reply(exchange, 200, text("hello")))) {
            try (OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0)) {
                ChatRequest request =
                        new ChatRequest(
                                List.of(Map.of("role", "user", "content", "avoid " + KEY)),
                                List.of(),
                                Map.of("temperature", 0.2, "stop", KEY));
                PreparedRequest prepared = client.prepare(request);
                assertEquals("[REDACTED]", prepared.request().parameters().get("stop"));
                assertEquals("test-model", prepared.request().parameters().get("model"));
                assertFalse(prepared.toString().contains(KEY));
                assertEquals(
                        "hello",
                        client.complete(prepared.request(), CancellationToken.NONE).text());
                assertEquals("Bearer " + KEY, server.authorization.get());
                Map<String, Object> wire =
                        JSON.readValue(
                                server.requests.getFirst(),
                                new TypeReference<Map<String, Object>>() {});
                assertEquals("test-model", wire.get("model"));
                assertEquals(false, wire.get("stream"));
                assertFalse(server.requests.getFirst().contains(KEY));
                assertFalse(server.config(Duration.ofSeconds(1), 4096, 0).toString().contains(KEY));
            }
        }
    }

    @Test
    void toolArgumentsParseAndRoundTripAsJsonStringsWithLinkedResults() throws Exception {
        String response = toolResponse("{\"path\":\"pom.xml\"}", false);
        try (Server server = new Server(exchange -> reply(exchange, 200, response));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0)) {
            ChatResponse parsed = client.complete(request(), CancellationToken.NONE);
            assertEquals(Map.of("path", "pom.xml"), parsed.toolCalls().getFirst().arguments());
            assertEquals(
                    SideEffectLevel.SYSTEM_IMPACT, parsed.toolCalls().getFirst().sideEffectLevel());
            ChatRequest next =
                    new ChatRequest(
                            List.of(
                                    Map.of("role", "user", "content", "read"),
                                    Map.of(
                                            "role",
                                            "assistant",
                                            "content",
                                            "",
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
                                                                    "read",
                                                                    "arguments",
                                                                    parsed.toolCalls()
                                                                            .getFirst()
                                                                            .arguments())))),
                                    Map.of(
                                            "role",
                                            "tool",
                                            "tool_call_id",
                                            "c1",
                                            "content",
                                            "result")),
                            List.of(),
                            Map.of());
            client.complete(next, CancellationToken.NONE);
            Map<String, Object> wire =
                    JSON.readValue(
                            server.requests.getLast(), new TypeReference<Map<String, Object>>() {});
            List<?> messages = (List<?>) wire.get("messages");
            Map<String, Object> assistant = Values.object(messages.get(1));
            Map<String, Object> call =
                    Values.object(((List<?>) assistant.get("tool_calls")).getFirst());
            assertTrue(Values.object(call.get("function")).get("arguments") instanceof String);
        }
    }

    @Test
    void malformedAndDuplicateAndIncompleteResponsesFailExplicitly() throws Exception {
        for (String body :
                List.of(
                        "{broken",
                        "{}",
                        text("ok") + " {}",
                        """
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"partial"}}]}
                """,
                        """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok","reasoning_content":"thinking"}}]}
                """,
                        """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":null}}]}
                """,
                        toolResponse("{}", true),
                        toolResponse("{\"x\":1,\"x\":2}", false))) {
            try (Server server = new Server(exchange -> reply(exchange, 200, body));
                    OpenAiCompatibleLlmClient client =
                            server.client(Duration.ofSeconds(3), 4096, 0)) {
                HarnessException error =
                        assertThrows(
                                HarnessException.class,
                                () -> client.complete(request(), CancellationToken.NONE));
                assertFalse(error.toString().contains(body));
                assertEquals(1, server.requests.size());
            }
        }
    }

    @Test
    void authenticationServerAndRateErrorsDoNotLeakBodiesAndAmbiguousErrorsAreNotRetried()
            throws Exception {
        Map<Integer, String> errors =
                Map.of(
                        401,
                        "MODEL_AUTH_FAILED",
                        402,
                        "MODEL_BALANCE",
                        429,
                        "MODEL_RATE_LIMIT",
                        500,
                        "MODEL_SERVER_ERROR",
                        503,
                        "MODEL_SERVER_ERROR",
                        400,
                        "MODEL_BAD_REQUEST");
        for (Map.Entry<Integer, String> entry : errors.entrySet()) {
            try (Server server = new Server(exchange -> reply(exchange, entry.getKey(), KEY));
                    OpenAiCompatibleLlmClient client =
                            server.client(Duration.ofSeconds(3), 4096, 0)) {
                HarnessException error =
                        assertThrows(
                                HarnessException.class,
                                () -> client.complete(request(), CancellationToken.NONE));
                assertEquals(entry.getValue(), error.code());
                assertFalse(error.toString().contains(KEY));
                assertEquals(1, server.requests.size());
            }
        }
    }

    @Test
    void explicitRateLimitRetriesAreBoundedAndCanRecover() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (Server server =
                        new Server(
                                exchange ->
                                        reply(
                                                exchange,
                                                attempts.incrementAndGet() == 1 ? 429 : 200,
                                                text("recovered")));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 1)) {
            assertEquals("recovered", client.complete(request(), CancellationToken.NONE).text());
            assertEquals(2, attempts.get());
        }
        try (Server server = new Server(exchange -> reply(exchange, 429, "{}"));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 1)) {
            assertEquals(
                    "MODEL_RATE_LIMIT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
            assertEquals(2, server.requests.size());
        }
    }

    @Test
    void cancellationBeforeSendingProducesNoHttpRequest() throws Exception {
        try (Server server = new Server(exchange -> reply(exchange, 200, text("ok")));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0)) {
            assertEquals(
                    "CANCELLED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), () -> true))
                            .code());
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void cancellationAfterHeadersAndBodyTimeoutStopWaiting() throws Exception {
        CountDownLatch headers = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Server server =
                        new Server(
                                exchange -> {
                                    exchange.sendResponseHeaders(200, 1024);
                                    headers.countDown();
                                    try {
                                        release.await(3, TimeUnit.SECONDS);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                    exchange.close();
                                });
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0);
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicBoolean cancelled = new AtomicBoolean();
            Future<ChatResponse> result =
                    executor.submit(() -> client.complete(request(), cancelled::get));
            assertTrue(headers.await(2, TimeUnit.SECONDS));
            cancelled.set(true);
            ExecutionException error =
                    assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertEquals("CANCELLED", ((HarnessException) error.getCause()).code());
            release.countDown();
        } finally {
            release.countDown();
        }
        try (Server server =
                        new Server(
                                exchange -> {
                                    try {
                                        Thread.sleep(500);
                                        reply(exchange, 200, text("late"));
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                OpenAiCompatibleLlmClient client = server.client(Duration.ofMillis(100), 4096, 0)) {
            assertEquals(
                    "MODEL_TIMEOUT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void responseLimitRedirectAndUnsupportedCapabilitiesFailClosed() throws Exception {
        try (Server server = new Server(exchange -> reply(exchange, 200, "x".repeat(4096)));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 128, 0)) {
            assertEquals(
                    "MODEL_RESPONSE_TOO_LARGE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
            assertFalse(client.capabilities().streaming());
            assertFalse(client.capabilities().images());
            assertEquals(
                    "UNSUPPORTED_STREAM",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.stream(request(), CancellationToken.NONE))
                            .code());
        }
        try (Server server =
                        new Server(
                                exchange -> {
                                    exchange.getResponseHeaders()
                                            .add(
                                                    "Location",
                                                    "https://example.invalid/credential-leak");
                                    reply(exchange, 302, "");
                                });
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0)) {
            assertEquals(
                    "MODEL_HTTP_ERROR",
                    assertThrows(
                                    HarnessException.class,
                                    () -> client.complete(request(), CancellationToken.NONE))
                            .code());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void invalidMessageProtocolAndRoutesFailBeforeSending() throws Exception {
        try (Server server = new Server(exchange -> reply(exchange, 200, text("ok")));
                OpenAiCompatibleLlmClient client = server.client(Duration.ofSeconds(3), 4096, 0)) {
            for (ChatRequest bad :
                    List.of(
                            new ChatRequest(
                                    List.of(
                                            Map.of(
                                                    "role",
                                                    "tool",
                                                    "tool_call_id",
                                                    "orphan",
                                                    "content",
                                                    "x")),
                                    List.of(),
                                    Map.of()),
                            new ChatRequest(
                                    request().messages(), List.of(), Map.of("model", "other")),
                            new ChatRequest(
                                    request().messages(), List.of(), Map.of("stream", true)),
                            new ChatRequest(
                                    request().messages(), List.of(), Map.of("api_key", KEY)),
                            new ChatRequest(
                                    List.of(Map.of("role", "user", "content", List.of("image"))),
                                    List.of(),
                                    Map.of()))) {
                assertThrows(
                        HarnessException.class, () -> client.complete(bad, CancellationToken.NONE));
            }
            assertTrue(server.requests.isEmpty());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OpenAiCompatibleConfig(
                                URI.create("http://external.invalid"),
                                "model",
                                KEY,
                                Duration.ofSeconds(1),
                                4096,
                                0,
                                capabilities()));
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(Map.of("role", "user", "content", "hello")), List.of(), Map.of());
    }

    private static String toolResponse(String arguments, boolean duplicate) throws IOException {
        Map<String, Object> call =
                Map.of(
                        "id",
                        "c1",
                        "type",
                        "function",
                        "function",
                        Map.of("name", "read", "arguments", arguments));
        return JSON.writeValueAsString(
                Map.of(
                        "choices",
                        List.of(
                                Map.of(
                                        "finish_reason",
                                        "tool_calls",
                                        "message",
                                        Map.of(
                                                "role",
                                                "assistant",
                                                "tool_calls",
                                                duplicate ? List.of(call, call) : List.of(call)))),
                        "usage",
                        Map.of("prompt_tokens", 10)));
    }

    private static ModelCapabilities capabilities() {
        return new ModelCapabilities(true, false, false, false, 8192);
    }

    private static String text(String text) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\""
                + text
                + "\"}}]}";
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final List<String> requests = new CopyOnWriteArrayList<>();
        final AtomicReference<String> authorization = new AtomicReference<>();

        Server(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext(
                    "/v1/chat/completions",
                    exchange -> {
                        requests.add(
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                        handler.handle(exchange);
                    });
            server.start();
        }

        OpenAiCompatibleConfig config(Duration timeout, int limit, int retries) {
            return new OpenAiCompatibleConfig(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                    "test-model",
                    KEY,
                    timeout,
                    limit,
                    retries,
                    capabilities());
        }

        OpenAiCompatibleLlmClient client(Duration timeout, int limit, int retries) {
            return new OpenAiCompatibleLlmClient(config(timeout, limit, retries));
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
