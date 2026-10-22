package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1RemainingProcessFaultTest {
    @TempDir Path root;

    @Test
    void exceptionalStdinFutureStopsLiveChildAndRejectsSecondWrite() throws Exception {
        try (var terminal =
                new LocalTerminalSession(
                        new LocalWorkspace(root),
                        LocalProcessRunner.buildEnvironment(),
                        List.of())) {
            String id =
                    (String)
                            terminal.start(
                                            new CommandRequest(
                                                    "broken-stdin",
                                                    CommandRequest.Mode.ARGV,
                                                    ProcessProbe.command("block"),
                                                    new ExecPath("."),
                                                    Duration.ofSeconds(15)),
                                            false,
                                            CancellationToken.NONE)
                                    .get("id");
            var field = LocalTerminalSession.class.getDeclaredField("tasks");
            field.setAccessible(true);
            Object task = ((Map<?, ?>) field.get(terminal)).get(id);
            var processField = task.getClass().getDeclaredField("process");
            processField.setAccessible(true);
            Process child = (Process) processField.get(task);
            assertTrue(child.isAlive());
            // Close only this test-owned stdin handle while the actual child remains alive.
            // The virtual writer must complete exceptionally, not time out or cancel.
            child.getOutputStream().close();
            var failure =
                    assertThrows(
                            HarnessException.class,
                            () -> terminal.write(id, "synthetic input", CancellationToken.NONE));
            assertEquals("TERMINAL_INPUT_UNKNOWN", failure.code());
            assertFalse(child.isAlive());
            assertEquals("STOPPED", terminal.status(id).get("state"));
            assertEquals(
                    "TERMINAL_EXITED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            terminal.write(
                                                    id, "do not replay", CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void cleanupSleepInterruptionStillKillsOwnedChildAndRestoresInterrupt() throws Exception {
        Path pidFile = root.resolve("owned.pid");
        var terminations = new AtomicInteger();
        var runner =
                new LocalProcessRunner(
                        new LocalWorkspace(root),
                        ProcessLimits.defaults(),
                        LocalProcessRunner.buildEnvironment(),
                        List.of(),
                        handle -> {
                            if (terminations.incrementAndGet() == 1) {
                                // Keep the child alive for the first residual check, then make the
                                // following cleanup sleep throw InterruptedException
                                // deterministically.
                                Thread.currentThread().interrupt();
                            } else handle.destroyForcibly();
                        });
        ToolResult result;
        try {
            result =
                    runner.execute(
                            new CommandRequest(
                                    "cleanup-interrupt",
                                    CommandRequest.Mode.ARGV,
                                    ProcessProbe.command("block", pidFile.toString()),
                                    new ExecPath("."),
                                    Duration.ofSeconds(10)),
                            () -> Files.exists(pidFile));
            assertTrue(Thread.currentThread().isInterrupted(), "cleanup must restore interruption");
        } finally {
            Thread.interrupted();
            runner.close();
        }
        assertTrue(terminations.get() >= 2, "sleep interruption must not abandon cleanup");
        assertEquals(ToolStatus.CANCELLED, result.status());
        assertEquals(Boolean.TRUE, result.value().get("observedClean"));
        assertEquals(List.of(), result.value().get("residualPids"));
        long pid = Long.parseLong(Files.readAllLines(pidFile).getFirst());
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }
}
