package io.jharness.events;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class R1EventSafetyTest {
    @Test
    void saturatedDiagnosticDoesNotHoldBusMonitorAndBarrierWaitsForAdmission() throws Exception {
        var listenerEntered = new CountDownLatch(1);
        var releaseListener = new CountDownLatch(1);
        var diagnosticEntered = new CountDownLatch(1);
        var releaseDiagnostic = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
                var bus =
                        new DefaultEventBus(
                                failure -> {
                                    diagnosticEntered.countDown();
                                    try {
                                        releaseDiagnostic.await();
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    }
                                })) {
            bus.notify(
                    "*",
                    event -> {
                        listenerEntered.countDown();
                        try {
                            releaseListener.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    });
            bus.publish(event());
            assertTrue(listenerEntered.await(1, TimeUnit.SECONDS));
            for (int i = 0; i < 256; i++) bus.publish(event());
            var saturated = workers.submit(() -> bus.publish(event()));
            try {
                assertTrue(diagnosticEntered.await(1, TimeUnit.SECONDS));
                var registration = workers.submit(() -> bus.notify("other", event -> {}));
                registration.get(1, TimeUnit.SECONDS).close();
                var barrier = workers.submit(() -> bus.awaitNotifications(Duration.ofSeconds(3)));
                releaseListener.countDown();
                barrier.get(3, TimeUnit.SECONDS);
            } finally {
                releaseListener.countDown();
                releaseDiagnostic.countDown();
                saturated.get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void cancelledSerialTaskThatNeverStartedDoesNotPoisonTheBus() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        try (var bus = new DefaultEventBus(failure -> {})) {
            var field = DefaultEventBus.class.getDeclaredField("serialWorker");
            field.setAccessible(true);
            ((ExecutorService) field.get(bus)).close();
            var executor = Executors.newSingleThreadExecutor();
            field.set(bus, executor);
            executor.submit(
                    () -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                bus.serial("*", event -> {}, 0, Duration.ofMillis(300));
                assertEquals(
                        "SERIAL_TIMEOUT",
                        assertThrows(HarnessException.class, () -> bus.publishSerial(event()))
                                .code());
                release.countDown();
                executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
                assertDoesNotThrow(() -> bus.publishSerial(event()));
            } finally {
                release.countDown();
            }
        }
    }

    static SessionEvent event() {
        return SessionEvent.session("s", "fixture", Map.of());
    }

    @Test
    void failureSummaryOmitsCauseMessageAndFreeFormEventIdentity() {
        var failure =
                new EventFailure(
                        "NOTIFY_FAILED",
                        "sentinel-private",
                        "sentinel-private",
                        new IllegalStateException("sentinel-private"));
        assertFalse(failure.toString().contains("sentinel-private"));
        assertTrue(failure.toString().contains("IllegalStateException"));
    }

    @Test
    void uncooperativeSerialTimeoutBlocksLaterDispatchUntilClose() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        try (var bus = new DefaultEventBus()) {
            bus.serial(
                    "*",
                    e -> {
                        started.countDown();
                        try {
                            while (release.getCount() != 0) {
                                try {
                                    release.await();
                                } catch (InterruptedException ignored) {
                                }
                            }
                        } finally {
                            exited.countDown();
                        }
                    },
                    0,
                    Duration.ofMillis(200));
            try {
                assertEquals(
                        "SERIAL_TIMEOUT",
                        assertThrows(HarnessException.class, () -> bus.publishSerial(event()))
                                .code());
                assertEquals(0, started.getCount());
                assertEquals(1, exited.getCount());
                assertEquals(
                        "SERIAL_UNCONFIRMED",
                        assertThrows(HarnessException.class, () -> bus.publishSerial(event()))
                                .code());
            } finally {
                release.countDown();
                assertTrue(exited.await(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void notificationSaturationIsBoundedAndDiagnosedWithoutRejectingPersistedFact()
            throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var delivered = new CountDownLatch(257);
        var failures = new CopyOnWriteArrayList<EventFailure>();
        try (var bus = new DefaultEventBus(failures::add)) {
            bus.notify(
                    "*",
                    e -> {
                        started.countDown();
                        release.await();
                        delivered.countDown();
                    });
            try {
                bus.publish(event());
                assertTrue(started.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 300; i++) bus.publish(event());
                assertTrue(failures.stream().anyMatch(e -> e.code().equals("NOTIFY_QUEUE_FULL")));
            } finally {
                release.countDown();
            }
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            bus.awaitNotifications(Duration.ofSeconds(2));
        }
    }

    @Test
    void recursiveSerialFailurePreservesExplicitRecursiveCause() {
        try (var bus = new DefaultEventBus()) {
            bus.serial("*", e -> bus.publishSerial(e), 0, Duration.ofSeconds(2));
            var error = assertThrows(HarnessException.class, () -> bus.publishSerial(event()));
            assertEquals("SERIAL_FAILED", error.code());
            Throwable cause = error;
            boolean recursive = false;
            while (cause != null) {
                if (cause instanceof HarnessException h && h.code().equals("RECURSIVE_EVENT"))
                    recursive = true;
                cause = cause.getCause();
            }
            assertTrue(recursive);
        }
    }

    @Test
    void barriersRejectNonpositiveTimeoutAndClosedBusWithStableCode() {
        var bus = new DefaultEventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.awaitNotifications(Duration.ZERO));
        bus.close();
        assertEquals(
                "BUS_CLOSED",
                assertThrows(
                                HarnessException.class,
                                () -> bus.awaitNotifications(Duration.ofSeconds(1)))
                        .code());
        assertEquals(
                "BUS_CLOSED",
                assertThrows(HarnessException.class, () -> bus.publishSerial(event())).code());
    }

    @Test
    void closeReportsWorkerThatDidNotCooperateAndReturnsBeforeItsRelease() throws Exception {
        var failures = new CopyOnWriteArrayList<EventFailure>();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var bus = new DefaultEventBus(failures::add);
        bus.notify(
                "*",
                e -> {
                    started.countDown();
                    try {
                        while (release.getCount() != 0) {
                            try {
                                release.await();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } finally {
                        exited.countDown();
                    }
                });
        try {
            bus.publish(event());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            bus.close();
            assertEquals(1, exited.getCount());
            assertTrue(failures.stream().anyMatch(f -> f.code().equals("BUS_WORKERS_UNCONFIRMED")));
        } finally {
            release.countDown();
            assertTrue(exited.await(2, TimeUnit.SECONDS));
            bus.close();
        }
    }
}
