package io.jharness.llm;

import io.jharness.api.*;
import java.util.*;
import java.util.function.Supplier;

/** Lazy multi-adapter routing: unused providers need no credentials or network activity. */
public final class LazyModelRouter implements ModelRouter {
    private final Map<String, Supplier<LlmClient>> factories;
    private final Map<String, LlmClient> clients = new LinkedHashMap<>();
    private final Map<String, String> closeFailures = new LinkedHashMap<>();
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

    /** Safe residual-resource metadata; failed close is never retried implicitly. */
    public synchronized Map<String, String> unconfirmedClosures() {
        return Map.copyOf(closeFailures);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        for (var entry : clients.entrySet())
            try {
                entry.getValue().close();
            } catch (RuntimeException | Error ex) {
                closeFailures.put(entry.getKey(), ex.getClass().getName());
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        // Failed clients remain referenced for owner diagnostics; close is not retried blindly.
        clients.keySet().removeIf(route -> !closeFailures.containsKey(route));
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
}
