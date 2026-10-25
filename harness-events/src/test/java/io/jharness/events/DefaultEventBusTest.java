package io.jharness.events;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class DefaultEventBusTest {
    private static SessionEvent event() {
        return SessionEvent.session("s", "test", Map.of());
    }

    @Test
    void notifyDoesNotBlockAndObserverFailureCannotChangeResult() throws Exception {
        List<EventFailure> errors = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DefaultEventBus bus = new DefaultEventBus(errors::add)) {
            bus.notify(
                    "test",
                    e -> {
                        entered.countDown();
                        release.await();
                    });
            bus.notify(
                    "test",
                    e -> {
                        throw new IllegalStateException("observer");
                    });
            AtomicInteger healthy = new AtomicInteger();
            bus.notify("test", e -> healthy.incrementAndGet());
            bus.publish(event()); // Must return while the first listener remains blocked.
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(0, healthy.get());
            release.countDown();
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals(1, healthy.get());
            assertEquals(1, errors.size(), "Exactly one observer diagnostic expected");
            assertEquals("NOTIFY_FAILED", errors.getFirst().code());
        } finally {
            release.countDown();
        }
    }

    @Test
    void removedNotificationDoesNotRunEvenIfAlreadyQueued() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DefaultEventBus bus = new DefaultEventBus()) {
            bus.notify(
                    "test",
                    e -> {
                        entered.countDown();
                        release.await();
                    });
            AtomicInteger hits = new AtomicInteger();
            Registration handle = bus.notify("test", e -> hits.incrementAndGet());
            bus.publish(event());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            handle.close();
            handle.close();
            release.countDown();
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals(0, hits.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void serialWaitsAndOrdersByPriorityThenRegistration() {
        try (DefaultEventBus bus = new DefaultEventBus()) {
            List<String> order = new CopyOnWriteArrayList<>();
            bus.serial("test", e -> order.add("last"), 20, Duration.ofSeconds(2));
            bus.serial("test", e -> order.add("first"), 0, Duration.ofSeconds(2));
            Registration removed =
                    bus.serial("test", e -> order.add("removed"), 0, Duration.ofSeconds(2));
            bus.serial("test", e -> order.add("second"), 0, Duration.ofSeconds(2));
            removed.close();
            bus.publishSerial(event());
            assertEquals(List.of("first", "second", "last"), order);
        }
    }

    @Test
    void serialFailureShortCircuitsAndTimeoutIsExplicit() {
        try (DefaultEventBus bus = new DefaultEventBus()) {
            AtomicBoolean reached = new AtomicBoolean();
            bus.serial(
                    "failure",
                    e -> {
                        throw new IllegalStateException("fail");
                    },
                    0,
                    Duration.ofSeconds(1));
            bus.serial("failure", e -> reached.set(true), 1, Duration.ofSeconds(1));
            HarnessException failure =
                    assertThrows(
                            HarnessException.class,
                            () ->
                                    bus.publishSerial(
                                            SessionEvent.session("s", "failure", Map.of())));
            assertEquals("SERIAL_FAILED", failure.code());
            assertFalse(reached.get());
            bus.serial("test", e -> new CountDownLatch(1).await(), 0, Duration.ofMillis(50));
            assertEquals(
                    "SERIAL_TIMEOUT",
                    assertThrows(HarnessException.class, () -> bus.publishSerial(event())).code());
        }
    }

    @Test
    void interceptorWrapsInOrderSupportsShortCircuitAndRevocation() {
        try (DefaultEventBus bus = new DefaultEventBus()) {
            ExtensionPoint<String> point = new ExtensionPoint<>("request", String.class);
            List<String> order = new ArrayList<>();
            bus.intercept(
                    point,
                    (value, next) -> {
                        order.add("outer-before");
                        String result = next.proceed(value + "A");
                        order.add("outer-after");
                        return result + "!";
                    },
                    0);
            bus.intercept(
                    point,
                    (value, next) -> {
                        order.add("inner");
                        return next.proceed(value + "B");
                    },
                    1);
            assertEquals("xAB!", bus.invoke(point, "x", value -> value));
            assertEquals(List.of("outer-before", "inner", "outer-after"), order);
            Registration deny = bus.intercept(point, (value, next) -> "denied", -1);
            AtomicBoolean executed = new AtomicBoolean();
            assertEquals(
                    "denied",
                    bus.invoke(
                            point,
                            "x",
                            value -> {
                                executed.set(true);
                                return value;
                            }));
            assertFalse(executed.get());
            deny.close();
            assertEquals("xAB!", bus.invoke(point, "x", value -> value));
        }
    }

    @Test
    void interceptorErrorsPropagateAndPointTypeIsPartOfIdentity() {
        try (DefaultEventBus bus = new DefaultEventBus()) {
            bus.intercept(new ExtensionPoint<>("same", Integer.class), (value, next) -> 99, 0);
            ExtensionPoint<String> point = new ExtensionPoint<>("same", String.class);
            assertEquals("ok", bus.invoke(point, "ok", value -> value));
            bus.intercept(
                    point,
                    (value, next) -> {
                        throw new IllegalStateException("fail");
                    },
                    0);
            assertEquals(
                    "INTERCEPT_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> bus.invoke(point, "x", value -> value))
                            .code());
        }
    }

    @Test
    void recursivePublicationIsRejectedAndClosedBusIsNotUsable() {
        List<EventFailure> errors = new CopyOnWriteArrayList<>();
        DefaultEventBus bus = new DefaultEventBus(errors::add);
        try {
            bus.notify("test", e -> bus.publish(e));
            bus.publish(event());
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals("RECURSIVE_EVENT", ((HarnessException) errors.getFirst().cause()).code());
            bus.serial("test", e -> bus.publishSerial(e), 0, Duration.ofSeconds(1));
            assertEquals(
                    "SERIAL_FAILED",
                    assertThrows(HarnessException.class, () -> bus.publishSerial(event())).code());
        } finally {
            bus.close();
        }
        bus.close();
        assertEquals(
                "BUS_CLOSED",
                assertThrows(HarnessException.class, () -> bus.publish(event())).code());
    }
}
