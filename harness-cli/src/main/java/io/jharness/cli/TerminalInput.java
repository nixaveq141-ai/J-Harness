package io.jharness.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** One daemon stdin reader for the entire CLI/REPL; runtime never blocks on readLine. */
final class TerminalInput implements AutoCloseable {
    private final BlockingQueue<String> lines = new ArrayBlockingQueue<>(64);
    private volatile boolean ended, closed;
    private final Thread reader;

    TerminalInput(InputStream input) {
        reader =
                Thread.ofPlatform()
                        .daemon()
                        .name("cli-stdin")
                        .start(
                                () -> {
                                    try {
                                        var buffered =
                                                new BufferedReader(
                                                        new InputStreamReader(
                                                                input,
                                                                input == System.in
                                                                                && System.console()
                                                                                        != null
                                                                        ? System.console().charset()
                                                                        : StandardCharsets.UTF_8));
                                        StringBuilder line = new StringBuilder();
                                        int value;
                                        while (!closed && (value = buffered.read()) >= 0) {
                                            if (value == '\n') {
                                                lines.put(line.toString().replaceAll("\r$", ""));
                                                line.setLength(0);
                                            } else {
                                                if (line.length() >= 1024 * 1024)
                                                    throw new IOException("Input bound");
                                                line.append((char) value);
                                            }
                                        }
                                        if (!line.isEmpty() && !closed) lines.put(line.toString());
                                    } catch (IOException | InterruptedException ex) {
                                        /* EOF/failure becomes denial, never implicit approval */
                                    } finally {
                                        ended = true;
                                    }
                                });
    }

    String poll(Duration timeout) throws InterruptedException {
        return lines.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    boolean eof() {
        return ended && lines.isEmpty();
    }

    boolean takeCancel() {
        if (":cancel".equals(lines.peek())) {
            lines.poll();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        closed = true;
        reader.interrupt();
    }
}
