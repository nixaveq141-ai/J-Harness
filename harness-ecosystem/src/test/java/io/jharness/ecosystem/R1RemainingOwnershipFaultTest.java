package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.process.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1RemainingOwnershipFaultTest {
    @TempDir Path root;

    @Test
    void environmentDoubleCloseFailureRevokesFilesAndPreservesBothCauses() throws Exception {
        var environment = new LocalExecutionEnvironment("double-fault", root, List.of());
        var primary = new AssertionError("terminal close fixture");
        var secondary = new IllegalStateException("process close fixture");
        ((LocalTerminalSession) environment.terminal())
                .onClose(
                        ignored -> {
                            throw primary;
                        });
        var closes = new AtomicInteger();
        var process =
                new OwnedProcess(0, false) {
                    @Override
                    public ProcessHandle toHandle() {
                        closes.incrementAndGet();
                        throw secondary;
                    }
                };
        var field = LocalProcessRunner.class.getDeclaredField("active");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var active = (Set<Process>) field.get(environment.processes());
        active.add(process);
        var files = environment.files();
        try {
            assertSame(primary, assertThrows(AssertionError.class, environment::close));
            assertArrayEquals(new Throwable[] {secondary}, primary.getSuppressed());
            assertEquals(1, closes.get(), "process close must run after terminal failure");
            assertEquals(
                    "ENVIRONMENT_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            files.write(
                                                    new ExecPath("revoked.txt"),
                                                    "denied",
                                                    false,
                                                    null,
                                                    CancellationToken.NONE))
                            .code());
            assertFalse(Files.exists(root.resolve("revoked.txt")));
            environment.close();
            assertEquals(1, closes.get(), "second close must not repeat either failing cleanup");
        } finally {
            active.remove(process);
        }
    }

    @Test
    void stdioCleanupWaitsForRootProcessExitState() {
        var observations = new AtomicInteger();
        var delayed =
                new OwnedProcess(0, false) {
                    @Override
                    public boolean isAlive() {
                        return alive.get() || observations.getAndIncrement() < 3;
                    }
                };
        assertTrue(ProcessCleanup.stop(delayed));
        assertFalse(delayed.isAlive(), "handle exit must not bypass the root Process reaper");
        assertTrue(observations.get() >= 4);
        assertEquals(3, delayed.streamCloses.get());

        var unreaped =
                new OwnedProcess(0, false) {
                    @Override
                    public boolean isAlive() {
                        return true;
                    }
                };
        assertFalse(ProcessCleanup.stop(unreaped), "a live root remains uncertain at the deadline");
        assertEquals(3, unreaped.streamCloses.get());
    }

    @Test
    void postStartInitializationErrorReapsActualOwnedChildAndRethrowsSameError() throws Exception {
        var child =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                System.getProperty("java.class.path"),
                                SleepingChild.class.getName())
                        .start();
        var fatal = new AssertionError("synthetic stream allocation boundary");
        var first = new AtomicBoolean(true);
        var closed = new AtomicInteger();
        var wrapped =
                new Process() {
                    public OutputStream getOutputStream() {
                        return new FilterOutputStream(child.getOutputStream()) {
                            public void close() throws IOException {
                                closed.incrementAndGet();
                                super.close();
                            }
                        };
                    }

                    public InputStream getInputStream() {
                        if (first.getAndSet(false)) throw fatal;
                        return new FilterInputStream(child.getInputStream()) {
                            public void close() throws IOException {
                                closed.incrementAndGet();
                                super.close();
                            }
                        };
                    }

                    public InputStream getErrorStream() {
                        return new FilterInputStream(child.getErrorStream()) {
                            public void close() throws IOException {
                                closed.incrementAndGet();
                                super.close();
                            }
                        };
                    }

                    public int waitFor() throws InterruptedException {
                        return child.waitFor();
                    }

                    public int exitValue() {
                        return child.exitValue();
                    }

                    public void destroy() {
                        child.destroy();
                    }

                    public ProcessHandle toHandle() {
                        return child.toHandle();
                    }

                    public Stream<ProcessHandle> descendants() {
                        return child.descendants();
                    }
                };
        try {
            assertSame(
                    fatal,
                    assertThrows(
                            AssertionError.class,
                            () -> new StdioMcpTransport(wrapped, Duration.ofSeconds(2))));
            assertFalse(child.isAlive(), "constructor must finish compensation before rethrow");
            assertEquals(3, closed.get());
            assertEquals(0, fatal.getSuppressed().length);
        } finally {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void postStartFatalFailureKeepsCleanupUncertaintyAsSuppressed() {
        var process = new OwnedProcess(0, true);
        var fatal = new AssertionError("synthetic allocation rejection");
        process.initializationFailure = fatal;
        assertSame(
                fatal,
                assertThrows(
                        AssertionError.class,
                        () -> new StdioMcpTransport(process, Duration.ofSeconds(2))));
        assertEquals(
                "MCP_CLEANUP_UNKNOWN",
                assertInstanceOf(HarnessException.class, fatal.getSuppressed()[0]).code());
        assertFalse(process.alive.get());
        assertEquals(3, process.streamCloses.get(), "one stream failure cannot skip other closes");
    }

    @Test
    void stdioCloseReportsSamplingOverflowAndStopsEveryRetainedHandle() throws Exception {
        var process = new OwnedProcess(65, false);
        var transport = new StdioMcpTransport(process, Duration.ofSeconds(2));
        assertEquals(
                "MCP_CLEANUP_UNKNOWN",
                assertThrows(HarnessException.class, transport::close).code());
        assertFalse(process.alive.get());
        assertEquals(64, process.children.stream().filter(value -> !value.get()).count());
        assertTrue(process.children.get(64).get(), "unretained child must not be reported killed");
        assertEquals(3, process.streamCloses.get());
        assertEquals(
                "MCP_CLEANUP_UNKNOWN",
                assertThrows(HarnessException.class, transport::close).code());
        assertEquals(3, process.streamCloses.get());
    }

    @Test
    void webResponseReceivedBeforeCancellationAndNeverHandedOffIsClosed() throws Exception {
        webCancellationHandoff(true);
    }

    @Test
    void webResponseArrivingAfterFailedCancellationIsClosedByLateCallback() throws Exception {
        webCancellationHandoff(false);
    }

    private void webCancellationHandoff(boolean beforeCancellation) throws Exception {
        var scope = new PluginScope("web-fault", "agent");
        var services = new io.jharness.plugin.DefaultServiceRegistry();
        var tools = new io.jharness.plugin.ScopedToolRegistry();
        services.register(ToolRegistry.class, "default", tools, scope);
        var owned = new ArrayList<AutoCloseable>();
        var plugin = new WebFetchPlugin(Map.of("fixture", URI.create("http://127.0.0.1/fixed")));
        plugin.start(
                new PluginContext() {
                    public <T> Registration register(Class<T> type, String key, T instance) {
                        return services.register(type, key, instance, scope);
                    }

                    public ServiceRegistry services() {
                        return services;
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
                        owned.add(resource);
                        return () -> {};
                    }
                });
        var bodyCloses = new AtomicInteger();
        InputStream body =
                new ByteArrayInputStream(new byte[0]) {
                    public void close() {
                        bodyCloses.incrementAndGet();
                    }
                };
        var response = response(body);
        var pending =
                new CompletableFuture<HttpResponse<InputStream>>() {
                    public boolean cancel(boolean interrupt) {
                        return false;
                    }
                };
        var requests = new AtomicInteger();
        var client = new FixedClient(pending, requests);
        var field = WebFetchPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        ((HttpClient) field.get(plugin)).shutdownNow();
        field.set(plugin, client);
        var cancellationHandoff = new AtomicBoolean();
        var cancelled = new AtomicBoolean();
        CancellationToken token =
                () -> {
                    if (requests.get() == 1 && cancellationHandoff.compareAndSet(false, true)) {
                        // The callback sees false before the await owner sees cancellation. Thus
                        // pending is complete while active never acquires ownership of its body.
                        if (beforeCancellation) pending.complete(response);
                        cancelled.set(true);
                    }
                    return cancelled.get();
                };
        try {
            var tool = tools.require("web_fetch", scope);
            assertEquals(
                    "CANCELLED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            tool.execute(
                                                    new ToolInvocation(
                                                            "late-header",
                                                            "web_fetch",
                                                            Map.of("source", "fixture"),
                                                            SideEffectLevel.SYSTEM_IMPACT),
                                                    new ToolContext(scope, token)))
                            .code());
            if (!beforeCancellation) {
                assertEquals(0, bodyCloses.get(), "body has not arrived yet");
                assertTrue(pending.complete(response));
            }
            assertEquals(1, bodyCloses.get(), "unhanded response must be reclaimed exactly once");
            assertEquals(1, requests.get(), "cancellation must never replay the fetch");
        } finally {
            plugin.stop();
            Collections.reverse(owned);
            for (var resource : owned) resource.close();
        }
    }

    private static HttpResponse<InputStream> response(InputStream body) {
        return new HttpResponse<>() {
            public int statusCode() {
                return 200;
            }

            public HttpRequest request() {
                return null;
            }

            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            public HttpHeaders headers() {
                return HttpHeaders.of(
                        Map.of("Content-Type", List.of("text/plain")), (a, b) -> true);
            }

            public InputStream body() {
                return body;
            }

            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            public URI uri() {
                return URI.create("http://127.0.0.1/fixed");
            }

            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class FixedClient extends HttpClient {
        final CompletableFuture<HttpResponse<InputStream>> pending;
        final AtomicInteger requests;

        FixedClient(CompletableFuture<HttpResponse<InputStream>> pending, AtomicInteger requests) {
            this.pending = pending;
            this.requests = requests;
        }

        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        public Version version() {
            return Version.HTTP_1_1;
        }

        public Optional<Executor> executor() {
            return Optional.empty();
        }

        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.incrementAndGet();
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) pending;
        }

        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushes) {
            return sendAsync(request, handler);
        }
    }

    static class OwnedProcess extends Process {
        final AtomicBoolean alive = new AtomicBoolean(true);
        final List<AtomicBoolean> children = new ArrayList<>();
        final AtomicInteger streamCloses = new AtomicInteger();
        final AtomicBoolean inputClosed = new AtomicBoolean(),
                outputClosed = new AtomicBoolean(),
                errorClosed = new AtomicBoolean();
        final boolean streamFailure;
        Error initializationFailure;

        OwnedProcess(int children, boolean streamFailure) {
            for (int i = 0; i < children; i++) this.children.add(new AtomicBoolean(true));
            this.streamFailure = streamFailure;
        }

        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream() {
                public void close() throws IOException {
                    if (outputClosed.compareAndSet(false, true)) streamCloses.incrementAndGet();
                    if (streamFailure) throw new IOException("fixture close failure");
                }
            };
        }

        public InputStream getInputStream() {
            if (initializationFailure != null) {
                var failure = initializationFailure;
                initializationFailure = null;
                throw failure;
            }
            return trackedInput(inputClosed);
        }

        public InputStream getErrorStream() {
            return trackedInput(errorClosed);
        }

        private InputStream trackedInput(AtomicBoolean flag) {
            return new ByteArrayInputStream(new byte[0]) {
                public void close() {
                    if (flag.compareAndSet(false, true)) streamCloses.incrementAndGet();
                }
            };
        }

        public int waitFor() {
            return 0;
        }

        public int exitValue() {
            return 0;
        }

        public void destroy() {
            alive.set(false);
        }

        public ProcessHandle toHandle() {
            return handle(alive, 1);
        }

        public Stream<ProcessHandle> descendants() {
            return java.util.stream.IntStream.range(0, children.size())
                    .mapToObj(i -> handle(children.get(i), i + 2));
        }

        private static ProcessHandle handle(AtomicBoolean alive, long pid) {
            return (ProcessHandle)
                    Proxy.newProxyInstance(
                            ProcessHandle.class.getClassLoader(),
                            new Class<?>[] {ProcessHandle.class},
                            (proxy, method, arguments) ->
                                    switch (method.getName()) {
                                        case "pid" -> pid;
                                        case "isAlive" -> alive.get();
                                        case "destroy", "destroyForcibly" -> {
                                            alive.set(false);
                                            yield true;
                                        }
                                        case "toString" -> "owned-fixture-" + pid;
                                        case "hashCode" -> Long.hashCode(pid);
                                        case "equals" -> proxy == arguments[0];
                                        default -> throw new AssertionError(method.getName());
                                    });
        }
    }

    public static class SleepingChild {
        public static void main(String[] args) throws Exception {
            Thread.sleep(30000);
        }
    }
}
