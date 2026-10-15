package io.jharness.testkit;

import io.jharness.api.*;
import java.util.*;

/** Scripted responses and captured immutable requests; no network or secrets. */
public final class FakeLlm implements LlmClient {
    private final Deque<ChatResponse> responses;
    private final List<ChatRequest> requests = new ArrayList<>();
    private final int contextTokens;

    public FakeLlm(List<ChatResponse> responses) {
        this(responses, 8192);
    }

    public FakeLlm(List<ChatResponse> responses, int contextTokens) {
        this.responses = new ArrayDeque<>(responses);
        if (contextTokens <= 0) throw new IllegalArgumentException("contextTokens");
        this.contextTokens = contextTokens;
    }

    public synchronized List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, false, false, false, contextTokens);
    }

    @Override
    public PreparedRequest prepare(ChatRequest request) {
        return new PreparedRequest(request);
    }

    @Override
    public synchronized ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        if (responses.isEmpty())
            throw new HarnessException("FAKE_EXHAUSTED", "Fake LLM has no scripted response");
        requests.add(Objects.requireNonNull(request));
        return responses.removeFirst();
    }

    @Override
    public StreamHandle stream(ChatRequest request, CancellationToken cancellation) {
        throw new HarnessException("UNSUPPORTED", "Fake LLM does not support streaming");
    }
}
