package io.jharness.llm;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import io.jharness.api.*;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounded JSON/SSE chat adapter. Stream/transport failures are never blindly replayed. */
public final class OpenAiCompatibleLlmClient implements LlmClient {
    private static final Set<String> PARAMETERS =
            Set.of(
                    "model",
                    "stream",
                    "temperature",
                    "top_p",
                    "max_tokens",
                    "stop",
                    "seed",
                    "thinking",
                    "tool_choice",
                    "response_format",
                    "stream_options");
    private final OpenAiCompatibleConfig config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final SecretRedactor redactor;

    public OpenAiCompatibleLlmClient(OpenAiCompatibleConfig config) {
        this.config = Objects.requireNonNull(config);
        json =
                new ObjectMapper(
                                JsonFactory.builder()
                                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                        .streamReadConstraints(
                                                StreamReadConstraints.builder()
                                                        .maxNestingDepth(64)
                                                        .maxStringLength(config.maxResponseBytes)
                                                        .build())
                                        .build())
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        http =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(config.timeout)
                        .build();
        redactor = new SecretRedactor(List.of(config.apiKey));
    }

    @Override
    public ModelCapabilities capabilities() {
        return config.capabilities;
    }

    @Override
    public PreparedRequest prepare(ChatRequest request) {
        if (!PARAMETERS.containsAll(request.parameters().keySet()))
            throw error("UNSUPPORTED_PARAMETER");
        if (request.parameters().containsKey("thinking")
                && !Map.of("type", "disabled").equals(request.parameters().get("thinking")))
            throw error("UNSUPPORTED_REASONING");
        if (request.parameters().containsKey("model")
                && !config.model.equals(request.parameters().get("model")))
            throw error("MODEL_ROUTE_MISMATCH");
        if (request.parameters().containsKey("stream")
                && !(request.parameters().get("stream") instanceof Boolean))
            throw error("UNSUPPORTED_STREAM");
        if (Boolean.TRUE.equals(request.parameters().get("stream")) && !capabilities().streaming())
            throw error("UNSUPPORTED_STREAM");
        Map<String, Object> parameters = new LinkedHashMap<>(request.parameters());
        parameters.put("model", config.model);
        parameters.put("stream", Boolean.TRUE.equals(request.parameters().get("stream")));
        if (!capabilities().tools()) parameters.remove("tool_choice");
        ChatRequest prepared =
                new ChatRequest(
                        request.messages().stream().map(redactor::sanitize).toList(),
                        capabilities().tools()
                                ? request.tools().stream().map(redactor::sanitize).toList()
                                : List.of(),
                        redactor.sanitize(parameters));
        encode(prepared); // Validate protocol and bounds before the caller checkpoints.
        return new PreparedRequest(prepared);
    }

