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
        cancellation.throwIfCancelled();
        Process process = null;
        try {
            var builder = new ProcessBuilder(command);
            builder.environment().clear();
            builder.environment().putAll(LocalProcessRunner.buildEnvironment());
            process = builder.start();
            final Process child = process;
            CompletableFuture<byte[]> out = read(child.getInputStream(), BoundedJson.LIMIT),
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
            long deadline = System.nanoTime() + timeout.toNanos();
            while (child.isAlive() || !out.isDone() || !err.isDone() || !sent.isDone()) {
                if (cancellation.isCancelled() || System.nanoTime() > deadline) {
                    ProcessCleanup.stop(child);
                    return new Reply(-1, new byte[0], new byte[0], true, true);
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
            return new Reply(-1, new byte[0], new byte[0], process != null, process != null);
        } catch (IOException | ExecutionException ex) {
            return new Reply(-1, new byte[0], new byte[0], process != null, process != null);
        } finally {
            if (process != null) {
                ProcessCleanup.stop(process);
            }
        }
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
