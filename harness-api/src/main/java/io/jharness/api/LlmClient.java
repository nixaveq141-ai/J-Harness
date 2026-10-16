package io.jharness.api;

public interface LlmClient extends AutoCloseable {
    @Override
    default void close() {}

    ModelCapabilities capabilities();

    PreparedRequest prepare(ChatRequest request);

    ChatResponse complete(ChatRequest request, CancellationToken cancellation);

    /** Opt-in single-send contract; legacy clients fail closed before sending. */
    default ChatResponse completeSingleAttempt(
            ChatRequest request, CancellationToken cancellation) {
        throw new HarnessException(
                "SINGLE_ATTEMPT_UNSUPPORTED", "Adapter has no single-send contract");
    }

    StreamHandle stream(ChatRequest request, CancellationToken cancellation);
}
