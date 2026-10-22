package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1ProcessSafetyTest {
    @TempDir Path root;

    @Test
    void environmentValidationUsesTheEffectiveSnapshot() throws Exception {
        var reads = new AtomicInteger();
        Map<String, String> changing =
                new AbstractMap<>() {
                    @Override
                    public int size() {
                        return 1;
                    }

                    @Override
                    public Set<Entry<String, String>> entrySet() {
                        return reads.getAndIncrement() == 0
                                ? Map.of("BAD", "nul\0value").entrySet()
                                : Map.of("GOOD", "safe").entrySet();
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalProcessRunner(
                                new LocalWorkspace(root),
                                ProcessLimits.defaults(),
                                changing,
                                List.of()));
    }

    @Test
    void publicTerminalRejectsInvalidEnvironmentBeforeStartingAnything() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalTerminalSession(
                                new LocalWorkspace(root),
                                Map.of("INVALID-NAME", "value"),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalTerminalSession(
                                new LocalWorkspace(root),
                                Map.of("VALID", "nul\0value"),
                                List.of()));
    }

    @Test
    void discoveryOnlyReturnsRegularFilesWithStrictUuidNames() throws Exception {
        Path state = Files.createDirectory(root.resolve("runs"));
        Files.writeString(state.resolve("-".repeat(36) + ".run"), "invalid");
        Files.createDirectory(state.resolve(UUID.randomUUID() + ".run"));
        assertEquals(List.of(), LocalRunControl.discover(state));
    }

    @Test
    void cancelEntrypointRejectsBlankAndInvalidPathsWithTheDocumentedCode() throws Exception {
        var blank =
                new ArrayList<>(
                        List.of(
                                ProcessProbe.java(),
                                "-cp",
                                System.getProperty("java.class.path"),
                                CancelMain.class.getName(),
                                "--state-dir",
                                " ",
                                "--run",
                                UUID.randomUUID().toString()));
        for (var command : List.of(blank, ProcessProbe.command("cancel-invalid-path"))) {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
                String output =
                        new String(
                                process.getInputStream().readNBytes(65536),
                                java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(5, process.exitValue(), output);
                assertFalse(output.contains("Exception"));
                assertFalse(output.contains("private-path"));
            } finally {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void terminalSecretChunksRotateWithStableSanitizedOffsetsAndKeepNormalFinalTail()
            throws Exception {
        try (var terminal =
                new LocalTerminalSession(
                        new LocalWorkspace(root),
                        LocalProcessRunner.buildEnvironment(),
                        List.of("very-private-value"))) {
            var request =
                    new io.jharness.api.CommandRequest(
                            "terminal-log",
                            io.jharness.api.CommandRequest.Mode.ARGV,
                            ProcessProbe.command("terminal-log"),
                            new io.jharness.api.ExecPath("."),
                            java.time.Duration.ofSeconds(10));
            String id =
                    (String)
                            terminal.start(request, false, io.jharness.api.CancellationToken.NONE)
                                    .get("id");
            awaitTerminal(terminal, id);
            long tailDeadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
            while (true) {
                var end = terminal.log(id, Long.MAX_VALUE, 1);
                long cursor = ((Number) end.get("nextOffset")).longValue();
                long beginning = ((Number) end.get("firstOffset")).longValue();
                if (((String) terminal.log(id, Math.max(beginning, cursor - 100), 100).get("text"))
                        .endsWith("ordinary final tail")) break;
                assertTrue(System.nanoTime() < tailDeadline, "reader must publish the final tail");
                Thread.sleep(10);
            }
            var first = terminal.log(id, Long.MAX_VALUE, 65536);
            long firstOffset = ((Number) first.get("firstOffset")).longValue();
            assertTrue(firstOffset > 0);
            long offset = firstOffset;
            var text = new StringBuilder();
            while (true) {
                var page = terminal.log(id, offset, 65536);
                String part = (String) page.get("text");
                long next = ((Number) page.get("nextOffset")).longValue();
                assertEquals(offset + part.length(), next);
                text.append(part);
                if (next == offset) break;
                offset = next;
            }
            assertFalse(text.toString().contains("very-private-value"));
            assertFalse(text.toString().contains("very-pr"));
            assertTrue(text.toString().endsWith("ordinary final tail"));
            assertTrue(text.toString().contains("[REDACTED]"));
            assertThrows(io.jharness.api.HarnessException.class, () -> terminal.log(id, 0, 1));
        }
    }

    @Test
    void terminalSuppressesKnownSecretPrefixAtEofWithoutDroppingOrdinaryTail() throws Exception {
        Path artifact = root.resolve("terminal-artifact.txt");
        var artifacts =
                new io.jharness.api.ArtifactStore() {
                    public Artifact put(String session, String content) throws java.io.IOException {
                        Files.writeString(artifact, content);
                        return new Artifact("terminal", content.length(), "fixture-hash");
                    }

                    public String read(String session, String id, int offset, int limit) {
                        throw new AssertionError();
                    }
                };
        try (var terminal =
                new LocalTerminalSession(
                        new LocalWorkspace(root),
                        LocalProcessRunner.buildEnvironment(),
                        List.of("very-private-value"),
                        artifacts,
                        "terminal")) {
            var request =
                    new io.jharness.api.CommandRequest(
                            "prefix",
                            io.jharness.api.CommandRequest.Mode.ARGV,
                            ProcessProbe.command("terminal-prefix"),
                            new io.jharness.api.ExecPath("."),
                            java.time.Duration.ofSeconds(5));
            String id =
                    (String)
                            terminal.start(request, false, io.jharness.api.CancellationToken.NONE)
                                    .get("id");
            awaitTerminal(terminal, id);
            long until = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
            String text;
            do {
                text = (String) terminal.log(id, 0, 65536).get("text");
                if (text.startsWith("ordinary terminal tail")) break;
                Thread.sleep(10);
            } while (System.nanoTime() < until);
            assertEquals("ordinary terminal tail\n", text);
            while (!Files.exists(artifact) && System.nanoTime() < until) Thread.sleep(10);
            assertTrue(Files.exists(artifact));
            assertEquals("ordinary terminal tail\n", Files.readString(artifact));
        }
    }

    @Test
    void aSlowPartialControlFrameCannotStarveAValidCancellation() throws Exception {
        try (var run = new LocalRunControl(root.resolve("runs"))) {
            var properties = new java.util.Properties();
            try (var input = Files.newInputStream(run.stateFile())) {
                properties.load(input);
            }
            try (var slow =
                    new java.net.Socket(
                            "127.0.0.1", Integer.parseInt(properties.getProperty("port")))) {
                slow.getOutputStream()
                        .write(0); // incomplete UTF frame header, absolute deadline still applies
                long before = System.nanoTime();
                assertEquals(
                        LocalRunControl.CancelResult.REQUESTED,
                        LocalRunControl.cancel(root.resolve("runs"), run.runId()));
                assertTrue(System.nanoTime() - before < java.time.Duration.ofSeconds(3).toNanos());
                assertTrue(run.cancellation().isCancelled());
            }
        }
    }

    @Test
    void interruptedCloseStillWritesExitedStateAndRestoresTheInterrupt() throws Exception {
        var run = new LocalRunControl(root.resolve("runs"));
        try {
            Thread.currentThread().interrupt();
            run.close();
            assertTrue(Thread.interrupted());
            assertTrue(Files.readString(run.stateFile()).contains("status=EXITED"));
            run.close();
        } finally {
            Thread.interrupted();
            run.close();
        }
    }

    @Test
    void cancelledFrameWithLostAcknowledgementHasUnknownDeliveryOutcome() throws Exception {
        Path directory = Files.createDirectory(root.resolve("lost-ack"));
        String id = UUID.randomUUID().toString();
        String token = "a".repeat(64);
        try (var server =
                new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3000);
            Files.writeString(
                    directory.resolve(id + ".run"),
                    "version=1\nrunId="
                            + id
                            + "\npid="
                            + ProcessHandle.current().pid()
                            + "\nprocessStart="
                            + ProcessHandle.current().info().startInstant().orElseThrow()
                            + "\nhost=127.0.0.1\nport="
                            + server.getLocalPort()
                            + "\nstatus=RUNNING\ntoken="
                            + token
                            + "\n");
            var received = new java.util.concurrent.CompletableFuture<String>();
            var listener =
                    Thread.ofVirtual()
                            .start(
                                    () -> {
                                        try (var socket = server.accept()) {
                                            received.complete(
                                                    new java.io.DataInputStream(
                                                                    socket.getInputStream())
                                                            .readUTF());
                                            // The authenticated frame arrived, but no
                                            // acknowledgement is delivered.
                                        } catch (Throwable failure) {
                                            received.completeExceptionally(failure);
                                        }
                                    });
            assertEquals(
                    LocalRunControl.CancelResult.OUTCOME_UNKNOWN,
                    LocalRunControl.cancel(directory, id));
            assertEquals("CANCEL " + token, received.get(3, java.util.concurrent.TimeUnit.SECONDS));
            listener.join(1000);
            assertFalse(listener.isAlive());
        }
    }

    @Test
    void failedExitedCheckpointCanBeRetriedWithoutReopeningTheListener() throws Exception {
        var run = new LocalRunControl(root.resolve("retry-exit"));
        try {
            Files.writeString(run.stateFile().resolveSibling(run.runId() + ".tmp"), "collision");
            assertThrows(java.io.IOException.class, run::close);
            assertTrue(Files.readString(run.stateFile()).contains("status=RUNNING"));
            assertTrue(run.cancellation().isCancelled());
            run.close();
            assertTrue(Files.readString(run.stateFile()).contains("status=EXITED"));
            assertEquals(
                    LocalRunControl.CancelResult.ALREADY_EXITED,
                    LocalRunControl.cancel(root.resolve("retry-exit"), run.runId()));
        } finally {
            run.close();
        }
    }

    @Test
    void staleProcessIdentityIsRejectedBeforeSendingCancellation() throws Exception {
        try (var run = new LocalRunControl(root.resolve("stale"))) {
            String state = Files.readString(run.stateFile());
            Files.writeString(
                    run.stateFile(),
                    state.replaceAll("processStart=[^\\n]+", "processStart=1970-01-01T00:00:00Z"));
            assertEquals(
                    LocalRunControl.CancelResult.INVALID_STATE,
                    LocalRunControl.cancel(root.resolve("stale"), run.runId()));
            assertFalse(run.cancellation().isCancelled());
        }
    }

    @Test
    void runnerCloseTakesOwnershipOfAnInFlightNativeProcessAndRejectsNewWork() throws Exception {
        var runner =
                new LocalProcessRunner(
                        new LocalWorkspace(root),
                        ProcessLimits.defaults(),
                        LocalProcessRunner.buildEnvironment(),
                        List.of());
        Path pid = root.resolve("running.pid");
        var future =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () ->
                                runner.execute(
                                        new io.jharness.api.CommandRequest(
                                                "active",
                                                io.jharness.api.CommandRequest.Mode.ARGV,
                                                ProcessProbe.command("block", pid.toString()),
                                                new io.jharness.api.ExecPath("."),
                                                java.time.Duration.ofSeconds(10)),
                                        io.jharness.api.CancellationToken.NONE));
        try {
            long until = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
            while (!Files.exists(pid) && System.nanoTime() < until) Thread.sleep(10);
            assertTrue(Files.exists(pid));
            runner.close();
            var result = future.get(4, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, result.value().get("observedClean"));
            assertNotEquals(io.jharness.api.ToolStatus.SUCCESS, result.status());
            var denied =
                    runner.execute(
                            ProcessProbe.command("echo", "never", "0"),
                            new io.jharness.api.ExecPath("."),
                            io.jharness.api.CancellationToken.NONE);
            assertEquals("PROCESS_SERVICE_CLOSED", denied.error().orElseThrow().code());
            assertEquals(Boolean.FALSE, denied.value().get("sideEffectUncertain"));
        } finally {
            runner.close();
        }
    }

    @Test
    void artifactFailureRetainsTheCommandStatusAndExitEvidence() throws Exception {
        var scope = new io.jharness.api.PluginScope("s", "agent");
        io.jharness.api.ToolResult executed =
                new io.jharness.api.ToolResult(
                        "command",
                        io.jharness.api.ToolStatus.SUCCESS,
                        Map.of(
                                "stdout",
                                "x".repeat(2048),
                                "exitCode",
                                0,
                                "sideEffectUncertain",
                                false,
                                "observedClean",
                                true),
                        "done",
                        Map.of(),
                        Optional.empty());
        io.jharness.api.ProcessRunner runner =
                new io.jharness.api.ProcessRunner() {
                    public io.jharness.api.ToolResult execute(
                            io.jharness.api.CommandRequest request,
                            io.jharness.api.CancellationToken token) {
                        return executed;
                    }

                    public io.jharness.api.ToolResult execute(
                            List<String> argv,
                            io.jharness.api.ExecPath cwd,
                            io.jharness.api.CancellationToken token) {
                        throw new AssertionError("bounded request required");
                    }
                };
        var artifacts =
                new io.jharness.api.ArtifactStore() {
                    public Artifact put(String session, String content) throws java.io.IOException {
                        throw new java.io.IOException("fixture disk failure");
                    }

                    public String read(String session, String id, int offset, int limit) {
                        throw new AssertionError();
                    }
                };
        var result =
                new ProcessToolsPlugin.CommandTool(runner, scope, artifacts)
                        .execute(
                                new io.jharness.api.ToolInvocation(
                                        "command",
                                        "execute_command",
                                        Map.of("argv", List.of("fixture")),
                                        io.jharness.api.SideEffectLevel.SYSTEM_IMPACT),
                                new io.jharness.api.ToolContext(
                                        scope, io.jharness.api.CancellationToken.NONE));
        assertEquals(io.jharness.api.ToolStatus.SUCCESS, result.status());
        assertEquals(0, result.value().get("exitCode"));
        assertEquals("FAILED", result.value().get("stdoutArtifactStatus"));
        assertEquals("x".repeat(2048), result.value().get("stdout"));
        assertEquals(Boolean.FALSE, result.value().get("sideEffectUncertain"));
    }

    @Test
    void controlledRunHasOneOwnerAndCloseRequestsCancellationBeforeEndpointExit() throws Exception {
        var control = new LocalRunControl(root.resolve("controlled"));
        var run = new ControlledAgentRun(control);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var records = new java.util.concurrent.CopyOnWriteArrayList<io.jharness.api.SessionEvent>();
        io.jharness.api.SessionWriter writer =
                new io.jharness.api.SessionWriter() {
                    public boolean durable() {
                        return true;
                    }

                    public String append(io.jharness.api.SessionEvent event) {
                        try {
                            Files.writeString(
                                    root.resolve("control-events.txt"),
                                    event.toString() + "\n",
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND);
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                        records.add(event);
                        return event.eventId();
                    }

                    public void flush() {
                        try (var channel =
                                java.nio.channels.FileChannel.open(
                                        root.resolve("control-events.txt"),
                                        StandardOpenOption.WRITE)) {
                            channel.force(true);
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    }

                    public void close() {}
                };
        var scope = new io.jharness.api.PluginScope("controlled", "agent");
        var bus =
                (io.jharness.api.EventBus)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {io.jharness.api.EventBus.class},
                                (proxy, method, arguments) -> null);
        var running =
                java.util.concurrent.CompletableFuture.runAsync(
                        () ->
                                run.run(
                                        token -> {
                                            entered.countDown();
                                            try {
                                                assertTrue(
                                                        release.await(
                                                                3,
                                                                java.util.concurrent.TimeUnit
                                                                        .SECONDS));
                                            } catch (InterruptedException ex) {
                                                throw new AssertionError(ex);
                                            }
                                            assertTrue(token.isCancelled());
                                        },
                                        writer,
                                        bus,
                                        scope,
                                        io.jharness.api.CancellationToken.NONE));
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    "RUN_ALREADY_STARTED",
                    assertThrows(
                                    io.jharness.api.HarnessException.class,
                                    () ->
                                            run.run(
                                                    token -> fail("second loop"),
                                                    writer,
                                                    bus,
                                                    scope,
                                                    io.jharness.api.CancellationToken.NONE))
                            .code());
            run.close();
            assertTrue(control.cancellation().isCancelled());
            assertTrue(Files.readString(control.stateFile()).contains("status=RUNNING"));
            release.countDown();
            running.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(Files.readString(control.stateFile()).contains("status=EXITED"));
            assertEquals(
                    1,
                    records.stream().filter(event -> event.type().equals("run.started")).count());
            assertEquals(
                    1, records.stream().filter(event -> event.type().equals("run.ended")).count());
            assertEquals(
                    "RUN_CLOSED",
                    assertThrows(
                                    io.jharness.api.HarnessException.class,
                                    () ->
                                            run.run(
                                                    token -> fail("closed loop"),
                                                    writer,
                                                    bus,
                                                    scope,
                                                    io.jharness.api.CancellationToken.NONE))
                            .code());
        } finally {
            release.countDown();
            run.close();
        }
    }

    @Test
    void closedControlledRunRejectsItsFirstSubmission() throws Exception {
        try (var run = new ControlledAgentRun(new LocalRunControl(root.resolve("closed")))) {
            closeControlled(run);
            assertEquals(
                    "RUN_CLOSED",
                    assertThrows(
                                    io.jharness.api.HarnessException.class,
                                    () ->
                                            run.run(
                                                    token -> fail("closed loop"),
                                                    null,
                                                    null,
                                                    null,
                                                    io.jharness.api.CancellationToken.NONE))
                            .code());
        }
    }

    @Test
    void controlledRunRetainsExecutionFailureWhenEndedEventAndStateCloseBothFail()
            throws Exception {
        var control = new LocalRunControl(root.resolve("double-failure"));
        var run = new ControlledAgentRun(control);
        var execution = new IllegalStateException("execution failed");
        var ended = new IllegalStateException("ended event failed");
        io.jharness.api.SessionWriter writer =
                new io.jharness.api.SessionWriter() {
                    public boolean durable() {
                        return true;
                    }

                    public String append(io.jharness.api.SessionEvent event) {
                        if (event.type().equals("run.ended")) throw ended;
                        try {
                            Files.writeString(root.resolve("started.txt"), event.toString());
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                        return event.eventId();
                    }

                    public void flush() {
                        try (var channel =
                                java.nio.channels.FileChannel.open(
                                        root.resolve("started.txt"), StandardOpenOption.WRITE)) {
                            channel.force(true);
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    }

                    public void close() {}
                };
        var bus =
                (io.jharness.api.EventBus)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {io.jharness.api.EventBus.class},
                                (proxy, method, arguments) -> null);
        try {
            Files.writeString(
                    control.stateFile().resolveSibling(control.runId() + ".tmp"), "collision");
            var actual =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    run.run(
                                            token -> {
                                                throw execution;
                                            },
                                            writer,
                                            bus,
                                            new io.jharness.api.PluginScope("s", "a"),
                                            io.jharness.api.CancellationToken.NONE));
            assertSame(execution, actual);
            assertEquals(2, actual.getSuppressed().length);
            assertSame(ended, actual.getSuppressed()[0]);
            assertEquals(
                    "RUN_STATE_IO",
                    assertInstanceOf(
                                    io.jharness.api.HarnessException.class,
                                    actual.getSuppressed()[1])
                            .code());
            assertTrue(control.cancellation().isCancelled());
            assertTrue(Files.readString(control.stateFile()).contains("status=RUNNING"));
            run.close();
            assertTrue(Files.readString(control.stateFile()).contains("status=EXITED"));
        } finally {
            run.close();
        }
    }

    private static void closeControlled(ControlledAgentRun run) throws java.io.IOException {
        run.close();
    }

    private static void awaitTerminal(LocalTerminalSession terminal, String id) throws Exception {
        long until = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while ("RUNNING".equals(terminal.status(id).get("state")) && System.nanoTime() < until)
            Thread.sleep(10);
        assertEquals("EXITED", terminal.status(id).get("state"));
    }
}
