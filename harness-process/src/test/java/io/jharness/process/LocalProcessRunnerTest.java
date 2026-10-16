package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalProcessRunnerTest {
    @TempDir Path root;

    LocalProcessRunner runner(int tree, int bytes) throws Exception {
        return new LocalProcessRunner(
                new LocalWorkspace(root),
                new ProcessLimits(Duration.ofSeconds(10), 1, tree, bytes, StandardCharsets.UTF_8),
                LocalProcessRunner.buildEnvironment(),
                List.of("very-private-value"));
    }

    CommandRequest request(List<String> argv, Duration timeout) {
        return new CommandRequest("c", CommandRequest.Mode.ARGV, argv, new ExecPath("."), timeout);
    }

    @Test
    void exitCodesSeparateFailureAndConsumeBothStreamsWithExactArgumentBoundaries()
            throws Exception {
        try (var runner = runner(8, 8192)) {
            ToolResult result =
                    runner.execute(
                            request(
                                    ProcessProbe.command("echo", "中文 space & ; literal", "7"),
                                    Duration.ofSeconds(5)),
                            CancellationToken.NONE);
            assertEquals(ToolStatus.FAILED, result.status());
            assertEquals(7, result.value().get("exitCode"));
            assertEquals("中文 space & ; literal", result.value().get("stdout"));
            assertEquals("diagnostic", result.value().get("stderr"));
            assertEquals("partial", result.value().get("cleanupStatus"));
            assertEquals(true, result.value().get("observedClean"));
            assertEquals("NONZERO_EXIT", result.error().orElseThrow().code());
        }
    }

    @Test
    void stdinIsClosedAndSuccessHasNoDetachedReader() throws Exception {
        try (var runner = runner(8, 8192)) {
            ToolResult result =
                    runner.execute(
                            request(ProcessProbe.command("stdin"), Duration.ofSeconds(5)),
                            CancellationToken.NONE);
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertEquals("-1", result.value().get("stdout").toString().trim());
            assertEquals(List.of(), result.value().get("residualPids"));
        }
    }

    @Test
    void floodingBothStreamsIsBoundedAndTimeoutReleasesAdmissionSlot() throws Exception {
        try (var runner = runner(8, 512)) {
            ToolResult flood =
                    runner.execute(
                            request(ProcessProbe.command("flood"), Duration.ofMillis(1000)),
                            CancellationToken.NONE);
            assertEquals(ToolStatus.TIMEOUT, flood.status());
            assertEquals(true, flood.value().get("truncated"));
            assertEquals(true, flood.value().get("timedOut"));
            assertTrue(flood.value().get("stdout").toString().length() < 600);
            assertTrue(flood.value().get("stderr").toString().length() < 600);
            assertEquals(
                    ToolStatus.SUCCESS,
                    runner.execute(
                                    request(
                                            ProcessProbe.command("echo", "ok", "0"),
                                            Duration.ofSeconds(5)),
                                    CancellationToken.NONE)
                            .status());
        }
    }

    @Test
    void cancellationKillsObservedRealChildTreeAndPreservesHonestScope() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean();
        Path pidFile = root.resolve("child.pid");
        try (var runner = runner(8, 8192);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> future =
                    executor.submit(
                            () ->
                                    runner.execute(
                                            request(
                                                    ProcessProbe.command(
                                                            "tree", pidFile.toString()),
                                                    Duration.ofSeconds(8)),
                                            cancel::get));
            awaitFile(pidFile);
            long pid = Long.parseLong(Files.readString(pidFile));
            try {
                Thread.sleep(100);
                cancel.set(true);
                ToolResult result = future.get(5, TimeUnit.SECONDS);
                assertEquals(ToolStatus.CANCELLED, result.status());
                assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
                assertTrue(((List<?>) result.value().get("trackedPids")).contains(pid));
                assertEquals("partial", result.value().get("cleanupStatus"));
                assertTrue(result.value().get("limitations").toString().contains("No Job Object"));
            } finally {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                cancel.set(true);
            }
        }
    }

    @Test
    void deniedTerminationReportsResidualAndUnknownRatherThanClaimingClean() throws Exception {
        var runner =
                new LocalProcessRunner(
                        new LocalWorkspace(root),
                        new ProcessLimits(Duration.ofSeconds(2), 1, 8, 512),
                        LocalProcessRunner.buildEnvironment(),
                        List.of(),
                        handle -> {});
        ToolResult result =
                runner.execute(
                        request(ProcessProbe.command("block"), Duration.ofMillis(500)),
                        CancellationToken.NONE);
        try {
            assertEquals(ToolStatus.UNKNOWN, result.status());
            assertEquals("failed", result.value().get("cleanupStatus"));
            assertFalse(((List<?>) result.value().get("residualPids")).isEmpty());
        } finally {
            for (Object pid : (List<?>) result.value().get("residualPids")) {
                var handle = ProcessHandle.of(((Number) pid).longValue());
                if (handle.isPresent()) {
                    handle.get().destroyForcibly();
                    handle.get().onExit().get(5, TimeUnit.SECONDS);
                }
            }
            runner.close();
        }
    }

    @Test
    void concurrentCeilingAndCloseCancelActiveCommand() throws Exception {
        var runner = runner(8, 8192);
        AtomicBoolean started = new AtomicBoolean();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> active =
                    executor.submit(
                            () ->
                                    runner.execute(
                                            request(
                                                    ProcessProbe.command("block"),
                                                    Duration.ofSeconds(8)),
                                            () -> {
                                                started.set(true);
                                                return false;
                                            }));
            while (!started.get()) Thread.sleep(5);
            Thread.sleep(200);
            assertEquals(
                    "PROCESS_LIMIT",
                    runner.execute(
                                    request(ProcessProbe.command("stdin"), Duration.ofSeconds(5)),
                                    CancellationToken.NONE)
                            .error()
                            .orElseThrow()
                            .code());
            runner.close();
            assertEquals(ToolStatus.CANCELLED, active.get(5, TimeUnit.SECONDS).status());
            assertEquals(
                    "PROCESS_SERVICE_CLOSED",
                    runner.execute(
                                    request(ProcessProbe.command("stdin"), Duration.ofSeconds(5)),
                                    CancellationToken.NONE)
                            .error()
                            .orElseThrow()
                            .code());
        } finally {
            runner.close();
        }
    }

    @Test
    void noInheritedSecretEnvironmentAndApprovedValuesScrubbed() throws Exception {
        Map<String, String> environment = new HashMap<>(LocalProcessRunner.buildEnvironment());
        environment.put("JHARNESS_APPROVED_VALUE", "very-private-value");
        try (var runner =
                new LocalProcessRunner(
                        new LocalWorkspace(root),
                        ProcessLimits.defaults(),
                        environment,
                        List.of("very-private-value"))) {
            ToolResult result =
                    runner.execute(
                            request(ProcessProbe.command("env"), Duration.ofSeconds(5)),
                            CancellationToken.NONE);
            assertTrue(result.value().get("stdout").toString().contains("null"));
            assertTrue(result.value().get("stdout").toString().contains("[REDACTED]"));
            assertFalse(result.toString().contains("very-private-value"));
            assertFalse(
                    result.value()
                            .get("environment")
                            .toString()
                            .contains(environment.getOrDefault("PATH", "nonexistent-path")));
        }
    }

    @Test
    void invalidWorkingDirectoryAndBatchShellCannotSneakThroughArgv() throws Exception {
        try (var runner = runner(8, 8192)) {
            ToolResult path =
                    runner.execute(
                            new CommandRequest(
                                    "c",
                                    CommandRequest.Mode.ARGV,
                                    ProcessProbe.command("stdin"),
                                    new ExecPath(".."),
                                    Duration.ofSeconds(1)),
                            CancellationToken.NONE);
            assertEquals(ToolStatus.DENIED, path.status());
            assertEquals(
                    "SHELL_REQUIRED",
                    runner.execute(
                                    request(
                                            List.of("cmd.exe", "/c", "echo x"),
                                            Duration.ofSeconds(1)),
                                    CancellationToken.NONE)
                            .error()
                            .orElseThrow()
                            .code());
            assertEquals(
                    ToolStatus.CANCELLED,
                    runner.execute(
                                    request(ProcessProbe.command("stdin"), Duration.ofSeconds(1)),
                                    () -> true)
                            .status());
        }
    }

    @Test
    void explicitNativeShellExecutesBatchAndTreeLimitStopsSpawn() throws Exception {
        Path pidFile = root.resolve("tree-limit.pid");
        ToolResult limited = null;
        try (var runner = runner(1, 8192)) {
            limited =
                    runner.execute(
                            request(
                                    ProcessProbe.command("tree", pidFile.toString()),
                                    Duration.ofSeconds(5)),
                            CancellationToken.NONE);
            assertEquals("PROCESS_LIMIT", limited.value().get("reason"));
            assertEquals(List.of(), limited.value().get("residualPids"));
            if (System.getProperty("os.name").startsWith("Windows")) {
                Files.writeString(
                        root.resolve("fixture.cmd"),
                        "@echo off\r\necho wrapper-like\r\nexit /b 9\r\n");
                ToolResult result =
                        runner.execute(
                                new CommandRequest(
                                        "c",
                                        CommandRequest.Mode.CMD,
                                        List.of("fixture.cmd"),
                                        new ExecPath("."),
                                        Duration.ofSeconds(5)),
                                CancellationToken.NONE);
                assertEquals(9, result.value().get("exitCode"));
                assertTrue(result.value().get("stdout").toString().contains("wrapper-like"));
            }
        } finally {
            cleanupLateFixtureChild(Path.of(pidFile + ".child"), limited);
        }
    }

    // A fixture child can start after the runner's final ProcessHandle snapshot.
    // Clean only its exact registered identity; retain all product assertions above.
    static void cleanupLateFixtureChild(Path manifest, ToolResult result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        List<String> identity = List.of();
        while (System.nanoTime() < deadline) {
            if (Files.exists(manifest)) {
                identity = Files.readAllLines(manifest);
                if (identity.size() == 2 && !identity.get(1).isBlank()) break;
            }
            Thread.sleep(10);
        }
        if (identity.size() != 2) return; // Killed before registration in main.
        long pid = Long.parseLong(identity.get(0));
        var started = java.time.Instant.parse(identity.get(1));
        var handle = ProcessHandle.of(pid);
        if (handle.isEmpty()
                || !handle.get().isAlive()
                || handle.get().info().startInstant().filter(started::equals).isEmpty()) return;
        try {
            if (result != null) {
                assertFalse(
                        ((List<?>) result.value().get("trackedPids")).contains(pid),
                        "runner left a tracked fixture process alive");
                assertEquals("partial", result.value().get("cleanupStatus"));
                assertTrue(result.value().get("limitations").toString().contains("unobserved"));
            }
        } finally {
            handle.get().destroyForcibly();
            handle.get().onExit().get(5, TimeUnit.SECONDS);
        }
        assertFalse(handle.get().isAlive());
    }

    static void awaitFile(Path file) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((!Files.exists(file) || Files.size(file) == 0) && System.nanoTime() < deadline)
            Thread.sleep(10);
        assertTrue(Files.exists(file), "fixture did not start");
    }
}
