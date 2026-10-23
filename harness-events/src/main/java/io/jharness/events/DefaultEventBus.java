package io.jharness.events;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Notification is asynchronous; serial and intercept are synchronous and ordered. */
public final class DefaultEventBus implements EventBus, AutoCloseable {
    private final List<NotifyEntry> notifications = new CopyOnWriteArrayList<>();
    private final List<SerialEntry> serials = new CopyOnWriteArrayList<>();
    private final List<InterceptEntry<?>> interceptors = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadLocal<Boolean> callback = ThreadLocal.withInitial(() -> false);
    private final ExecutorService notificationWorker =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(256),
                    r -> {
                        Thread t = new Thread(r, "harness-notify");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
    private final ExecutorService serialWorker = Executors.newVirtualThreadPerTaskExecutor();
    private final Consumer<EventFailure> diagnostics;
    private final Object serialLock = new Object();
    private volatile boolean closed;
    private volatile boolean serialUnconfirmed;

    public DefaultEventBus() {
        this(
                failure ->
                        System.getLogger(DefaultEventBus.class.getName())
                                .log(
                                        System.Logger.Level.WARNING,
                                        "code={0} eventId={1} type={2}",
                                        failure.code(),
                                        failure.eventId(),
                                        failure.type()));
    }

    public DefaultEventBus(Consumer<EventFailure> diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    private void ensureOpen() {
        if (closed) throw new HarnessException("BUS_CLOSED", "Event bus is closed");
    }

    private void rejectRecursive() {
        if (callback.get())
            throw new HarnessException("RECURSIVE_EVENT", "Publish from a listener is forbidden");
    }

    private void report(EventFailure failure) {
        try {
            diagnostics.accept(failure);
        } catch (RuntimeException ignored) {
            // A broken diagnostic sink must not turn observation into a runtime failure.
        }
    }

    @Override
    public synchronized Registration notify(String type, NotificationListener listener) {
        ensureOpen();
        Values.required(type, "type");
        Objects.requireNonNull(listener);
        NotifyEntry entry = new NotifyEntry(type, listener);
        notifications.add(entry);
        return () -> {
            entry.active.set(false);
            notifications.remove(entry);
        };
    }

    @Override
    public synchronized Registration serial(
            String type, SerialListener listener, int priority, Duration timeout) {
        ensureOpen();
        Values.required(type, "type");
        Objects.requireNonNull(listener);
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout");
        SerialEntry entry =
                new SerialEntry(type, listener, priority, sequence.getAndIncrement(), timeout);
        serials.add(entry);
        return () -> {
            entry.active.set(false);
            serials.remove(entry);
        };
    }

    @Override
    public synchronized <T> Registration intercept(
            ExtensionPoint<T> point, Interceptor<T> interceptor, int priority) {
        ensureOpen();
        Objects.requireNonNull(point);
        Objects.requireNonNull(interceptor);
        InterceptEntry<T> entry =
                new InterceptEntry<>(point, interceptor, priority, sequence.getAndIncrement());
        interceptors.add(entry);
        return () -> {
            entry.active.set(false);
            interceptors.remove(entry);
        };
    }

    private static boolean matches(String filter, SessionEvent event) {
        return filter.equals("*") || filter.equals(event.type());
    }

    @Override
    public synchronized void publish(SessionEvent event) {
        ensureOpen();
        rejectRecursive();
        Objects.requireNonNull(event);
        List<NotifyEntry> snapshot =
                notifications.stream().filter(e -> matches(e.type, event)).toList();
        try {
            notificationWorker.execute(
                    () -> {
                        callback.set(true);
                        try {
                            for (NotifyEntry entry : snapshot) {
                                if (!entry.active.get()) continue;
                                try {
                                    entry.listener.accept(event);
                                } catch (Exception ex) {
                                    report(
                                            new EventFailure(
                                                    "NOTIFY_FAILED",
                                                    event.eventId(),
                                                    event.type(),
                                                    ex));
                                }
                            }
                        } finally {
                            callback.remove();
                        }
                    });
        } catch (RejectedExecutionException saturated) {
            report(new EventFailure("NOTIFY_QUEUE_FULL", event.eventId(), event.type(), saturated));
        }
    }

    @Override
    public void publishSerial(SessionEvent event) {
        ensureOpen();
        rejectRecursive();
        Objects.requireNonNull(event);
        synchronized (serialLock) {
            ensureOpen();
            if (serialUnconfirmed)
                throw new HarnessException(
                        "SERIAL_UNCONFIRMED", "Earlier callback termination is unconfirmed");
            List<SerialEntry> snapshot =
                    serials.stream()
                            .filter(e -> matches(e.type, event))
                            .sorted(
                                    Comparator.comparingInt((SerialEntry e) -> e.priority)
                                            .thenComparingLong(e -> e.sequence))
                            .toList();
            for (SerialEntry entry : snapshot) {
                if (!entry.active.get()) continue;
                CountDownLatch exited = new CountDownLatch(1);
                Future<?> task;
                synchronized (this) {
                    ensureOpen();
                    task =
                            serialWorker.submit(
                                    () -> {
                                        callback.set(true);
                                        try {
                                            if (entry.active.get()) entry.listener.accept(event);
                                        } catch (Exception ex) {
                                            throw new CompletionException(ex);
                                        } finally {
                                            callback.remove();
                                            exited.countDown();
                                        }
                                    });
                }
                try {
                    task.get(entry.timeout.toNanos(), TimeUnit.NANOSECONDS);
                } catch (TimeoutException ex) {
                    task.cancel(true);
                    confirmSerialExit(exited);
                    throw new HarnessException(
                            "SERIAL_TIMEOUT", "Serial listener timed out: " + event.type(), ex);
                } catch (InterruptedException ex) {
                    task.cancel(true);
                    confirmSerialExit(exited);
                    Thread.currentThread().interrupt();
                    throw new HarnessException("CANCELLED", "Serial dispatch interrupted", ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    while (cause instanceof CompletionException && cause.getCause() != null)
                        cause = cause.getCause();
                    throw new HarnessException(
                            "SERIAL_FAILED", "Serial listener failed: " + event.type(), cause);
                }
            }
        }
    }

    @Override
    public <T> T invoke(ExtensionPoint<T> point, T request, Interceptor.Next<T> terminal) {
        ensureOpen();
        Objects.requireNonNull(point);
        Objects.requireNonNull(terminal);
        point.type().cast(Objects.requireNonNull(request));
        List<InterceptEntry<?>> snapshot =
                interceptors.stream()
                        .filter(e -> e.point.equals(point))
                        .sorted(
                                Comparator.comparingInt((InterceptEntry<?> e) -> e.priority)
                                        .thenComparingLong(e -> e.sequence))
                        .toList();
        Interceptor.Next<T> chain =
                value -> point.type().cast(terminal.proceed(point.type().cast(value)));
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            InterceptEntry<T> entry = typed(snapshot.get(index));
            Interceptor.Next<T> next = chain;
            chain =
                    value ->
                            entry.active.get()
                                    ? point.type()
                                            .cast(
                                                    entry.interceptor.intercept(
                                                            point.type().cast(value), next))
                                    : next.proceed(value);
        }
        try {
            return chain.proceed(request);
        } catch (HarnessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HarnessException(
                    "INTERCEPT_FAILED", "Interceptor failed: " + point.name(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> InterceptEntry<T> typed(InterceptEntry<?> entry) {
        // Entries were selected by ExtensionPoint equality, including its runtime Class<T>.
        return (InterceptEntry<T>) entry;
    }

    /** Test barrier: all notifications queued before this call have finished. */
    public void awaitNotifications(Duration timeout) {
        ensureOpen();
        rejectRecursive();
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout");
        Future<?> barrier;
        synchronized (this) {
            ensureOpen();
            try {
                barrier = notificationWorker.submit(() -> {});
            } catch (RejectedExecutionException ex) {
                throw new HarnessException(
                        "NOTIFY_QUEUE_FULL", "Notification queue is saturated", ex);
            }
        }
        try {
            barrier.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HarnessException("CANCELLED", "Notification barrier interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            barrier.cancel(true);
            throw new HarnessException(
                    "NOTIFY_BARRIER_FAILED", "Notification barrier did not complete", ex);
        }
    }

    private void confirmSerialExit(CountDownLatch exited) {
        try {
            if (!exited.await(100, TimeUnit.MILLISECONDS)) serialUnconfirmed = true;
        } catch (InterruptedException interrupted) {
            serialUnconfirmed = true;
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            notifications.forEach(e -> e.active.set(false));
            serials.forEach(e -> e.active.set(false));
            interceptors.forEach(e -> e.active.set(false));
            notifications.clear();
            serials.clear();
            interceptors.clear();
            notificationWorker.shutdownNow();
            serialWorker.shutdownNow();
        }
        try {
            boolean notificationsDone =
                    notificationWorker.awaitTermination(200, TimeUnit.MILLISECONDS);
            boolean serialDone = serialWorker.awaitTermination(200, TimeUnit.MILLISECONDS);
            if (!notificationsDone || !serialDone)
                report(new EventFailure("BUS_WORKERS_UNCONFIRMED", "lifecycle", "bus.close", null));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            report(
                    new EventFailure(
                            "BUS_WORKERS_UNCONFIRMED", "lifecycle", "bus.close", interrupted));
        }
    }

    private static final class NotifyEntry {
        final String type;
        final NotificationListener listener;
        final AtomicBoolean active = new AtomicBoolean(true);

        NotifyEntry(String type, NotificationListener listener) {
            this.type = type;
            this.listener = listener;
        }
    }

    private static final class SerialEntry {
        final String type;
        final SerialListener listener;
        final int priority;
        final long sequence;
        final Duration timeout;
        final AtomicBoolean active = new AtomicBoolean(true);

        SerialEntry(
                String type,
                SerialListener listener,
                int priority,
                long sequence,
                Duration timeout) {
            this.type = type;
            this.listener = listener;
            this.priority = priority;
            this.sequence = sequence;
            this.timeout = timeout;
        }
    }

    private static final class InterceptEntry<T> {
        final ExtensionPoint<T> point;
        final Interceptor<T> interceptor;
        final int priority;
        final long sequence;
        final AtomicBoolean active = new AtomicBoolean(true);

        InterceptEntry(
                ExtensionPoint<T> point, Interceptor<T> interceptor, int priority, long sequence) {
            this.point = point;
            this.interceptor = interceptor;
            this.priority = priority;
            this.sequence = sequence;
        }
    }
}
