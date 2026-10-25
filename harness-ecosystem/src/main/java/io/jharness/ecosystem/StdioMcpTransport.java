package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.process.LocalProcessRunner;
import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit trusted host process, minimal inherited environment; not a container execution provider.
 */
public final class StdioMcpTransport implements McpTransport {
    private final Process process;
    private final InputStream input;
    private final OutputStream output;
    private final Duration timeout;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean cleanupUnknown;
    private final Object closeLock = new Object();

    public StdioMcpTransport(List<String> command, Path cwd, Duration timeout) throws IOException {
        this(() -> start(command, cwd, timeout), timeout);
    }

    private static Process start(List<String> command, Path cwd, Duration timeout)
            throws IOException {
        McpIo.bounded(timeout);
        if (command.isEmpty()
                || command.size() > 256
                || command.stream().mapToInt(String::length).sum() > 32768)
            throw new IllegalArgumentException("Invalid MCP command");
        var builder = new ProcessBuilder(List.copyOf(command)).directory(cwd.toRealPath().toFile());
        builder.environment().clear();
        builder.environment().putAll(LocalProcessRunner.buildEnvironment());
        return builder.start();
    }

    /** Package-local ownership seam: exercises post-start failure without exhausting the JVM. */
    StdioMcpTransport(Process started, Duration timeout) throws IOException {
        this(() -> Objects.requireNonNull(started), timeout);
    }

    @FunctionalInterface
    private interface ProcessStarter {
        Process start() throws IOException;
    }

    private StdioMcpTransport(ProcessStarter starter, Duration timeout) throws IOException {
        this.timeout = McpIo.bounded(timeout);
        // Instance ownership state is initialized before the production starter can acquire
        // an OS process. Constructor argument evaluation must not start that process early.
        process = starter.start();
        try {
            input = new BufferedInputStream(process.getInputStream());
            output = process.getOutputStream();
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try (var errors = process.getErrorStream()) {
                                    long size = 0;
                                    byte[] buffer = new byte[4096];
                                    int count;
                                    while ((count = errors.read(buffer)) != -1)
                                        if ((size += count) > 8 * 1024 * 1024) {
                                            try {
                                                close();
                                            } catch (HarnessException unavailable) {
                                                /* Owner close reports retained cleanupUnknown. */
                                            }
                                            break;
                                        }
                                } catch (IOException ignored) {
                                    /* Never persist server stderr or credentials. */
                                }
                            });
        } catch (RuntimeException | Error failure) {
            if (!ProcessCleanup.stop(process))
                failure.addSuppressed(BoundedJson.error("MCP_CLEANUP_UNKNOWN"));
            throw failure;
        }
    }

    @Override
    public String kind() {
        return "stdio-host";
    }

    @Override
    public synchronized Map<String, Object> exchange(
            Map<String, Object> message, CancellationToken cancellation) {
        if (closed.get()) throw BoundedJson.error("MCP_CLOSED");
        byte[] encoded = BoundedJson.encode(message);
        return McpIo.run(
                () -> {
                    send(encoded);
                    if (!McpIo.expectsResponse(message)) return Map.of();
                    int total = 0;
                    for (int frames = 0; frames < 128; frames++) {
                        byte[] raw = McpIo.line(input, BoundedJson.LIMIT - total);
                        total += raw.length + 1;
                        var incoming = BoundedJson.decode(raw);
                        if (McpIo.response(incoming, message.get("id"))) return incoming;
                        if (!incoming.containsKey("method"))
                            throw BoundedJson.error("MCP_RESPONSE_ID_MISMATCH");
                        if (incoming.containsKey("id"))
                            send(BoundedJson.encode(McpIo.rejection(incoming)));
                        else McpIo.notification(incoming);
                    }
                    throw BoundedJson.error("MCP_MESSAGE_LIMIT");
                },
                timeout,
                cancellation,
                this::close);
    }

    private void send(byte[] bytes) throws IOException {
        output.write(bytes);
        output.write('\n');
        output.flush();
    }

    @Override
    public void close() {
        synchronized (closeLock) {
            if (closed.compareAndSet(false, true)) cleanupUnknown = !ProcessCleanup.stop(process);
            if (cleanupUnknown) throw BoundedJson.error("MCP_CLEANUP_UNKNOWN");
        }
    }
}
