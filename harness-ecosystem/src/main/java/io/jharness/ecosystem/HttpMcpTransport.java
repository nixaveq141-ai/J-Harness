package io.jharness.ecosystem;

import io.jharness.api.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Bounded Streamable HTTP POST JSON/SSE; redirects, reconnects and replay are disabled. */
public final class HttpMcpTransport implements McpTransport {
    private final URI endpoint;
    private final Duration timeout;
    private final Supplier<String> bearerToken;
    private final HttpClient client;
    private final AtomicReference<InputStream> active = new AtomicReference<>();
    private String session;
    private boolean closed;
    private volatile boolean disconnected;

    public HttpMcpTransport(URI endpoint, Duration timeout, Supplier<String> bearerToken) {
        this.timeout = McpIo.bounded(timeout);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.bearerToken = Objects.requireNonNull(bearerToken);
        boolean loopback =
                Set.of("localhost", "127.0.0.1", "[::1]")
                        .contains(String.valueOf(endpoint.getHost()));
        if (endpoint.getUserInfo() != null
                || endpoint.getFragment() != null
                || endpoint.getHost() == null
                || !("https".equals(endpoint.getScheme())
                        || "http".equals(endpoint.getScheme()) && loopback))
            throw new IllegalArgumentException("MCP requires HTTPS or explicit loopback HTTP");
        client =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(timeout)
                        .build();
    }

    @Override
    public String kind() {
        return "streamable-http";
    }

    @Override
    public synchronized Map<String, Object> exchange(
            Map<String, Object> message, CancellationToken cancellation) {
        if (closed || disconnected) throw BoundedJson.error("MCP_CLOSED");
        byte[] bytes = BoundedJson.encode(message);
        return McpIo.run(() -> post(message, bytes), timeout, cancellation, this::abort);
    }

    private HttpRequest.Builder request() {
        var request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("MCP-Protocol-Version", McpPlugin.PROTOCOL_VERSION);
        if (session != null) request.header("MCP-Session-Id", session);
        String token = bearerToken.get();
        if (token != null) {
            if (token.isBlank()
                    || token.length() > 8192
                    || token.chars().anyMatch(c -> c < 0x21 || c > 0x7e))
                throw BoundedJson.error("MCP_INVALID_CREDENTIAL");
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private Map<String, Object> post(Map<String, Object> message, byte[] bytes)
            throws IOException, InterruptedException {
        var response =
                client.send(
                        request()
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json, text/event-stream")
                                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());
        try (var input = new BufferedInputStream(response.body())) {
            active.set(input);
            if (!McpIo.expectsResponse(message)) {
                if (response.statusCode() != 202 || input.read() != -1)
                    throw BoundedJson.error("MCP_PROTOCOL_ERROR");
                return Map.of();
            }
            if (response.statusCode() != 200) throw BoundedJson.error("MCP_HTTP_ERROR");
            if ("initialize".equals(message.get("method"))) {
                String assigned = response.headers().firstValue("MCP-Session-Id").orElse(null);
                if (assigned != null
                        && (assigned.isEmpty()
                                || assigned.length() > 512
                                || assigned.chars().anyMatch(c -> c < 0x21 || c > 0x7e)))
                    throw BoundedJson.error("MCP_INVALID_SESSION");
                session = assigned;
            }
            String type =
                    response.headers()
                            .firstValue("Content-Type")
                            .orElse("")
                            .split(";", 2)[0]
                            .trim()
                            .toLowerCase(Locale.ROOT);
            if (type.equals("application/json")) {
                var value = BoundedJson.decode(input.readNBytes(BoundedJson.LIMIT + 1));
                if (!McpIo.response(value, message.get("id")))
                    throw BoundedJson.error("MCP_RESPONSE_ID_MISMATCH");
                return value;
            }
            if (!type.equals("text/event-stream")) throw BoundedJson.error("MCP_CONTENT_TYPE");
            int total = 0, frames = 0;
            var data = new StringBuilder();
            while (total < BoundedJson.LIMIT && frames < 128) {
                byte[] raw = McpIo.line(input, BoundedJson.LIMIT - total);
                total += raw.length + 1;
                String line =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(raw))
                                .toString();
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                if (line.isEmpty()) {
                    if (data.isEmpty()) continue;
                    frames++;
                    var incoming =
                            BoundedJson.decode(data.toString().getBytes(StandardCharsets.UTF_8));
                    data.setLength(0);
                    if (McpIo.response(incoming, message.get("id"))) return incoming;
                    if (!incoming.containsKey("method"))
                        throw BoundedJson.error("MCP_RESPONSE_ID_MISMATCH");
                    if (incoming.containsKey("id")) {
                        var rejection = McpIo.rejection(incoming);
                        // A client response is a separate POST, never an execution capability.
                        post(rejection, BoundedJson.encode(rejection));
                        active.set(input);
                    } else McpIo.notification(incoming);
                } else if (line.startsWith("data:")) {
                    String part = line.substring(5);
                    if (part.startsWith(" ")) part = part.substring(1);
                    if (!data.isEmpty()) data.append('\n');
                    data.append(part);
                }
            }
            throw BoundedJson.error("MCP_MESSAGE_LIMIT");
        } finally {
            active.set(null);
        }
    }

    private void abort() {
        disconnected = true;
        var input = active.getAndSet(null);
        if (input != null)
            try {
                input.close();
            } catch (IOException ignored) {
                /* bounded disconnect */
            }
        client.shutdownNow();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (session != null && !disconnected)
                McpIo.run(
                        () -> {
                            var response =
                                    client.send(
                                            request().DELETE().build(),
                                            HttpResponse.BodyHandlers.ofInputStream());
                            try (var input = response.body()) {
                                active.set(input);
                                if (!Set.of(200, 204, 405).contains(response.statusCode()))
                                    throw BoundedJson.error("MCP_CLOSE_FAILED");
                            } finally {
                                active.set(null);
                            }
                            return true;
                        },
                        Duration.ofSeconds(3),
                        CancellationToken.NONE,
                        this::abort);
        } finally {
            abort();
        }
    }
}
