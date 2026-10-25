package io.jharness.llm;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Lazy multi-adapter routing: unused providers need no credentials or network activity. */
public final class LazyModelRouter implements ModelRouter {
    private final Map<String, Supplier<LlmClient>> factories;
    private final Map<String, LlmClient> clients = new LinkedHashMap<>();
    private final Map<String, String> closeFailures = new LinkedHashMap<>();
    private boolean closed;

    private record Creation(Thread owner, CompletableFuture<LlmClient> future) {}

    private final Map<String, Creation> creating = new HashMap<>();

    public LazyModelRouter(Map<String, Supplier<LlmClient>> factories) {
        if (factories.isEmpty() || factories.size() > 64)
            throw new IllegalArgumentException("Invalid routes");
        this.factories = Map.copyOf(factories);
    }

    @Override
    public Set<String> routes() {
        return factories.keySet();
    }

    @Override
    public LlmClient select(String route) {
        Supplier<LlmClient> factory;
        Creation creation;
        boolean owner;
        synchronized (this) {
            if (closed) throw new HarnessException("MODEL_ROUTER_CLOSED", "Router closed");
            factory = route == null ? null : factories.get(route);
            if (factory == null)
                throw new HarnessException("MODEL_ROUTE_NOT_FOUND", "Unknown model route");
            if (clients.containsKey(route)) return clients.get(route);
            creation = creating.get(route);
            owner = creation == null;
            if (owner) {
                creation = new Creation(Thread.currentThread(), new CompletableFuture<>());
                creating.put(route, creation);
            } else if (creation.owner() == Thread.currentThread()) {
                throw new HarnessException(
                        "MODEL_ROUTE_RECURSION", "Factory recursively selected its own route");
            }
        }
        if (!owner) {
            try {
                return creation.future().join();
            } catch (CompletionException ex) {
                throw propagate(ex.getCause());
            }
        }
        try {
            LlmClient client = Objects.requireNonNull(factory.get(), "factory client");
            boolean abandoned;
            synchronized (this) {
                abandoned = closed;
                creating.remove(route, creation);
                if (!abandoned) {
                    clients.put(route, client);
                    creation.future().complete(client);
                }
            }
            if (abandoned) {
                var failure =
                        new HarnessException(
                                "MODEL_ROUTER_CLOSED", "Router closed during client construction");
                try {
                    client.close();
                    synchronized (this) {
                        closeFailures.remove(route);
                    }
                } catch (RuntimeException | Error cleanup) {
                    synchronized (this) {
                        clients.put(route, client);
                        closeFailures.put(route, cleanup.getClass().getName());
                    }
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
            return client;
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                creating.remove(route, creation);
                if (!clients.containsKey(route)) closeFailures.remove(route, "FACTORY_PENDING");
            }
            creation.future().completeExceptionally(failure);
            throw failure;
        }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error fatal) throw fatal;
        return failure instanceof RuntimeException runtime
                ? runtime
                : new HarnessException(
                        "MODEL_ROUTE_FACTORY_FAILED", "Model route construction failed", failure);
    }

    /** Safe residual-resource metadata; failed close is never retried implicitly. */
    public synchronized Map<String, String> unconfirmedClosures() {
        return Map.copyOf(closeFailures);
    }

    @Override
    public void close() {
        Map<String, LlmClient> owned;
        synchronized (this) {
            if (closed) return;
            closed = true;
            owned = new LinkedHashMap<>(clients);
            for (var entry : creating.entrySet()) {
                closeFailures.put(entry.getKey(), "FACTORY_PENDING");
                entry.getValue()
                        .future()
                        .completeExceptionally(
                                new HarnessException("MODEL_ROUTER_CLOSED", "Router closed"));
            }
        }
        Throwable failure = null;
        for (var entry : owned.entrySet()) {
            try {
                entry.getValue().close();
                synchronized (this) {
                    clients.remove(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException | Error ex) {
                synchronized (this) {
                    closeFailures.put(entry.getKey(), ex.getClass().getName());
                }
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
}
