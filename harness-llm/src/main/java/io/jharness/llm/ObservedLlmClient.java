package io.jharness.llm;

import io.jharness.api.*;
import java.util.*;

/** Metadata-only request observation; never records prompt or response bodies. */
public final class ObservedLlmClient implements LlmClient, ModelRouter {
    private final LlmClient delegate;
    private final SessionWriter writer;
    private final SessionReader reader;
    private final DataSanitizer sanitizer;
    private final String session;
    private final String requestEvent;

    public ObservedLlmClient(
            LlmClient delegate,
            SessionWriter writer,
            SessionReader reader,
            DataSanitizer sanitizer,
            String session) {
        this(delegate, writer, reader, sanitizer, session, "");
    }

    private ObservedLlmClient(
            LlmClient delegate,
            SessionWriter writer,
            SessionReader reader,
            DataSanitizer sanitizer,
            String session,
            String requestEvent) {
        this.delegate = Objects.requireNonNull(delegate);
        this.writer = Objects.requireNonNull(writer);
        this.reader = Objects.requireNonNull(reader);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.session = session;
        this.requestEvent = requestEvent;
    }

    @Override
    public LlmClient forRecordedRequest(String eventId) {
        Values.required(eventId, "request event ID");
        return new ObservedLlmClient(
                delegate.forRecordedRequest(eventId), writer, reader, sanitizer, session, eventId);
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
            if (chosen instanceof ObservedLlmClient observed
                    && observed.writer == writer
                    && observed.reader == reader
                    && observed.sanitizer == sanitizer
                    && observed.requestEvent.equals(requestEvent)
                    && Objects.equals(observed.session, session)) return chosen;
            return new ObservedLlmClient(
                    requestEvent.isEmpty() ? chosen : chosen.forRecordedRequest(requestEvent),
                    writer,
                    reader,
                    sanitizer,
                    session,
                    requestEvent);
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
        return delegate.prepare(request);
    }

    @Override
    public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
        return complete(request, cancellation, false);
    }

    @Override
    public boolean supportsSingleAttempt() {
        return delegate.supportsSingleAttempt();
    }

    @Override
    public ChatResponse completeSingleAttempt(ChatRequest request, CancellationToken cancellation) {
        return complete(request, cancellation, true);
    }

    private ChatResponse complete(
            ChatRequest request, CancellationToken cancellation, boolean single) {
        long start = System.nanoTime();
        ChatResponse response = null;
        String outcome = "SUCCESS";
        RuntimeException primary = null;
        try {
            response =
                    single
                            ? delegate.completeSingleAttempt(request, cancellation)
                            : delegate.complete(request, cancellation);
            return response;
        } catch (RuntimeException ex) {
            primary = ex;
            outcome =
                    ex instanceof ModelRejectedException
                            ? "MODEL_RATE_LIMIT"
                            : ex instanceof HarnessException h ? h.code() : "MODEL_FAILED";
            throw ex;
        } finally {
            try {
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("elapsedMs", (System.nanoTime() - start) / 1000000);
                metrics.put("outcome", outcome);
                metrics.put("requestCount", 1);
                metrics.put("model", request.parameters().getOrDefault("model", "unknown"));
                metrics.put("stream", Boolean.TRUE.equals(request.parameters().get("stream")));
                if (!requestEvent.isEmpty()) metrics.put("sourceEvent", requestEvent);
                else metrics.put("sourceAssociation", "UNBOUND");
                if (response != null) {
                    Map<String, Object> usage = new LinkedHashMap<>();
                    for (String key : List.of("prompt_tokens", "completion_tokens", "total_tokens"))
                        if (response.usage().get(key) instanceof Number number)
                            usage.put(key, number);
                    metrics.put("usage", usage);
                }
                writer.append(
                        SessionEvent.session(
                                session, "model.observed", sanitizer.sanitize(metrics)));
            } catch (RuntimeException observation) {
                if (primary != null) primary.addSuppressed(observation);
                else throw observation;
            }
        }
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
