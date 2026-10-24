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
import java.util.concurrent.atomic.AtomicInteger;
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
            List<String> argv = ProcessProbe.unicodeEcho(root, "中文 space & ; literal", 7);
            ToolResult result =
                    runner.execute(request(argv, Duration.ofSeconds(5)), CancellationToken.NONE);
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
        Path commandDirectory = Files.createDirectory(root.resolve("command-cwd"));
        try (var runner = runner(8, 8192);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> future =
                    executor.submit(
                            () ->
                                    runner.execute(
                                            new CommandRequest(
                                                    "c",
                                                    CommandRequest.Mode.ARGV,
                                                    ProcessProbe.command(
                                                            "tree", pidFile.toString()),
                                                    new ExecPath("command-cwd"),
                                                    Duration.ofSeconds(8)),
                                            cancel::get));
            awaitFile(pidFile);
            long pid = Long.parseLong(Files.readString(pidFile));
            try {
                awaitFile(Path.of(pidFile + ".child"));
                cancel.set(true);
                ToolResult result = future.get(5, TimeUnit.SECONDS);
                assertEquals(ToolStatus.CANCELLED, result.status());
                assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
                assertTrue(((List<?>) result.value().get("trackedPids")).contains(pid));
                assertEquals("partial", result.value().get("cleanupStatus"));
                assertTrue(result.value().get("limitations").toString().contains("No Job Object"));
                try {
                    // Probe the exact fixture CWD immediately, rather than losing the
                    // owner evidence when JUnit later removes the temporary root.
                    Files.delete(commandDirectory);
                } catch (java.io.IOException locked) {
                    System.err.println(
                            "PROCESS_CWD_LOCK tracked="
                                    + result.value().get("trackedPids")
                                    + " residual="
                                    + result.value().get("residualPids"));
                    String diagnostic = System.getenv("JHARNESS_PROCESS_HANDLE_EXE");
                    if ("true".equals(System.getenv("GITHUB_ACTIONS")) && diagnostic != null) {
                        Process inspector =
                                new ProcessBuilder(
                                                diagnostic,
                                                "-accepteula",
                                                "-nobanner",
                                                commandDirectory.toString())
                                        .inheritIO()
                                        .start();
                        if (!inspector.waitFor(5, TimeUnit.SECONDS)) inspector.destroyForcibly();
                    }
                    throw locked;
                }
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
    void rootProcessExitCacheCannotBypassTreeCleanup() throws Exception {
        var delayed = new ExitCachedProcess(false);
        Object tree = cleanupTree(delayed);
        var cleanup = tree.getClass().getDeclaredMethod("cleanup");
        cleanup.setAccessible(true);
        assertEquals(false, cleanup.invoke(tree));
        assertFalse(
                delayed.isAlive(), "root Process exit-state cache must settle before clean tree");
        assertTrue(delayed.observations.get() >= 4);
        var residual = tree.getClass().getDeclaredMethod("residual");
        residual.setAccessible(true);
        assertEquals(List.of(), residual.invoke(tree));

        var unreaped = new ExitCachedProcess(true);
        Object uncertain = cleanupTree(unreaped);
        assertTimeoutPreemptively(
                Duration.ofSeconds(4), () -> assertEquals(false, cleanup.invoke(uncertain)));
        assertEquals(
                List.of(42L),
                residual.invoke(uncertain),
                "unsettled root remains explicit residual");
    }

    private static Object cleanupTree(Process process) throws Exception {
        Class<?> tree = Class.forName(LocalProcessRunner.class.getName() + "$Tree");
        var constructor =
                tree.getDeclaredConstructor(
                        Process.class, int.class, java.util.function.Consumer.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                process,
                8,
                (java.util.function.Consumer<ProcessHandle>)
                        handle -> {
                            throw new AssertionError(
                                    "exited fake handle must never terminate an OS process");
                        });
    }

    @Test
    void rootExitCodeCannotBypassTerminationSignal() throws Exception {
        var waiting = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        var process =
                new ExitCachedProcess(false) {
                    public boolean isAlive() {
                        return false;
                    }

                    public int waitFor() throws InterruptedException {
                        waiting.countDown();
                        released.await();
                        return 0;
                    }
                };
        Object tree = cleanupTree(process);
        var cleanup = tree.getClass().getDeclaredMethod("cleanup");
        cleanup.setAccessible(true);
        var result =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return cleanup.invoke(tree);
                            } catch (ReflectiveOperationException failure) {
                                throw new CompletionException(failure);
                            }
                        });
        try {
            assertTrue(
                    waiting.await(1, TimeUnit.SECONDS),
                    "cleanup must wait for the owned termination signal");
            assertFalse(result.isDone(), "an exit code alone cannot confirm cleanup");
        } finally {
            released.countDown();
            result.get(3, TimeUnit.SECONDS);
        }
        var residual = tree.getClass().getDeclaredMethod("residual");
        residual.setAccessible(true);
        assertEquals(List.of(), residual.invoke(tree));
        var stopped = new CountDownLatch(1);
        var neverSignalled =
                new ExitCachedProcess(false) {
                    public boolean isAlive() {
                        return false;
                    }

                    public int waitFor() throws InterruptedException {
                        try {
                            new CountDownLatch(1).await();
                            return 0;
                        } finally {
                            stopped.countDown();
                        }
                    }
                };
        Object uncertain = cleanupTree(neverSignalled);
        assertTimeoutPreemptively(Duration.ofSeconds(4), () -> cleanup.invoke(uncertain));
        assertEquals(
                List.of(42L),
                residual.invoke(uncertain),
                "unconfirmed signal remains residual even with an exit code");
        assertTrue(stopped.await(1, TimeUnit.SECONDS), "deadline must interrupt the owned waiter");
    }

    private static class ExitCachedProcess extends Process {
        final AtomicInteger remaining = new AtomicInteger(3), observations = new AtomicInteger();
        final boolean permanentlyAlive;
        final ProcessHandle handle =
                (ProcessHandle)
                        java.lang.reflect.Proxy.newProxyInstance(
                                ProcessHandle.class.getClassLoader(),
                                new Class<?>[] {ProcessHandle.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "pid" -> 42L;
                                            case "isAlive", "destroy", "destroyForcibly" -> false;
                                            case "children", "descendants" ->
                                                    java.util.stream.Stream.empty();
                                            case "parent" -> Optional.empty();
                                            case "supportsNormalTermination" -> true;
                                            case "hashCode" -> 42;
                                            case "equals" -> proxy == args[0];
                                            case "compareTo" -> 0;
                                            case "toString" -> "exited-fixture-handle";
                                            default -> throw new AssertionError(method.getName());
                                        });

        ExitCachedProcess(boolean permanentlyAlive) {
            this.permanentlyAlive = permanentlyAlive;
        }

        public java.io.OutputStream getOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        public int waitFor() throws InterruptedException {
            return 0;
        }

        public int exitValue() {
            return 0;
        }

        public void destroy() {
            throw new AssertionError("fake root must not terminate an OS process");
        }

        public ProcessHandle toHandle() {
            return handle;
        }

        public boolean isAlive() {
            observations.incrementAndGet();
            return permanentlyAlive || remaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
        }
    }

    @Test
    void concurrentCeilingAndCloseCancelActiveCommand() throws Exception {
        var runner = runner(8, 8192);
        AtomicBoolean started = new AtomicBoolean();
        Path ready = root.resolve("active-command.pid");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> active =
                    executor.submit(
                            () ->
                                    runner.execute(
                                            request(
                                                    ProcessProbe.command("block", ready.toString()),
                                                    Duration.ofSeconds(8)),
                                            () -> {
                                                started.set(true);
                                                return false;
                                            }));
            while (!started.get()) Thread.sleep(5);
            awaitFile(ready);
            assertEquals(
                    "PROCESS_LIMIT",
                    runner.execute(
                                    request(ProcessProbe.command("stdin"), Duration.ofSeconds(5)),
                                    CancellationToken.NONE)
                            .error()
                            .orElseThrow()
                            .code());
            runner.close();
            var settled = active.get(5, TimeUnit.SECONDS);
            assertEquals(ToolStatus.CANCELLED, settled.status(), () -> settled.value().toString());
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
            assertEquals(ToolStatus.UNKNOWN, limited.status());
            assertEquals(Boolean.FALSE, limited.value().get("observedClean"));
            assertEquals("failed", limited.value().get("cleanupStatus"));
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
                assertEquals(
                        Boolean.TRUE.equals(result.value().get("observedClean"))
                                ? "partial"
                                : "failed",
                        result.value().get("cleanupStatus"));
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
        assertTrue(Files.exists(file) && Files.size(file) > 0, "fixture did not publish readiness");
    }
}
