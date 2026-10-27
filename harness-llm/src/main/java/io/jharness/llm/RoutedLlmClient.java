package io.jharness.llm;

import io.jharness.api.*;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Frozen task route, with a separate router service for selecting adapters for future tasks. */
public final class RoutedLlmClient implements LlmClient, ModelRouter {
    private final ModelRouter router;
    private final LlmClient selected;
    private final UnaryOperator<LlmClient> decorate;

    public RoutedLlmClient(ModelRouter router, String route) {
        this.router = Objects.requireNonNull(router, "router");
        selected = Objects.requireNonNull(router.select(route), "selected client");
        decorate = client -> client;
    }

    private RoutedLlmClient(
            ModelRouter router, LlmClient selected, UnaryOperator<LlmClient> decorate) {
        this.router = router;
        this.selected = selected;
        this.decorate = decorate;
    }

    @Override
    public LlmClient forRecordedRequest(String eventId) {
        Values.required(eventId, "request event ID");
        return new RoutedLlmClient(
                router,
                selected.forRecordedRequest(eventId),
                client -> decorate.apply(client).forRecordedRequest(eventId));
    }

    @Override
    public LlmClient withDisplay(Consumer<String> observer) {
        Objects.requireNonNull(observer);
        return new RoutedLlmClient(
                router,
                selected.withDisplay(observer),
                client -> decorate.apply(client).withDisplay(observer));
    }

    @Override
    public LlmClient withDisplayUpdates(Consumer<ModelDisplay.Update> observer) {
        Objects.requireNonNull(observer);
        return new RoutedLlmClient(
                router,
                selected.withDisplayUpdates(observer),
                client -> decorate.apply(client).withDisplayUpdates(observer));
    }

    @Override
    public Set<String> routes() {
        return router.routes();
    }

    @Override
    public LlmClient select(String route) {
        return decorate.apply(Objects.requireNonNull(router.select(route), "selected client"));
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
