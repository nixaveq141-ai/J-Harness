package io.jharness.api;

public interface LlmClient extends AutoCloseable {
    @Override
    default void close() {}

    /**
     * Optional independent host display observer. Text is a cumulative sanitized snapshot, never a
     * delta or execution fact. Implementations must isolate display failures from model completion;
     * low-level stream() frames are provider data and require sanitization before presentation,
     * logging or export.
     */
    default LlmClient withDisplay(java.util.function.Consumer<String> display) {
        return this;
    }

    default LlmClient withDisplayUpdates(java.util.function.Consumer<ModelDisplay.Update> display) {
        return withDisplay(text -> display.accept(new ModelDisplay.Update(text, false)));
    }

    ModelCapabilities capabilities();

    PreparedRequest prepare(ChatRequest request);

    ChatResponse complete(ChatRequest request, CancellationToken cancellation);

    /** Query before journaling; existing explicit overrides retain their single-send contract. */
    default boolean supportsSingleAttempt() {
        try {
            return getClass()
                            .getMethod(
                                    "completeSingleAttempt",
                                    ChatRequest.class,
                                    CancellationToken.class)
                            .getDeclaringClass()
                    != LlmClient.class;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    /** Opt-in single-send contract; legacy clients fail closed before sending. */
    default ChatResponse completeSingleAttempt(
            ChatRequest request, CancellationToken cancellation) {
        throw new HarnessException(
                "SINGLE_ATTEMPT_UNSUPPORTED", "Adapter has no single-send contract");
    }

    StreamHandle stream(ChatRequest request, CancellationToken cancellation);
}
