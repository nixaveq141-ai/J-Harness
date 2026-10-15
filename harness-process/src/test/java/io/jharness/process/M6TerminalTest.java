package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M6TerminalTest {
    @TempDir Path root;

    private LocalTerminalSession terminal() throws Exception {
        return new LocalTerminalSession(
                new LocalWorkspace(root),
                LocalProcessRunner.buildEnvironment(),
                List.of("terminal-secret"));
    }

    private CommandRequest command(boolean windows, String text, int timeout) {
        return new CommandRequest(
                "call",
                windows ? CommandRequest.Mode.CMD : CommandRequest.Mode.SH,
                List.of(text),
                new ExecPath("."),
                Duration.ofMillis(timeout));
    }

    private boolean windows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private void waitForExit(LocalTerminalSession terminal, String id) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(6).toNanos();
        while (Boolean.TRUE.equals(terminal.status(id).get("alive")) && System.nanoTime() < until)
            Thread.sleep(20);
        assertEquals(false, terminal.status(id).get("alive"));
    }

    @Test
    void realPtyAcceptsInputResizesAndPreservesItsWorkingDirectory() throws Exception {
        try (var terminal = terminal()) {
            var started =
                    terminal.start(
                            command(
                                    windows(),
                                    windows()
                                            ? "set /p line=INPUT: & echo RECEIVED & cd"
                                            : "read line; echo RECEIVED; pwd",
                                    5000),
                            true,
                            CancellationToken.NONE);
            String id = (String) started.get("id");
            assertEquals(true, started.get("pty"));
            assertEquals(true, terminal.resize(id, 80, 24).get("pty"));
            terminal.write(id, windows() ? "hello\r" : "hello\n", CancellationToken.NONE);
            waitForExit(terminal, id);
            long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            String text = "";
            while (System.nanoTime() < until) {
                text = (String) terminal.log(id, 0, 65536).get("text");
                if (text.contains("RECEIVED") && text.contains(root.getFileName().toString()))
                    break;
                Thread.sleep(20);
            }
            assertTrue(text.contains("RECEIVED"), text);
            assertTrue(text.contains(root.getFileName().toString()), text);
        }
    }

    @Test
    void backgroundTaskStatusLogStopAndTimeoutAreBounded() throws Exception {
        try (var terminal = terminal()) {
            var started =
                    terminal.start(
                            command(
                                    windows(),
                                    windows()
                                            ? "echo terminal-secret & ping -n 10 127.0.0.1 >nul"
                                            : "printf terminal-secret; sleep 10",
                                    3000),
                            false,
                            CancellationToken.NONE);
            String id = (String) started.get("id");
            Thread.sleep(150);
            assertTrue(
                    ((String) terminal.log(id, 0, 65536).get("text")).indexOf("terminal-secret")
                            < 0);
            var stopped = terminal.stop(id);
            assertEquals("STOPPED", stopped.get("state"));
            waitForExit(terminal, id);
            assertTrue(((List<?>) stopped.get("residualPids")).isEmpty());
            assertEquals("partial", stopped.get("cleanupStatus"));
            var timed =
                    terminal.start(
                            command(
                                    windows(),
                                    windows() ? "ping -n 10 127.0.0.1 >nul" : "sleep 10",
                                    100),
                            false,
                            CancellationToken.NONE);
            String timedId = (String) timed.get("id");
            waitForExit(terminal, timedId);
            assertEquals("TIMED_OUT", terminal.status(timedId).get("state"));
        }
    }

    @Test
    void cancellationTraversalInvalidPagesAndCloseNeverStartOrLeakTasks() throws Exception {
        var terminal = terminal();
        assertThrows(
                HarnessException.class,
                () -> terminal.start(command(windows(), "echo x", 1000), false, () -> true));
        assertThrows(
                HarnessException.class,
                () ->
                        terminal.start(
                                new CommandRequest(
                                        "call",
                                        CommandRequest.Mode.ARGV,
                                        List.of("ignored"),
                                        new ExecPath("../outside"),
                                        Duration.ofSeconds(1)),
                                false,
                                CancellationToken.NONE));
        var started =
                terminal.start(
                        command(
                                windows(),
                                windows() ? "ping -n 10 127.0.0.1 >nul" : "sleep 10",
                                3000),
                        false,
                        CancellationToken.NONE);
        String id = (String) started.get("id");
        assertThrows(HarnessException.class, () -> terminal.log(id, -1, 1));
        assertThrows(HarnessException.class, () -> terminal.log(id, 0, 65537));
        assertThrows(HarnessException.class, () -> terminal.resize(id, 80, 24));
        terminal.close();
        terminal.close();
        waitForExit(terminal, id);
        assertThrows(
                HarnessException.class,
                () ->
                        terminal.start(
                                command(windows(), "echo x", 1000), false, CancellationToken.NONE));
    }

    @Test
    void rollingLogsAndConcurrentTaskQuotaStayBounded() throws Exception {
        try (var terminal = terminal()) {
            var request =
                    new CommandRequest(
                            "flood",
                            CommandRequest.Mode.ARGV,
                            ProcessProbe.command("flood"),
                            new ExecPath("."),
                            Duration.ofSeconds(4));
            String id = (String) terminal.start(request, false, CancellationToken.NONE).get("id");
            long until = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (((Number) terminal.status(id).get("firstOffset")).longValue() == 0
                    && System.nanoTime() < until) Thread.sleep(20);
            terminal.stop(id);
            long first = ((Number) terminal.status(id).get("firstOffset")).longValue();
            assertTrue(first > 0);
            assertEquals(
                    "TERMINAL_LOG_ROTATED",
                    assertThrows(HarnessException.class, () -> terminal.log(id, 0, 1)).code());
            assertTrue(((String) terminal.log(id, first, 4096).get("text")).length() <= 4096);
            for (int index = 0; index < 4; index++)
                terminal.start(
                        new CommandRequest(
                                "block-" + index,
                                CommandRequest.Mode.ARGV,
                                ProcessProbe.command("block"),
                                new ExecPath("."),
                                Duration.ofSeconds(5)),
                        false,
                        CancellationToken.NONE);
            assertEquals(
                    "TERMINAL_LIMIT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> terminal.start(request, false, CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void cancellingBlockedStdinStopsProcessAndReturnsUnknownWithinTheDeadline() throws Exception {
        try (var terminal = terminal();
                var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            String id =
                    (String)
                            terminal.start(
                                            new CommandRequest(
                                                    "blocked-input",
                                                    CommandRequest.Mode.ARGV,
                                                    ProcessProbe.command("block"),
                                                    new ExecPath("."),
                                                    Duration.ofSeconds(10)),
                                            false,
                                            CancellationToken.NONE)
                                    .get("id");
            java.util.concurrent.atomic.AtomicBoolean cancel =
                    new java.util.concurrent.atomic.AtomicBoolean();
            // A POSIX pipe may accept an entire 64 KiB write. Fill it before testing a blocked
            // write.
            var pending =
                    executor.submit(
                            () -> {
                                for (int i = 0; i < 64; i++)
                                    terminal.write(id, "x".repeat(65536), cancel::get);
                                return null;
                            });
            Thread.sleep(100);
            cancel.set(true);
            var failure =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> pending.get(4, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("TERMINAL_INPUT_UNKNOWN", ((HarnessException) failure.getCause()).code());
            waitForExit(terminal, id);
        }
    }
}
