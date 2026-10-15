package io.jharness.api;

public interface LlmClient extends AutoCloseable {
    @Override
    default void close() {}

    ModelCapabilities capabilities();

    PreparedRequest prepare(ChatRequest request);

    ChatResponse complete(ChatRequest request, CancellationToken cancellation);

    StreamHandle stream(ChatRequest request, CancellationToken cancellation);
}
