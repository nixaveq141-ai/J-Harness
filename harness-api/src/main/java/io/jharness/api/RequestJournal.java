package io.jharness.api;

import java.util.List;

/** Records the final model-visible envelope and reconstructs using a bounded event prefix. */
public interface RequestJournal {
    String record(
            ChatRequest request,
            String sessionId,
            String turnId,
            String stepId,
            SessionWriter writer,
            EventBus events);

    ChatRequest reconstruct(List<SessionEvent> events, String requestedEventId);
}
