package io.jharness.llm;

import io.jharness.api.*;
import java.util.Objects;
import java.util.Set;

/** Frozen task route, with a separate router service for selecting adapters for future tasks. */
public final class RoutedLlmClient implements LlmClient, ModelRouter {
    private final ModelRouter router;
    private final LlmClient selected;

    public RoutedLlmClient(ModelRouter router, String route) {
        this.router = Objects.requireNonNull(router, "router");
        selected = Objects.requireNonNull(router.select(route), "selected client");
    }

    @Override
    public Set<String> routes() {
        return router.routes();
    }

    @Override
    public LlmClient select(String route) {
        return router.select(route);
    }

    @Override
    public ModelCapabilities capabilities() {
        return selected.capabilities();
    }

    @Override
    public PreparedRequest prepare(ChatRequest request) {
        return selected.prepare(request);
    }

    @Override
    public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
        return selected.complete(request, cancellation);
    }

    @Override
    public boolean supportsSingleAttempt() {
        return selected.supportsSingleAttempt();
    }

    @Override
    public ChatResponse completeSingleAttempt(ChatRequest request, CancellationToken cancellation) {
        return selected.completeSingleAttempt(request, cancellation);
    }

    @Override
    public StreamHandle stream(ChatRequest request, CancellationToken cancellation) {
        return selected.stream(request, cancellation);
    }

    @Override
    public void close() {
        router.close();
    }
}