    @Override
    public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
        return complete(request, cancellation, false);
    }

    @Override
    public ChatResponse completeSingleAttempt(ChatRequest request, CancellationToken cancellation) {
        return complete(request, cancellation, true);
    }

    private ChatResponse complete(
            ChatRequest request, CancellationToken cancellation, boolean single) {
        cancellation.throwIfCancelled();
        if (Boolean.TRUE.equals(request.parameters().get("stream"))) {
            try (StreamHandle handle = stream(request, cancellation)) {
                handle.consume(snapshot -> {});
                return handle.finalResponse();
            }
        }
        byte[] body = encode(prepare(request).request());
        long started = System.nanoTime();
        long budget = config.timeout.toNanos();
        for (int attempt = 0; ; attempt++) {
            cancellation.throwIfCancelled();
            long remaining = budget - (System.nanoTime() - started);
            if (remaining <= 0) throw error("MODEL_TIMEOUT");
            HttpRequest outgoing =
                    HttpRequest.newBuilder(config.endpoint)
                            .timeout(Duration.ofNanos(remaining))
                            .header("Authorization", "Bearer " + config.apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();
            CompletableFuture<HttpResponse<byte[]>> pending =
                    http.sendAsync(outgoing, info -> new LimitedBody(config.maxResponseBytes));
            HttpResponse<byte[]> response;
            try {
                while (true) {
                    cancellation.throwIfCancelled();
                    if (System.nanoTime() - started >= budget) throw error("MODEL_TIMEOUT");
                    try {
                        response = pending.get(25, TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException ignored) {
                        /* Poll cancellation through the whole body read. */
                    }
                }
            } catch (InterruptedException ex) {
                pending.cancel(true);
                Thread.currentThread().interrupt();
                throw error("CANCELLED");
            } catch (ExecutionException ex) {
                pending.cancel(true);
                Throwable cause = ex.getCause();
                if (cause instanceof HarnessException h) throw error(h.code());
                if (cause instanceof HttpTimeoutException) throw error("MODEL_TIMEOUT");
                throw error("MODEL_TRANSPORT_UNKNOWN");
            } catch (RuntimeException ex) {
                pending.cancel(true);
                throw ex;
            }
            cancellation.throwIfCancelled();
            if (response.statusCode() == 429 && attempt < (single ? 0 : config.rateLimitRetries)) {
                long until = System.nanoTime() + Math.min(1_000L, 100L << attempt) * 1_000_000;
                while (System.nanoTime() < until) {
                    cancellation.throwIfCancelled();
                    if (System.nanoTime() - started >= budget) throw error("MODEL_TIMEOUT");
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw error("CANCELLED");
                    }
                }
                continue;
            }
            if (single && response.statusCode() == 429) throw new ModelRejectedException();
            if (response.statusCode() != 200)
                throw error(
                        switch (response.statusCode()) {
                            case 401 -> "MODEL_AUTH_FAILED";
                            case 402 -> "MODEL_BALANCE";
                            case 429 -> "MODEL_RATE_LIMIT";
                            case 400, 422 -> "MODEL_BAD_REQUEST";
                            case 500, 503 -> "MODEL_SERVER_ERROR";
                            default -> "MODEL_HTTP_ERROR";
                        });
            return parse(response.body());
        }
    }

    @Override
    public StreamHandle stream(ChatRequest request, CancellationToken cancellation) {
        if (!capabilities().streaming()) throw error("UNSUPPORTED_STREAM");
        var parameters = new LinkedHashMap<>(request.parameters());
        parameters.put("stream", true);
        ChatRequest prepared =
                prepare(new ChatRequest(request.messages(), request.tools(), parameters)).request();
        return new StreamHandle() {
            private volatile CompletableFuture<HttpResponse<ChatResponse>> pending;
            private volatile SseBody subscriber;
            private volatile boolean closed;
            private boolean consumed;
            private ChatResponse result;

            @Override
            public synchronized ChatResponse finalResponse() {
                if (result == null) throw error("MODEL_STREAM_INCOMPLETE");
                return result;
            }

            @Override
            public void consume(java.util.function.Consumer<ChatResponse> consumer) {
                synchronized (this) {
                    if (consumed || closed) throw error("STREAM_CLOSED");
                    consumed = true;
                }
                cancellation.throwIfCancelled();
                long started = System.nanoTime();
                var outgoing =
                        HttpRequest.newBuilder(config.endpoint)
                                .timeout(config.timeout)
                                .header("Authorization", "Bearer " + config.apiKey)
                                .header("Content-Type", "application/json")
                                .header("Accept", "text/event-stream")
                                .POST(HttpRequest.BodyPublishers.ofByteArray(encode(prepared)))
                                .build();
                pending =
                        http.sendAsync(
                                outgoing,
                                info -> {
                                    if (info.statusCode() != 200) {
                                        return HttpResponse.BodySubscribers.mapping(
                                                new LimitedBody(config.maxResponseBytes),
                                                bytes -> {
                                                    throw error("MODEL_HTTP_ERROR");
                                                });
                                    }
                                    if (!info.headers()
                                            .firstValue("Content-Type")
                                            .orElse("")
                                            .toLowerCase(Locale.ROOT)
                                            .startsWith("text/event-stream"))
                                        return HttpResponse.BodySubscribers.mapping(
                                                new LimitedBody(config.maxResponseBytes),
                                                bytes -> {
                                                    throw error("MODEL_BAD_CONTENT_TYPE");
                                                });
                                    subscriber = new SseBody(consumer);
                                    return subscriber;
                                });
                try {
                    while (true) {
                        cancellation.throwIfCancelled();
                        if (closed) throw error("CANCELLED");
                        if (System.nanoTime() - started >= config.timeout.toNanos())
                            throw error("MODEL_TIMEOUT");
                        try {
                            var response = pending.get(25, TimeUnit.MILLISECONDS);
                            cancellation.throwIfCancelled();
                            if (closed) throw error("CANCELLED");
                            synchronized (this) {
                                result = response.body();
                            }
                            return;
                        } catch (TimeoutException ignored) {
                            /* bounded cancellation polling */
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw error("CANCELLED");
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof HarnessException h) throw error(h.code());
                    if (ex.getCause() instanceof HttpTimeoutException) throw error("MODEL_TIMEOUT");
                    throw error("MODEL_TRANSPORT_UNKNOWN");
                } finally {
                    if (result == null) close();
                }
            }

            @Override
            public void close() {
                closed = true;
                if (subscriber != null) subscriber.cancel();
                if (pending != null && !pending.isDone()) pending.cancel(true);
            }
        };
    }

    @Override
    public void close() {
        http.shutdownNow();
    }

    private byte[] encode(ChatRequest request) {
        if (request.messages().isEmpty()) throw error("INVALID_MESSAGES");
        List<Map<String, Object>> messages = new ArrayList<>();
        Set<String> pending = new LinkedHashSet<>();
        Set<String> used = new HashSet<>();
        for (Map<String, Object> message : request.messages()) {
            Object role = message.get("role");
            Object content = message.get("content");
            if (!(role instanceof String name)
                    || !Set.of("system", "user", "assistant", "tool").contains(name)
                    || content != null && !(content instanceof String))
                throw error("UNSUPPORTED_MESSAGE");
            if (!"tool".equals(role) && !pending.isEmpty()) throw error("UNRESOLVED_TOOL_CALL");
            if (!"assistant".equals(role) && message.containsKey("tool_calls"))
                throw error("INVALID_MESSAGES");
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("role", role);
            wire.put("content", content);
            if ("tool".equals(role)) {
                Object id = message.get("tool_call_id");
                if (!(id instanceof String text) || !pending.remove(text))
                    throw error("ORPHAN_TOOL_RESULT");
                wire.put("tool_call_id", id);
            }
            if (message.containsKey("tool_calls")) {
                if (!(message.get("tool_calls") instanceof List<?> declaredCalls)
                        || declaredCalls.isEmpty()) throw error("INVALID_MESSAGES");
                List<Map<String, Object>> calls = new ArrayList<>();
                for (Object item : (List<?>) message.get("tool_calls")) {
                    Map<String, Object> call = Values.object(item);
                    String id = requiredText(call, "id");
                    Map<String, Object> function = Values.object(call.get("function"));
                    if (!used.add(id)) throw error("DUPLICATE_CALL_ID");
                    pending.add(id);
                    calls.add(
                            Map.of(
                                    "id",
                                    id,
                                    "type",
                                    "function",
                                    "function",
                                    Map.of(
                                            "name",
                                            requiredText(function, "name"),
                                            "arguments",
                                            stringify(Values.object(function.get("arguments"))))));
                }
                wire.put("tool_calls", calls);
            }
            messages.add(wire);
        }
        if (!pending.isEmpty()) throw error("UNRESOLVED_TOOL_CALL");
        Map<String, Object> wire = new LinkedHashMap<>(request.parameters());
        wire.put("messages", messages);
        if (!request.tools().isEmpty()) wire.put("tools", request.tools());
        try {
            byte[] bytes = json.writeValueAsBytes(wire);
            if (bytes.length > 1024 * 1024) throw error("MODEL_REQUEST_TOO_LARGE");
            return bytes;
        } catch (JsonProcessingException ex) {
            throw error("INVALID_JSON");
        }
    }

    private ChatResponse parse(byte[] bytes) {
        try {
            Map<String, Object> root = read(bytes);
            if (!(root.get("choices") instanceof List<?> choices) || choices.size() != 1)
                throw error("MODEL_BAD_RESPONSE");
            Map<String, Object> choice = Values.object(choices.getFirst());
            Object finish = choice.get("finish_reason");
            if (!"stop".equals(finish) && !"tool_calls".equals(finish))
                throw error("MODEL_INCOMPLETE");
            Map<String, Object> message = Values.object(choice.get("message"));
            if (!"assistant".equals(message.get("role"))) throw error("MODEL_BAD_RESPONSE");
            if (message.get("reasoning_content") instanceof String reasoning
                    && !reasoning.isEmpty()) throw error("UNSUPPORTED_REASONING");
            Object content = message.get("content");
            if (content != null && !(content instanceof String)) throw error("MODEL_BAD_RESPONSE");
            List<ToolInvocation> calls = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            if (message.containsKey("tool_calls")) {
                if (!(message.get("tool_calls") instanceof List<?> list))
                    throw error("MODEL_BAD_RESPONSE");
                for (Object item : (List<?>) message.get("tool_calls")) {
                    Map<String, Object> call = Values.object(item);
                    if (!"function".equals(call.get("type"))) throw error("MODEL_BAD_RESPONSE");
                    String id = requiredText(call, "id");
                    if (!ids.add(id)) throw error("DUPLICATE_CALL_ID");
                    Map<String, Object> function = Values.object(call.get("function"));
                    calls.add(
                            new ToolInvocation(
                                    id,
                                    requiredText(function, "name"),
                                    redactor.sanitize(
                                            read(
                                                    requiredText(function, "arguments")
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8))),
                                    SideEffectLevel.SYSTEM_IMPACT));
                }
            }
            if (calls.isEmpty() && (content == null || ((String) content).isBlank()))
                throw error("MODEL_BAD_RESPONSE");
            Map<String, Object> usage =
                    root.containsKey("usage") && root.get("usage") != null
                            ? Values.object(root.get("usage"))
                            : Map.of();
            return new ChatResponse(
                    redactor.text(content == null ? "" : (String) content),
                    calls,
                    redactor.sanitize(usage));
        } catch (HarnessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("MODEL_BAD_RESPONSE");
        }
    }

    private Map<String, Object> read(byte[] bytes) throws java.io.IOException {
        return Values.object(json.readValue(bytes, new TypeReference<Map<String, Object>>() {}));
    }

    private String stringify(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw error("INVALID_JSON");
        }
    }

    private static String requiredText(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String text) || text.isBlank())
            throw error("MODEL_BAD_RESPONSE");
        return text;
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }

    private final class SseBody implements HttpResponse.BodySubscriber<ChatResponse> {
        private final CompletableFuture<ChatResponse> body = new CompletableFuture<>();
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final StringBuilder data = new StringBuilder(), text = new StringBuilder();
        private final SortedMap<Integer, Map<String, StringBuilder>> calls = new TreeMap<>();
        private final java.util.function.Consumer<ChatResponse> consumer;
        private Flow.Subscription subscription;
        private int bytes;
        private String finish;
        private Map<String, Object> usage = Map.of();
        private boolean done;

        SseBody(java.util.function.Consumer<ChatResponse> consumer) {
            this.consumer = Objects.requireNonNull(consumer);
        }

        @Override
        public CompletionStage<ChatResponse> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        void cancel() {
            if (subscription != null) subscription.cancel();
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (ByteBuffer buffer : buffers)
                    while (buffer.hasRemaining()) {
                        if (++bytes > config.maxResponseBytes)
                            throw error("MODEL_RESPONSE_TOO_LARGE");
                        byte value = buffer.get();
                        if (value == '\n') {
                            String valueLine =
                                    java.nio.charset.StandardCharsets.UTF_8
                                            .newDecoder()
                                            .onMalformedInput(
                                                    java.nio.charset.CodingErrorAction.REPORT)
                                            .decode(ByteBuffer.wrap(line.toByteArray()))
                                            .toString();
                            line.reset();
                            if (valueLine.endsWith("\r"))
                                valueLine = valueLine.substring(0, valueLine.length() - 1);
                            if (valueLine.isEmpty()) frame();
                            else if (valueLine.startsWith("data:")) {
                                if (!data.isEmpty()) data.append('\n');
                                data.append(valueLine.substring(5).stripLeading());
                            }
                        } else line.write(value);
                    }
                subscription.request(1);
            } catch (Exception ex) {
                cancel();
                body.completeExceptionally(
                        ex instanceof HarnessException ? ex : error("MODEL_BAD_RESPONSE"));
            }
        }

        private void frame() throws java.io.IOException {
            if (data.isEmpty()) return;
            String payload = data.toString();
            data.setLength(0);
            if (done) throw error("MODEL_BAD_RESPONSE");
            if (payload.equals("[DONE]")) {
                done = true;
                return;
            }
            Map<String, Object> root =
                    read(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (root.get("usage") instanceof Map<?, ?> values) usage = Values.object(values);
            if (!(root.get("choices") instanceof List<?> choices) || choices.size() > 1)
                throw error("MODEL_BAD_RESPONSE");
            if (choices.isEmpty()) return;
            Map<String, Object> choice = Values.object(choices.getFirst());
            if (!Integer.valueOf(0).equals(choice.get("index"))) throw error("MODEL_BAD_RESPONSE");
            Map<String, Object> delta = Values.object(choice.get("delta"));
            if (delta.containsKey("role") && !"assistant".equals(delta.get("role")))
                throw error("MODEL_BAD_RESPONSE");
            if (delta.get("reasoning_content") instanceof String reasoning && !reasoning.isEmpty())
                throw error("UNSUPPORTED_REASONING");
            if (finish != null && (!delta.isEmpty() || choice.get("finish_reason") != null))
                throw error("MODEL_BAD_RESPONSE");
            if (delta.get("content") != null) {
                if (!(delta.get("content") instanceof String content))
                    throw error("MODEL_BAD_RESPONSE");
                text.append(content);
                String clean = redactor.text(text.toString());
                consumer.accept(
                        new ChatResponse(
                                clean.substring(
                                        0, Math.max(0, clean.length() - config.apiKey.length())),
                                List.of(),
                                Map.of()));
            }
            if (delta.get("tool_calls") != null) {
                if (!(delta.get("tool_calls") instanceof List<?> fragments))
                    throw error("MODEL_BAD_RESPONSE");
                for (Object raw : fragments) {
                    Map<String, Object> fragment = Values.object(raw);
                    if (!(fragment.get("index") instanceof Integer index)
                            || index < 0
                            || index >= 64) throw error("MODEL_BAD_RESPONSE");
                    var target = calls.computeIfAbsent(index, ignored -> new LinkedHashMap<>());
                    appendFragment(target, "id", fragment.get("id"));
                    if (fragment.containsKey("type") && !"function".equals(fragment.get("type")))
                        throw error("MODEL_BAD_RESPONSE");
                    if (fragment.containsKey("function")) {
                        var function = Values.object(fragment.get("function"));
                        appendFragment(target, "name", function.get("name"));
                        appendFragment(target, "arguments", function.get("arguments"));
                    }
                }
            }
            if (choice.get("finish_reason") != null) {
                if (!(choice.get("finish_reason") instanceof String value))
                    throw error("MODEL_BAD_RESPONSE");
                finish = value;
            }
        }

        private void appendFragment(Map<String, StringBuilder> target, String key, Object raw) {
            if (raw == null) return;
            if (!(raw instanceof String value)) throw error("MODEL_BAD_RESPONSE");
            target.computeIfAbsent(key, ignored -> new StringBuilder()).append(value);
        }

        @Override
        public void onError(Throwable cause) {
            body.completeExceptionally(cause);
        }

        @Override
        public void onComplete() {
            try {
                if (!done || !line.toString().isBlank() || !data.isEmpty() || finish == null)
                    throw error("MODEL_STREAM_INCOMPLETE");
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("role", "assistant");
                message.put("content", text.toString());
                if (!calls.isEmpty()) {
                    List<Map<String, Object>> assembled = new ArrayList<>();
                    int expected = 0;
                    for (var entry : calls.entrySet()) {
                        if (entry.getKey() != expected++) throw error("MODEL_BAD_RESPONSE");
                        var parts = entry.getValue();
                        assembled.add(
                                Map.of(
                                        "id",
                                        parts.getOrDefault("id", new StringBuilder()).toString(),
                                        "type",
                                        "function",
                                        "function",
                                        Map.of(
                                                "name",
                                                parts.getOrDefault("name", new StringBuilder())
                                                        .toString(),
                                                "arguments",
                                                parts.getOrDefault("arguments", new StringBuilder())
                                                        .toString())));
                    }
                    message.put("tool_calls", assembled);
                }
                if (calls.isEmpty() && !"stop".equals(finish)
                        || !calls.isEmpty() && !"tool_calls".equals(finish))
                    throw error("MODEL_INCOMPLETE");
                ChatResponse response =
                        parse(
                                json.writeValueAsBytes(
                                        Map.of(
                                                "choices",
                                                List.of(
                                                        Map.of(
                                                                "message",
                                                                message,
                                                                "finish_reason",
                                                                finish)),
                                                "usage",
                                                usage)));
                body.complete(response);
            } catch (Exception ex) {
                body.completeExceptionally(
                        ex instanceof HarnessException ? ex : error("MODEL_BAD_RESPONSE"));
            }
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        LimitedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > limit) {
                    subscription.cancel();
                    body.completeExceptionally(error("MODEL_RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable cause) {
            body.completeExceptionally(cause);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
