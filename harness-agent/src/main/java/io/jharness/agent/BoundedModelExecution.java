package io.jharness.agent;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** No executor or unbounded queue: one caller executes, bounded callers wait cooperatively. */
public final class BoundedModelExecution implements ModelExecution, AutoCloseable {
    private final ModelResilienceOptions options;
    private final BiConsumer<String, Map<String, Object>> audit;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean active;
    private int waiters;
    private long lastStart;
    private boolean started;

    public BoundedModelExecution(ModelResilienceOptions options) {
        this(options, (type, payload) -> {});
    }

    public BoundedModelExecution(
            ModelResilienceOptions options, BiConsumer<String, Map<String, Object>> audit) {
        this.options = Objects.requireNonNull(options);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public ChatResponse execute(ModelAttempt attempt, CancellationToken cancellation) {
        if (closed.get()) throw error("MODEL_RESILIENCE_CLOSED");
        if (!options.enabled()) return attempt.complete(false, cancellation);
        long start = System.nanoTime();
        CancellationToken bounded =
                () ->
                        closed.get()
                                || cancellation.isCancelled()
                                || System.nanoTime() - start
                                        >= options.totalTimeoutMillis() * 1000000L;
        boolean acquired = false;
        try {
            synchronized (this) {
                if (active) {
                    if (waiters >= options.maxWaiters()) throw error("MODEL_RATE_QUEUE_FULL");
                    audit.accept(
                            "model.rate.waiting",
                            Map.of("reason", "CONCURRENCY", "maxWaiters", options.maxWaiters()));
                    waiters++;
                    try {
                        while (active) {
                            check(bounded, cancellation, start);
                            pause();
                        }
                    } finally {
                        waiters--;
                    }
                }
                check(bounded, cancellation, start);
                active = true;
                acquired = true;
            }
            for (int index = 0; ; index++) {
                if (started
                        && System.nanoTime() - lastStart < options.minIntervalMillis() * 1000000L)
                    audit.accept(
                            "model.rate.waiting",
                            Map.of(
                                    "reason",
                                    "INTERVAL",
                                    "minIntervalMillis",
                                    options.minIntervalMillis()));
                while (started
                        && System.nanoTime() - lastStart < options.minIntervalMillis() * 1000000L) {
                    check(bounded, cancellation, start);
                    synchronized (this) {
                        pause();
                    }
                }
                check(bounded, cancellation, start);
                lastStart = System.nanoTime();
                started = true;
                audit.accept(
                        "model.attempt.started",
                        Map.of("attempt", index + 1, "maxAttempts", options.maxAttempts()));
                try {
                    ChatResponse response = attempt.complete(true, bounded);
                    check(bounded, cancellation, start);
                    return response;
                } catch (ModelRejectedException rejected) {
                    check(bounded, cancellation, start);
                    if (index + 1 >= options.maxAttempts()) throw error("MODEL_RATE_LIMIT");
                    long backoff =
                            Math.min(10000L, ((long) options.backoffMillis()) << index) * 1000000L;
                    audit.accept(
                            "model.retry.scheduled",
                            Map.of(
                                    "reason",
                                    "HTTP_429",
                                    "nextAttempt",
                                    index + 2,
                                    "backoffMillis",
                                    backoff / 1000000L));
                    long waiting = System.nanoTime();
                    while (System.nanoTime() - waiting < backoff) {
                        check(bounded, cancellation, start);
                        synchronized (this) {
                            pause();
                        }
                    }
                }
            }
        } catch (HarnessException ex) {
            if (ex.code().equals("CANCELLED")) {
                try {
                    check(bounded, cancellation, start);
                } catch (HarnessException mapped) {
                    ex = mapped;
                }
            }
            audit.accept("model.execution.stopped", Map.of("reason", ex.code()));
            throw ex;
        } finally {
            if (acquired)
                synchronized (this) {
                    active = false;
                    notifyAll();
                }
        }
    }

    private void check(CancellationToken bounded, CancellationToken parent, long start) {
        if (parent.isCancelled() || Thread.currentThread().isInterrupted())
            throw error("CANCELLED");
        if (closed.get()) throw error("MODEL_RESILIENCE_CLOSED");
        if (System.nanoTime() - start >= options.totalTimeoutMillis() * 1000000L)
            throw error("MODEL_RESILIENCE_TIMEOUT");
        bounded.throwIfCancelled();
    }

    private void pause() {
        try {
            wait(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw error("CANCELLED");
        }
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }

    @Override
    public void close() {
        closed.set(true);
        synchronized (this) {
            notifyAll();
        }
    }
}
