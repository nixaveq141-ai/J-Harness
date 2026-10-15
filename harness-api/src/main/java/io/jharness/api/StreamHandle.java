package io.jharness.api;

import java.util.function.Consumer;

public interface StreamHandle extends AutoCloseable {
    /** Cumulative display snapshots; tools are executable only in finalResponse after consume. */
    default ChatResponse finalResponse() {
        throw new HarnessException(
                "UNSUPPORTED", "Provider must expose a validated final stream response");
    }

    void consume(Consumer<ChatResponse> consumer);

    @Override
    void close();
}
