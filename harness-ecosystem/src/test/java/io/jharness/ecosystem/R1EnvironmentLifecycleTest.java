package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1EnvironmentLifecycleTest {
    @TempDir Path root;

    @Test
    void mcpAbortFailureCannotReplaceTheUnknownExecutionCode() {
        HarnessException failure =
                assertThrows(
                        HarnessException.class,
                        () ->
                                McpIo.run(
                                        () -> {
                                            Thread.sleep(10000);
                                            return null;
                                        },
                                        Duration.ofMillis(30),
                                        CancellationToken.NONE,
                                        () -> {
                                            throw new IllegalStateException("abort failed");
                                        }));
        assertEquals("MCP_OUTCOME_UNKNOWN", failure.code());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void sdkConfigurationNeverPrintsPromptOrParameterValues() {
        var config =
                new SdkConfiguration(
                        "private-root-instruction",
                        Set.of("read_file"),
                        Map.of("apiKey", "synthetic-private-value"));
        assertFalse(config.toString().contains("private-root-instruction"));
        assertFalse(config.toString().contains("synthetic-private-value"));
    }

    @Test
    void closedLocalEnvironmentRevokesAlreadyBorrowedFiles() throws Exception {
        var environment = new LocalExecutionEnvironment("test", root, List.of());
        var files = environment.files();
        environment.close();
        HarnessException failure =
                assertThrows(
                        HarnessException.class,
                        () ->
                                files.write(
                                        new ExecPath("after-close.txt"),
                                        "must not write",
                                        false,
                                        null,
                                        CancellationToken.NONE));
        assertEquals("ENVIRONMENT_CLOSED", failure.code());
        assertFalse(Files.exists(root.resolve("after-close.txt")));
    }

    @Test
    void childRevocationReachesAnAlreadyRunningBorrowedReadToken() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var workspace =
                (WorkspaceFiles)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {WorkspaceFiles.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("isolatedView")) return proxy;
                                    if (method.getName().equals("readPage")) {
                                        CancellationToken token = (CancellationToken) arguments[3];
                                        entered.countDown();
                                        long deadline =
                                                System.nanoTime() + Duration.ofSeconds(3).toNanos();
                                        while (!token.isCancelled() && System.nanoTime() < deadline)
                                            Thread.sleep(10);
                                        token.throwIfCancelled();
                                        throw new AssertionError("revocation did not reach read");
                                    }
                                    throw new AssertionError(method.getName());
                                });
        var parent =
                new ExecutionEnvironment() {
                    public String id() {
                        return "parent";
                    }

                    public WorkspaceFiles files() {
                        return workspace;
                    }

                    public ProcessRunner processes() {
                        throw new AssertionError();
                    }

                    public TerminalSession terminal() {
                        throw new AssertionError();
                    }

                    public Map<String, Object> descriptor() {
                        return Map.of();
                    }

                    public void close() {
                        throw new AssertionError("borrower must not close parent");
                    }
                };
        var child = new ReadOnlyChildEnvironment(parent, "child", () -> false);
        var outcome = new java.util.concurrent.CompletableFuture<Throwable>();
        var worker =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        child.files()
                                                .readPage(
                                                        new ExecPath("a"),
                                                        1,
                                                        10,
                                                        CancellationToken.NONE);
                                        outcome.complete(null);
                                    } catch (Throwable failure) {
                                        outcome.complete(failure);
                                    }
                                });
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            child.close();
            var failure =
                    assertInstanceOf(
                            HarnessException.class,
                            outcome.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("CANCELLED", failure.code());
            worker.join(1000);
            assertFalse(worker.isAlive());
        } finally {
            child.close();
        }
    }

    @Test
    void dockerRemovalCanBeRetriedWithoutRestoringCapabilities() throws Exception {
        var removals = new java.util.concurrent.atomic.AtomicInteger();
        CommandTransport transport =
                (command, input, timeout, token) -> {
                    if (command.get(1).equals("rm"))
                        return reply(removals.incrementAndGet() == 1 ? 1 : 0, false);
                    return reply(0, false);
                };
        var environment = docker(transport);
        assertEquals(
                "CONTAINER_CLEANUP_UNKNOWN",
                assertThrows(HarnessException.class, environment::close).code());
        assertEquals(
                "ENVIRONMENT_CLOSED",
                assertThrows(
                                HarnessException.class,
                                () -> environment.files().readText(new ExecPath("a")))
                        .code());
        environment.close();
        environment.close();
        assertEquals(2, removals.get());
    }

    @Test
    void failedContainerStartRetainsFailedCompensation() throws Exception {
        var failure =
                assertThrows(
                        HarnessException.class,
                        () -> docker((command, input, timeout, token) -> reply(1, false)));
        assertEquals("CONTAINER_START_FAILED", failure.code());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(
                "CONTAINER_CLEANUP_UNKNOWN",
                ((HarnessException) failure.getSuppressed()[0]).code());
    }

    @Test
    void nonzeroMutationAndFailedRemovalKeepBothUnknownFacts() throws Exception {
        var environment =
                docker(
                        (command, input, timeout, token) ->
                                reply(command.get(1).equals("run") ? 0 : 1, false));
        var failure =
                assertThrows(
                        HarnessException.class,
                        () ->
                                environment
                                        .files()
                                        .write(
                                                new ExecPath("a"),
                                                "content",
                                                false,
                                                null,
                                                CancellationToken.NONE));
        assertEquals("ENVIRONMENT_OPERATION_UNKNOWN", failure.code());
        assertEquals(
                "CONTAINER_CLEANUP_UNKNOWN",
                ((HarnessException) failure.getSuppressed()[0]).code());
    }

    @Test
    void uncertainCommandNeverReportsRemovedWhenRemovalFailed() throws Exception {
        var environment =
                docker(
                        (command, input, timeout, token) ->
                                reply(
                                        command.get(1).equals("run") ? 0 : -1,
                                        command.get(1).equals("exec")));
        var result =
                environment
                        .processes()
                        .execute(List.of("task"), new ExecPath("."), CancellationToken.NONE);
        assertEquals(ToolStatus.UNKNOWN, result.status());
        assertEquals("container_cleanup_unknown", result.value().get("cleanupStatus"));
    }

    @Test
    void workerUncertaintyNormalizesPublicStatusButKeepsExitEvidence() throws Exception {
        var environment =
                docker(
                        (command, input, timeout, token) -> {
                            if (!command.get(1).equals("exec")) return reply(0, false);
                            return new CommandTransport.Reply(
                                    0,
                                    BoundedJson.encode(
                                            Map.of(
                                                    "value",
                                                    Map.of(
                                                            "status",
                                                            "SUCCESS",
                                                            "modelContent",
                                                            "done",
                                                            "display",
                                                            Map.of(),
                                                            "value",
                                                            Map.of(
                                                                    "sideEffectUncertain",
                                                                    true,
                                                                    "exitCode",
                                                                    0)))),
                                    new byte[0],
                                    true,
                                    false);
                        });
        var result =
                environment
                        .processes()
                        .execute(List.of("task"), new ExecPath("."), CancellationToken.NONE);
        assertEquals(ToolStatus.UNKNOWN, result.status());
        assertEquals(0, result.value().get("exitCode"));
        assertEquals("container_removed", result.value().get("containerCleanup"));
    }

    @Test
    void malformedWorkerCommandResponseRemainsUnknownAfterSuccessfulControlExit() throws Exception {
        var executions = new java.util.concurrent.atomic.AtomicInteger();
        var environment =
                docker(
                        (command, input, timeout, token) -> {
                            if (!command.get(1).equals("exec")) return reply(0, false);
                            executions.incrementAndGet();
                            return new CommandTransport.Reply(
                                    0,
                                    "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    new byte[0],
                                    true,
                                    false);
                        });
        var result =
                environment
                        .processes()
                        .execute(List.of("task"), new ExecPath("."), CancellationToken.NONE);
        assertEquals(ToolStatus.UNKNOWN, result.status());
        assertEquals(Boolean.TRUE, result.value().get("sideEffectUncertain"));
        assertEquals(0, result.value().get("controlExitCode"));
        assertEquals("container_removed", result.value().get("cleanupStatus"));
        assertEquals(1, executions.get());
    }

    @Test
    void fatalMcpFailureCompletesImmediatelyAndStillAborts() {
        var aborts = new java.util.concurrent.atomic.AtomicInteger();
        var failure = new AssertionError("fatal fixture");
        assertSame(
                failure,
                assertThrows(
                        AssertionError.class,
                        () ->
                                McpIo.run(
                                        () -> {
                                            throw failure;
                                        },
                                        Duration.ofSeconds(5),
                                        CancellationToken.NONE,
                                        aborts::incrementAndGet)));
        assertEquals(1, aborts.get());
    }

    @Test
    void httpCloseAbortsTheInFlightBodyWithoutWaitingForExchangeMonitor() throws Exception {
        var requestStarted = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/mcp",
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().flush();
                    requestStarted.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.close();
                });
        server.start();
        var transport =
                new HttpMcpTransport(
                        java.net.URI.create(
                                "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"),
                        Duration.ofSeconds(30),
                        () -> null);
        var outcome = new java.util.concurrent.CompletableFuture<Throwable>();
        var worker =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        transport.exchange(
                                                Map.of(
                                                        "jsonrpc",
                                                        "2.0",
                                                        "id",
                                                        1,
                                                        "method",
                                                        "tools/list"),
                                                CancellationToken.NONE);
                                        outcome.complete(null);
                                    } catch (Throwable ex) {
                                        outcome.complete(ex);
                                    }
                                });
        try {
            assertTrue(requestStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            long before = System.nanoTime();
            transport.close();
            assertTrue(System.nanoTime() - before < Duration.ofSeconds(2).toNanos());
            assertNotNull(outcome.get(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.join(1000);
            assertFalse(worker.isAlive());
        } finally {
            release.countDown();
            transport.close();
            server.stop(0);
        }
    }

    @Test
    void skillScanDeadlineIsDistinctFromUserCancellation() throws Exception {
        var roots = List.of(new SkillCatalog.Root("test", root));
        assertEquals(
                "SKILL_SCAN_TIMEOUT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SkillCatalog.scan(
                                                roots,
                                                Set.of(),
                                                CancellationToken.NONE,
                                                Duration.ZERO))
                        .code());
        assertEquals(
                "CANCELLED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SkillCatalog.scan(
                                                roots, Set.of(), () -> true, Duration.ofSeconds(1)))
                        .code());
    }

    @Test
    void planningStopRevokesBeforeBlockedPreparationCanSaveAndWaitsForOwnerExit() throws Exception {
        var scope = new PluginScope("planning", "agent");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new io.jharness.plugin.DefaultServiceRegistry();
        var tools = new io.jharness.plugin.ScopedToolRegistry();
        registry.register(ToolRegistry.class, "default", tools, scope);
        registry.register(
                SessionReader.class,
                "default",
                () -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                    return List.of();
                },
                scope);
        registry.register(
                SessionWriter.class,
                "default",
                new SessionWriter() {
                    public String append(SessionEvent event) {
                        writes.incrementAndGet();
                        return event.eventId();
                    }

                    public void flush() {}

                    public void close() {}
                },
                scope);
        var plugin = new PlanningPlugin();
        plugin.start(
                new PluginContext() {
                    public <T> Registration register(Class<T> type, String key, T instance) {
                        return registry.register(type, key, instance, scope);
                    }

                    public ServiceRegistry services() {
                        return registry;
                    }

                    public EventBus events() {
                        return new io.jharness.events.DefaultEventBus();
                    }

                    public ConfigView config() {
                        return ConfigView.empty();
                    }

                    public PluginScope scope() {
                        return scope;
                    }

                    public Registration own(AutoCloseable resource) {
                        return () -> {};
                    }
                });
        var running = new java.util.concurrent.CompletableFuture<Throwable>();
        var owner =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        tools.require("plan_update", scope)
                                                .execute(
                                                        new ToolInvocation(
                                                                "plan",
                                                                "plan_update",
                                                                Map.of(
                                                                        "revision",
                                                                        0,
                                                                        "steps",
                                                                        List.of("inspect")),
                                                                SideEffectLevel.SYSTEM_IMPACT),
                                                        new ToolContext(
                                                                scope, CancellationToken.NONE));
                                        running.complete(null);
                                    } catch (Throwable ex) {
                                        running.complete(ex);
                                    }
                                });
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        var stopping = java.util.concurrent.CompletableFuture.runAsync(plugin::stop);
        long until = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        // Stop must revoke before it waits for the blocked preparation owner.
        var activeField = PlanningPlugin.class.getDeclaredField("active");
        activeField.setAccessible(true);
        while (activeField.getBoolean(plugin) && System.nanoTime() < until) Thread.onSpinWait();
        assertFalse(activeField.getBoolean(plugin));
        release.countDown();
        assertInstanceOf(
                HarnessException.class, running.get(3, java.util.concurrent.TimeUnit.SECONDS));
        stopping.get(3, java.util.concurrent.TimeUnit.SECONDS);
        owner.join(1000);
        assertEquals(0, writes.get());
    }

    @Test
    void nativeTransportReturnsOnlyAfterCancelledChildExitAndPreservesUnknownDelivery()
            throws Exception {
        Path pid = root.resolve("native.pid");
        var command =
                List.of(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        NativeProbe.class.getName(),
                        pid.toString());
        var reply =
                new NativeCommandTransport()
                        .call(
                                command,
                                new byte[0],
                                Duration.ofSeconds(10),
                                () -> Files.exists(pid));
        assertTrue(reply.started());
        assertTrue(reply.uncertain());
        assertTrue(Files.exists(pid));
        long child = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
    }

    public static class NativeProbe {
        public static void main(String[] arguments) throws Exception {
            Files.writeString(Path.of(arguments[0]), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(30000);
        }
    }

    private DockerExecutionEnvironment docker(CommandTransport transport) throws Exception {
        Path jar = root.resolve("worker.jar");
        Files.writeString(jar, "synthetic worker");
        return new DockerExecutionEnvironment(
                "docker", "fixture@sha256:" + "a".repeat(64), root, jar, transport);
    }

    private static CommandTransport.Reply reply(int code, boolean uncertain) {
        return new CommandTransport.Reply(code, new byte[0], new byte[0], true, uncertain);
    }
}
