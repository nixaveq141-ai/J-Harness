package io.jharness.api;

import java.time.Instant;
import java.util.*;

public final class EventSupport {
    private EventSupport() {}

    public static SessionEvent append(
            SessionWriter writer,
            EventBus bus,
            PluginScope scope,
            String type,
            EventLevel level,
            String turnId,
            String stepId,
            String callId,
            Map<String, Object> payload) {
        SessionEvent event =
                new SessionEvent(
                        UUID.randomUUID().toString(),
                        scope.sessionId(),
                        type,
                        Instant.now(),
                        1,
                        level,
                        level == EventLevel.SESSION ? null : turnId,
                        level == EventLevel.STEP || level == EventLevel.TOOL ? stepId : null,
                        level == EventLevel.TOOL ? callId : null,
                        payload);
        event = writer.appendPrepared(event);
        try {
            bus.publish(event);
        } catch (RuntimeException ex) {
            throw new HarnessException(
                    "EVENT_NOTIFICATION_FAILED",
                    "Event persisted but notification failed; do not replay execution",
                    ex);
        }
        return event;
    }

    public static void checkpoint(SessionWriter writer, CheckpointDecision decision) {
        Objects.requireNonNull(decision, "decision");
        switch (decision) {
            case MEMORY_ONLY -> {}
            case FLUSH -> writer.flush();
            case DURABLE_FLUSH -> {
                if (!writer.durable())
                    throw new HarnessException(
                            "DURABILITY_REQUIRED",
                            "This checkpoint requires a durable Session writer");
                writer.flush();
            }
        }
    }
}
