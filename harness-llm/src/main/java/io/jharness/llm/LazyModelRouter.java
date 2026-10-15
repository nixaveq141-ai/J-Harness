package io.jharness.llm;

import io.jharness.api.*;
import java.util.*;
import java.util.function.Supplier;

/** Lazy multi-adapter routing: unused providers need no credentials or network activity. */
public final class LazyModelRouter implements ModelRouter {
    private final Map<String, Supplier<LlmClient>> factories;
    private final Map<String, LlmClient> clients = new LinkedHashMap<>();
    private boolean closed;

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
    public synchronized LlmClient select(String route) {
        if (closed) throw new HarnessException("MODEL_ROUTER_CLOSED", "Router closed");
        Supplier<LlmClient> factory = factories.get(route);
        if (factory == null)
            throw new HarnessException("MODEL_ROUTE_NOT_FOUND", "Unknown model route");
        return clients.computeIfAbsent(route, ignored -> Objects.requireNonNull(factory.get()));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (LlmClient client : clients.values())
            try {
                client.close();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        clients.clear();
        if (failure != null) throw failure;
    }
}
