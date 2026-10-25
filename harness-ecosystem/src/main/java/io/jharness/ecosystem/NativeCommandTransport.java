package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.process.LocalProcessRunner;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Full duplex bounded control transport. Commands are host configuration, not model shell text. */
public final class NativeCommandTransport implements CommandTransport {
    @Override
    public Reply call(
            List<String> command, byte[] stdin, Duration timeout, CancellationToken cancellation) {
        if (stdin.length > BoundedJson.LIMIT) throw BoundedJson.error("PAYLOAD_LIMIT");
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(cancellation);
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Transport timeout must be in (0, 1h]");
        final long deadline = System.nanoTime() + timeout.toNanos();
        cancellation.throwIfCancelled();
        Process process = null;
        CompletableFuture<byte[]> out = null, err = null;
        try {
            var builder = new ProcessBuilder(command);
            builder.environment().clear();
            builder.environment().putAll(LocalProcessRunner.buildEnvironment());
            process = builder.start();
            final Process child = process;
            if (System.nanoTime() > deadline || cancellation.isCancelled()) {
                ProcessCleanup.stop(child);
                return new Reply(
                        exit(child),
                        new byte[0],
                        new byte[0],
                        true,
                        true,
                        cancellation.isCancelled() ? "CANCELLED" : "TIMEOUT");
            }
            out = read(child.getInputStream(), BoundedJson.LIMIT);
            err = read(child.getErrorStream(), 65536);
            CompletableFuture<Void> sent = new CompletableFuture<>();
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try (var input = child.getOutputStream()) {
                                    input.write(stdin);
                                    sent.complete(null);
                                } catch (IOException ex) {
                                    sent.completeExceptionally(ex);
                                }
                            });
            while (child.isAlive() || !out.isDone() || !err.isDone() || !sent.isDone()) {
                if (out.isCompletedExceptionally()
                        || err.isCompletedExceptionally()
                        || sent.isCompletedExceptionally()) {
                    ProcessCleanup.stop(child);
                    return new Reply(
                            exit(child),
                            observed(out),
                            observed(err),
                            true,
                            true,
                            "CONTROL_EXCHANGE_FAILED");
                }
                if (cancellation.isCancelled() || System.nanoTime() > deadline) {
                    ProcessCleanup.stop(child);
                    return new Reply(
                            -1,
                            observed(out),
                            observed(err),
                            true,
                            true,
                            cancellation.isCancelled() ? "CANCELLED" : "TIMEOUT");
                }
                Thread.sleep(10);
            }
            sent.get();
            byte[] stdout = out.get(), stderr = err.get();
            int exitCode = child.exitValue();
            boolean observedClean = ProcessCleanup.stop(child);
            return new Reply(exitCode, stdout, stderr, true, !observedClean);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Reply(
                    exit(process),
                    observed(out),
                    observed(err),
                    process != null,
                    process != null,
                    "INTERRUPTED");
        } catch (IOException | ExecutionException ex) {
            return new Reply(
                    exit(process),
                    observed(out),
                    observed(err),
                    process != null,
                    process != null,
                    ex instanceof IOException ? "CONTROL_IO" : "CONTROL_EXCHANGE_FAILED");
        } finally {
            if (process != null) {
                ProcessCleanup.stop(process);
            }
        }
    }

    private static int exit(Process process) {
        return process != null && !process.isAlive() ? process.exitValue() : -1;
    }

    private static byte[] observed(CompletableFuture<byte[]> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally())
            return new byte[0];
        return future.getNow(new byte[0]);
    }

    private static CompletableFuture<byte[]> read(InputStream input, int limit) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        Thread.ofVirtual()
                .start(
                        () -> {
                            try (input) {
                                byte[] bytes = input.readNBytes(limit + 1);
                                if (bytes.length > limit)
                                    throw new IOException("control output bound exceeded");
                                result.complete(bytes);
                            } catch (IOException ex) {
                                result.completeExceptionally(ex);
                            }
                        });
        return result;
    }
}
