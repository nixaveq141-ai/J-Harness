package io.jharness.ecosystem;

import io.jharness.api.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Fixed trusted source aliases only. Fetches are always approved host network operations. */
public final class WebFetchPlugin implements HarnessPlugin {
    private final Map<String, URI> sources;
    private final AtomicReference<InputStream> active = new AtomicReference<>();
    private HttpClient client;
    private ExecutorService reads;
    private PluginScope scope;
    private volatile boolean closed = true;

    public WebFetchPlugin(Map<String, URI> sources) {
        this.sources = Map.copyOf(sources);
        if (sources.size() > 16) throw new IllegalArgumentException("Source limit");
        sources.forEach(
                (name, uri) -> {
                    if (!name.matches("[A-Za-z0-9_-]{1,32}")
                            || uri.getUserInfo() != null
                            || uri.getFragment() != null
                            || uri.toString().length() > 2048
                            || uri.getHost() == null
                            || !("https".equals(uri.getScheme())
                                    || "http".equals(uri.getScheme())
                                            && Set.of("localhost", "127.0.0.1", "[::1]")
                                                    .contains(uri.getHost())))
                        throw new IllegalArgumentException(
                                "HTTPS or explicit local source required");
                });
    }

    public String id() {
        return "web-fetch";
    }

    public String version() {
        return "1.7";
    }

    public void start(PluginContext context) {
        if (client != null || sources.isEmpty()) throw BoundedJson.error("INVALID_WEB_SOURCES");
        scope = context.scope();
        client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        reads =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new SynchronousQueue<>(),
                        r -> {
                            var t = new Thread(r, "web-fetch-read");
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        closed = false;
        context.own(this::shutdown);
        context.own(
                context.services()
                        .requireUnique(ToolRegistry.class, scope)
                        .register(new Tool(), scope));
    }

    public void stop() {
        closed = true;
    }

    private void shutdown() {
        closed = true;
        var input = active.getAndSet(null);
        try {
            if (input != null) input.close();
        } catch (IOException ignored) {
        }
        if (reads != null) reads.shutdownNow();
        if (client != null) client.shutdownNow();
    }

    private <T> T await(Future<T> value, CancellationToken token, long deadline) throws Exception {
        while (System.nanoTime() < deadline) {
            token.throwIfCancelled();
            if (closed) throw BoundedJson.error("PLUGIN_CLOSED");
            try {
                return value.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            }
        }
        throw BoundedJson.error("WEB_FETCH_TIMEOUT");
    }

    private final class Tool implements AgentTool {
        public String name() {
            return "web_fetch";
        }

        public String description() {
            return "Fetch untrusted text from a fixed host-approved source. Never follows redirects or executes page content.";
        }

        public SideEffectLevel sideEffectLevel() {
            return SideEffectLevel.SYSTEM_IMPACT;
        }

        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "source",
                            Map.of("type", "string", "enum", List.copyOf(sources.keySet()))),
                    "required",
                    List.of("source"),
                    "additionalProperties",
                    false);
        }

        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        public synchronized ToolResult execute(ToolInvocation call, ToolContext context) {
            if (closed || !scope.equals(context.scope())) throw BoundedJson.error("SCOPE_DENIED");
            var uri = sources.get(call.arguments().get("source"));
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(10))
                            .header("Accept", "text/plain, text/html, application/json")
                            .GET()
                            .build();
            CompletableFuture<HttpResponse<InputStream>> pending = null;
            Future<byte[]> reading = null;
            try {
                context.cancellation().throwIfCancelled();
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
                // Closing a late response avoids leaking its body after cancellation before
                // headers.
                pending.thenAccept(
                        response -> {
                            if (closed || context.cancellation().isCancelled())
                                try {
                                    response.body().close();
                                } catch (IOException ignored) {
                                }
                        });
                var response = await(pending, context.cancellation(), deadline);
                var input = response.body();
                active.set(input);
                if (response.statusCode() != 200) throw BoundedJson.error("WEB_FETCH_HTTP_ERROR");
                String type =
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("")
                                .toLowerCase(Locale.ROOT);
                if (!(type.startsWith("text/") || type.startsWith("application/json")))
                    throw BoundedJson.error("WEB_FETCH_CONTENT_TYPE");
                reading = reads.submit(() -> input.readNBytes(1024 * 1024 + 1));
                byte[] bytes = await(reading, context.cancellation(), deadline);
                if (bytes.length > 1024 * 1024) throw BoundedJson.error("WEB_FETCH_LIMIT");
                var value =
                        Map.<String, Object>of(
                                "url",
                                uri.toString(),
                                "untrusted",
                                true,
                                "sha256",
                                java.util.HexFormat.of()
                                        .formatHex(
                                                java.security.MessageDigest.getInstance("SHA-256")
                                                        .digest(bytes)),
                                "content",
                                "Untrusted external content:\n"
                                        + new String(bytes, StandardCharsets.UTF_8));
                return new ToolResult(
                        call.callId(),
                        ToolStatus.SUCCESS,
                        value,
                        BoundedJson.text(value),
                        value,
                        Optional.empty());
            } catch (HarnessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw BoundedJson.error("WEB_FETCH_FAILED");
            } finally {
                if (pending != null && !pending.isDone()) pending.cancel(true);
                var input = active.getAndSet(null);
                try {
                    if (input != null) input.close();
                } catch (IOException ignored) {
                }
                if (reading != null) reading.cancel(true);
            }
        }
    }
}
