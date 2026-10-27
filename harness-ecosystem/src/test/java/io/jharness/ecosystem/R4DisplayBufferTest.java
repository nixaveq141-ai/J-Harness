package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class R4DisplayBufferTest {
    private SessionReader reader() {
        return () ->
                List.of(
                        new SessionEvent(
                                "request",
                                "session",
                                "model.requested",
                                Instant.now(),
                                1,
                                EventLevel.STEP,
                                "turn",
                                "step",
                                null,
                                Map.of()));
    }

    @Test
    void rapidFinalSnapshotIsVisibleWithoutASecondFrame() {
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"), reader(), new SecretRedactor());
        display.accept(new ModelDisplay.Update("first", false));
        display.accept(new ModelDisplay.Update("last snapshot", false));
        assertEquals("last snapshot", display.snapshot().get("text"));
    }

    @Test
    void sanitizerAndReaderFailureClearPresentationWithoutThrowing() {
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"),
                        reader(),
                        new DataSanitizer() {
                            public Map<String, Object> sanitize(Map<String, Object> value) {
                                throw new IllegalStateException();
                            }

                            public String streamingText(String text) {
                                throw new IllegalStateException();
                            }
                        });
        assertDoesNotThrow(() -> display.accept(new ModelDisplay.Update("private content", false)));
        assertEquals("UNAVAILABLE", display.snapshot().get("state"));
        assertEquals("", display.snapshot().get("text"));
        var failedReader =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"),
                        () -> {
                            throw new IllegalStateException();
                        },
                        new SecretRedactor());
        assertThrows(IllegalStateException.class, failedReader::snapshot);
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Controlled reader was not released");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    @Test
    void blockedFactSnapshotDoesNotBlockResetOrSettleTheNextGeneration() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var first = reader().events().getFirst();
        var next =
                new SessionEvent(
                        "next-request",
                        "session",
                        "model.requested",
                        Instant.now(),
                        1,
                        EventLevel.STEP,
                        "next-turn",
                        "next-step",
                        null,
                        Map.of());
        var persisted =
                new SessionEvent(
                        "old-answer",
                        "session",
                        "assistant.message",
                        Instant.now(),
                        1,
                        EventLevel.STEP,
                        "turn",
                        "step",
                        null,
                        Map.of());
        SessionReader facts =
                () -> {
                    int read = reads.incrementAndGet();
                    if (read == 2) {
                        entered.countDown();
                        await(release);
                        return List.of(first, persisted);
                    }
                    return List.of(read == 1 ? first : next);
                };
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"), facts, new SecretRedactor());
        display.accept(new ModelDisplay.Update("old", false));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var snapshot = workers.submit(display::snapshot);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                workers.submit(
                                () -> {
                                    display.reset();
                                    display.accept(new ModelDisplay.Update("new", false));
                                })
                        .get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            var result = snapshot.get(2, TimeUnit.SECONDS);
            assertEquals("next-request", result.get("sourceEvent"));
            assertEquals("STREAMING", result.get("state"));
            assertEquals("new", result.get("text"));
            assertEquals(3, reads.get());
        }
    }

    @Test
    void staleFailingPresentationCannotPoisonResetGeneration() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var reads = new AtomicInteger();
        SessionReader facts =
                () -> {
                    if (reads.incrementAndGet() == 1) {
                        entered.countDown();
                        await(release);
                        throw new IllegalStateException("old reader failure");
                    }
                    return reader().events();
                };
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"), facts, new SecretRedactor());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var stale = workers.submit(() -> display.accept(new ModelDisplay.Update("old", false)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                workers.submit(
                                () -> {
                                    display.reset();
                                    display.accept(new ModelDisplay.Update("new", false));
                                })
                        .get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            stale.get(2, TimeUnit.SECONDS);
            assertEquals("new", display.snapshot().get("text"));
            assertEquals("STREAMING", display.snapshot().get("state"));
        }
    }

    @Test
    void slowSanitizerCannotOverwriteNewerCumulativeUpdate() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var sanitizer =
                new DataSanitizer() {
                    public Map<String, Object> sanitize(Map<String, Object> value) {
                        return value;
                    }

                    public String streamingText(String value) {
                        if (value.equals("old")) {
                            entered.countDown();
                            await(release);
                        }
                        return value;
                    }
                };
        var display =
                new ModelDisplayBuffer(new PluginScope("session", "agent"), reader(), sanitizer);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var stale = workers.submit(() -> display.accept(new ModelDisplay.Update("old", true)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                workers.submit(() -> display.accept(new ModelDisplay.Update("new", false)))
                        .get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            stale.get(2, TimeUnit.SECONDS);
            assertEquals("new", display.snapshot().get("text"));
            assertEquals(false, display.snapshot().get("truncated"));
        }
    }
}
