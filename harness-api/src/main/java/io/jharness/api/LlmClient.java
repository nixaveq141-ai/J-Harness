package io.jharness.api;

public interface LlmClient extends AutoCloseable {
    @Override
    default void close() {}

    /**
     * Borrowed per-call view bound to the already persisted model.requested fact. The identity is
     * host metadata, never a provider parameter. Legacy adapters may ignore it; decorators must
     * forward it. The loop closes the original owning client, not this temporary view.
     */
    default LlmClient forRecordedRequest(String eventId) {
        Values.required(eventId, "request event ID");
        return this;
    }

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
        java.util.Objects.requireNonNull(display, "display");
        return withDisplay(text -> display.accept(new ModelDisplay.Update(text, false)));
    }

    ModelCapabilities capabilities();

    PreparedRequest prepare(ChatRequest request);

    ChatResponse complete(ChatRequest request, CancellationToken cancellation);

    /**
     * Query before journaling. Reflection preserves compatibility with existing explicit overrides;
     * it does not verify delegation. Decorators must explicitly propagate the delegate's capability
     * and must never invoke a retrying complete() from completeSingleAttempt().
     */
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
