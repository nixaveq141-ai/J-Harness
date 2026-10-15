package io.jharness.ecosystem;

import io.jharness.api.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

final class McpIo {
    private McpIo() {}

    static Duration bounded(Duration duration) {
        if (duration == null
                || duration.isNegative()
                || duration.isZero()
                || duration.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("MCP timeout must be between zero and 60 seconds");
        return duration;
    }

    static <T> T run(
            Callable<T> action, Duration timeout, CancellationToken token, Runnable abort) {
        token.throwIfCancelled();
        var result = new CompletableFuture<T>();
        var thread =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        result.complete(action.call());
                                    } catch (Exception ex) {
                                        result.completeExceptionally(ex);
                                    }
                                });
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                if (token.isCancelled() || System.nanoTime() >= deadline)
                    throw BoundedJson.error("MCP_OUTCOME_UNKNOWN");
                try {
                    return result.get(20, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    /* check monotonic deadline */
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BoundedJson.error("MCP_OUTCOME_UNKNOWN");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof HarnessException failure) throw failure;
            throw BoundedJson.error("MCP_TRANSPORT_FAILED");
        } finally {
            if (!result.isDone() || result.isCompletedExceptionally()) {
                try {
                    abort.run();
                } finally {
                    thread.interrupt();
                }
            }
        }
    }

    static boolean expectsResponse(Map<String, Object> message) {
        return message.containsKey("method") && message.containsKey("id");
    }

    static boolean response(Map<String, Object> message, Object id) {
        if (!"2.0".equals(message.get("jsonrpc"))) throw BoundedJson.error("MCP_PROTOCOL_ERROR");
        return !message.containsKey("method") && Objects.equals(id, message.get("id"));
    }

    static Map<String, Object> rejection(Map<String, Object> request) {
        return Map.of(
                "jsonrpc",
                "2.0",
                "id",
                request.get("id"),
                "ping".equals(request.get("method")) ? "result" : "error",
                "ping".equals(request.get("method"))
                        ? Map.of()
                        : Map.of("code", -32601, "message", "Client capability unsupported"));
    }

    static void notification(Map<String, Object> message) {
        if ("notifications/tools/list_changed".equals(message.get("method")))
            throw BoundedJson.error("MCP_TOOLS_CHANGED");
    }

    static byte[] line(InputStream input, int limit) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (bytes.size() >= limit) throw BoundedJson.error("MCP_PAYLOAD_LIMIT");
            bytes.write(value);
        }
        if (value == -1) throw BoundedJson.error("MCP_DISCONNECTED");
        return bytes.toByteArray();
    }
}
