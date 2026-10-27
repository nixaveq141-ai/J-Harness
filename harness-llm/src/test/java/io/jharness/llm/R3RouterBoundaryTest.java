package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R3RouterBoundaryTest {
    private LlmClient model(java.util.function.Consumer<ChatRequest> complete) {
        return new LlmClient() {
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(false, false, false, false, 4096);
            }

            public PreparedRequest prepare(ChatRequest request) {
                return new PreparedRequest(request);
            }

            public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
                complete.accept(request);
                return new ChatResponse("done", List.of(), Map.of());
            }

            public boolean supportsSingleAttempt() {
                return true;
            }

            public ChatResponse completeSingleAttempt(
                    ChatRequest request, CancellationToken cancellation) {
                return complete(request, cancellation);
            }

            public StreamHandle stream(ChatRequest request, CancellationToken cancellation) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private SessionWriter writer(List<SessionEvent> facts) {
        return new SessionWriter() {
            public String append(SessionEvent event) {
                facts.add(event);
                return event.eventId();
            }

            public void flush() {}

            public void close() {}
        };
    }

    @Test
    void recordedIdentitySurvivesRoutingStreamingAndConcurrentIdenticalRequestsWithoutHistoryReads()
            throws Exception {
        var facts = new CopyOnWriteArrayList<SessionEvent>();
        var reads = new AtomicInteger();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var request =
                new ChatRequest(
                        List.of(Map.of("role", "user", "content", "same request")),
                        List.of(),
                        Map.of("model", "fixture", "stream", false));
        var raw =
                model(
                        actual -> {
                            assertSame(request, actual);
                            entered.countDown();
                            try {
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                            } catch (InterruptedException error) {
                                throw new AssertionError(error);
                            }
                        });
        var observed =
                new ObservedLlmClient(
                        raw,
                        writer(facts),
                        () -> {
                            reads.incrementAndGet();
                            throw new AssertionError("A bound observation must not scan history");
                        },
                        new SecretRedactor(),
                        "session");
        try (var router =
                        new LazyModelRouter(
                                Map.of(
                                        "base",
                                        () ->
                                                new StreamingLlmClient(
                                                        observed, text -> {}, List.of())));
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var routed = new RoutedLlmClient(router, "base");
            var a = routed.forRecordedRequest("request-a");
            var b = routed.forRecordedRequest("request-b");
            var first = pool.submit(() -> a.complete(request, CancellationToken.NONE));
            var second =
                    pool.submit(() -> b.completeSingleAttempt(request, CancellationToken.NONE));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }
        assertEquals(0, reads.get());
        assertEquals(2, facts.size());
        assertEquals(
                Set.of("request-a", "request-b"),
                facts.stream()
                        .map(e -> (String) e.payload().get("sourceEvent"))
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(
                facts.stream()
                        .allMatch(
                                e ->
                                        e.type().equals("model.observed")
                                                && "SUCCESS".equals(e.payload().get("outcome"))));
    }

    @Test
    void unboundObservationDoesNotInventSourceAndBoundFailureKeepsPrimaryException() {
        var facts = new ArrayList<SessionEvent>();
        SessionReader unreadable =
                () -> {
                    throw new AssertionError("No implicit latest-request lookup");
                };
        var request = new ChatRequest(List.of(), List.of(), Map.of());
        new ObservedLlmClient(
                        model(value -> {}),
                        writer(facts),
                        unreadable,
                        new SecretRedactor(),
                        "session")
                .complete(request, CancellationToken.NONE);
        assertEquals("UNBOUND", facts.getFirst().payload().get("sourceAssociation"));
        assertFalse(facts.getFirst().payload().containsKey("sourceEvent"));
        var primary = new HarnessException("MODEL_FAILED", "fixture failure");
        var secondary = new IllegalStateException("observation failed");
        SessionWriter broken =
                new SessionWriter() {
                    public String append(SessionEvent event) {
                        throw secondary;
                    }

                    public void flush() {}

                    public void close() {}
                };
        var bound =
                new ObservedLlmClient(
                                model(
                                        value -> {
                                            throw primary;
                                        }),
                                broken,
                                unreadable,
                                new SecretRedactor(),
                                "session")
                        .forRecordedRequest("request-failed");
        assertSame(
                primary,
                assertThrows(
                        HarnessException.class,
                        () -> bound.complete(request, CancellationToken.NONE)));
        assertEquals(List.of(secondary), List.of(primary.getSuppressed()));
    }

    @Test
    void factoryRunsOutsideLockAndLateClientClosesAfterRouterClose() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closes = new AtomicInteger();
        var router =
                new LazyModelRouter(
                        Map.of(
                                "base",
                                () -> {
                                    entered.countDown();
                                    try {
                                        assertTrue(release.await(3, TimeUnit.SECONDS));
                                    } catch (InterruptedException ex) {
                                        throw new IllegalStateException(ex);
                                    }
                                    return new Client(closes);
                                }));
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = worker.submit(() -> router.select("base"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var close = worker.submit(router::close);
            try {
                close.get(1, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            var failure =
                    assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertEquals("MODEL_ROUTER_CLOSED", ((HarnessException) failure.getCause()).code());
            assertEquals(1, closes.get());
            router.close();
            assertEquals(1, closes.get());
        } finally {
            release.countDown();
            router.close();
        }
    }

    @Test
    void factoryReentryCannotCorruptMapOrRecursivelyConstructSameRoute() {
        var reference = new AtomicReference<LazyModelRouter>();
        var closes = new AtomicInteger();
        var router =
                new LazyModelRouter(
                        Map.of(
                                "a",
                                () -> {
                                    reference.get().select("b");
                                    return new Client(closes);
                                },
                                "b",
                                () -> new Client(closes),
                                "recursive",
                                () -> reference.get().select("recursive")));
        reference.set(router);
        assertNotSame(router.select("a"), router.select("b"));
        assertEquals(
                "MODEL_ROUTE_RECURSION",
                assertThrows(HarnessException.class, () -> router.select("recursive")).code());
        assertEquals(
                "MODEL_ROUTE_NOT_FOUND",
                assertThrows(HarnessException.class, () -> router.select(null)).code());
        router.close();
        assertEquals(2, closes.get());
    }

    @Test
    void factoryFailureAfterCloseRemovesPendingMarkerWithoutClaimingClientCleanup()
            throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var router =
                new LazyModelRouter(
                        Map.of(
                                "base",
                                () -> {
                                    entered.countDown();
                                    try {
                                        assertTrue(release.await(3, TimeUnit.SECONDS));
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                    throw new IllegalStateException(
                                            "factory failed before returning a client");
                                }));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var selecting = pool.submit(() -> router.select("base"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            router.close();
            assertEquals(Map.of("base", "FACTORY_PENDING"), router.unconfirmedClosures());
            release.countDown();
            assertThrows(ExecutionException.class, () -> selecting.get(2, TimeUnit.SECONDS));
            assertTrue(router.unconfirmedClosures().isEmpty());
        } finally {
            release.countDown();
            router.close();
        }
    }

    private static final class Client implements LlmClient {
        private final AtomicInteger closes;

        Client(AtomicInteger closes) {
            this.closes = closes;
        }

        public ModelCapabilities capabilities() {
            return new ModelCapabilities(false, false, false, false, 1);
        }

        public PreparedRequest prepare(ChatRequest request) {
            return new PreparedRequest(request);
        }

        public ChatResponse complete(ChatRequest request, CancellationToken token) {
            return new ChatResponse("done", List.of(), Map.of());
        }

        public StreamHandle stream(ChatRequest request, CancellationToken token) {
            throw new UnsupportedOperationException();
        }

        public void close() {
            closes.incrementAndGet();
        }
    }
}
