package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R3RouterBoundaryTest {
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
