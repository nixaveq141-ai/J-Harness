package io.jharness.llm;

import io.jharness.api.*;
import java.util.*;
import java.util.function.Consumer;

/** Streaming transport exposed as complete: the loop only sees one validated final response. */
public final class StreamingLlmClient implements LlmClient, ModelRouter {
    private final LlmClient delegate;
    private final Consumer<String> display;
    private final SecretRedactor redactor;
    private final List<String> secrets;
    private final Consumer<ModelDisplay.Update> updates;

    public StreamingLlmClient(LlmClient delegate, Consumer<String> display, List<String> secrets) {
        this(delegate, display, secrets, update -> {});
    }

    private StreamingLlmClient(
            LlmClient delegate,
            Consumer<String> display,
            List<String> secrets,
            Consumer<ModelDisplay.Update> updates) {
        this.delegate = Objects.requireNonNull(delegate);
        this.display = Objects.requireNonNull(display);
        this.secrets = List.copyOf(secrets);
        redactor = new SecretRedactor(secrets);
        this.updates = updates;
    }

    @Override
    public LlmClient withDisplay(Consumer<String> observer) {
        Objects.requireNonNull(observer);
        return new StreamingLlmClient(
                delegate,
                text -> {
                    notifySink(display, text, "");
                    notifySink(observer, text, "");
                },
                secrets,
                updates);
    }

    @Override
    public LlmClient withDisplayUpdates(Consumer<ModelDisplay.Update> observer) {
        Objects.requireNonNull(observer);
        return new StreamingLlmClient(
                delegate,
                display,
                secrets,
                update -> {
                    var empty = new ModelDisplay.Update("", false);
                    notifySink(updates, update, empty);
                    notifySink(observer, update, empty);
                });
    }

    private static <T> void notifySink(Consumer<T> sink, T value, T empty) {
        try {
            sink.accept(value);
        } catch (RuntimeException ignored) {
            try {
                sink.accept(empty);
            } catch (RuntimeException unavailable) {
                /* Other sinks still run. */
            }
        }
    }

    private void display(String cumulative) {
        var update = redactor.streamingUpdate(cumulative);
        // Observers are optional presentation sinks, never part of model completion.
        try {
            display.accept(update.text());
        } catch (RuntimeException ignored) {
            try {
                display.accept("");
            } catch (RuntimeException unavailable) {
                /* disabled sink */
            }
        }
        try {
            updates.accept(update);
        } catch (RuntimeException ignored) {
            try {
                updates.accept(redactor.streamingUpdate(""));
            } catch (RuntimeException unavailable) {
                /* disabled sink */
            }
        }
    }

    @Override
    public Set<String> routes() {
        return delegate instanceof ModelRouter router ? router.routes() : Set.of("base");
    }

    @Override
    public LlmClient select(String route) {
        if (delegate instanceof ModelRouter router) {
            LlmClient chosen = Objects.requireNonNull(router.select(route), "selected client");
            if (chosen == delegate) return this;
            return new StreamingLlmClient(chosen, display, secrets, updates);
        }
        if ("base".equals(route)) return this;
        throw new HarnessException("MODEL_ROUTE_NOT_FOUND", "Unknown model route");
    }

    @Override
    public ModelCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public PreparedRequest prepare(ChatRequest request) {
        var parameters = new LinkedHashMap<>(request.parameters());
        parameters.put("stream", capabilities().streaming());
        if (capabilities().streaming())
            parameters.put("stream_options", Map.of("include_usage", true));
        return delegate.prepare(new ChatRequest(request.messages(), request.tools(), parameters));
    }

    @Override
    public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
        if (!capabilities().streaming()) return delegate.complete(request, cancellation);
        display("");
        try (StreamHandle handle = delegate.stream(request, cancellation)) {
            handle.consume(
                    frame -> {
                        display(frame.text());
                    });
            ChatResponse response = handle.finalResponse();
            if (response == null)
                throw new HarnessException(
                        "MODEL_BAD_RESPONSE", "Stream returned no final response");
            display(response.text());
            return response;
        }
    }

    @Override
    public boolean supportsSingleAttempt() {
        return delegate.supportsSingleAttempt();
    }

    @Override
    public ChatResponse completeSingleAttempt(ChatRequest request, CancellationToken cancellation) {
        if (capabilities().streaming()) return complete(request, cancellation);
        return delegate.completeSingleAttempt(request, cancellation);
    }

    @Override
    public StreamHandle stream(ChatRequest request, CancellationToken cancellation) {
        return delegate.stream(request, cancellation);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
