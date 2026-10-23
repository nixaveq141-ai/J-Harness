package io.jharness.ecosystem;

import io.jharness.api.*;
import java.util.*;

/** One bounded cumulative snapshot, keyed by the actual prepared-request fact. */
final class ModelDisplayBuffer implements ModelDisplay {
    private final PluginScope scope;
    private final SessionReader reader;
    private final DataSanitizer sanitizer;
    private String text = "", sourceId = "", turnId = "", stepId = "";
    private boolean truncated;
    private boolean unavailable;

    ModelDisplayBuffer(PluginScope scope, SessionReader reader, DataSanitizer sanitizer) {
        this.scope = scope;
        this.reader = reader;
        this.sanitizer = sanitizer;
    }

    synchronized void reset() {
        text = "";
        sourceId = "";
        turnId = "";
        stepId = "";
        truncated = false;
        unavailable = false;
    }

    synchronized void accept(ModelDisplay.Update update) {
        try {
            acceptSnapshot(update);
        } catch (RuntimeException failure) {
            text = "";
            unavailable = true;
        }
    }

    private void acceptSnapshot(ModelDisplay.Update update) {
        String value = update.text();
        truncated |= update.truncated();
        if (sourceId.isEmpty()) {
            var request =
                    reader.events().reversed().stream()
                            .filter(e -> e.type().equals("model.requested"))
                            .findFirst();
            if (request.isEmpty()) return;
            var e = request.get();
            sourceId = e.eventId();
            turnId = e.turnId();
            stepId = e.stepId();
        }
        String clean = sanitizer.streamingText(value);
        truncated |= value.length() > 65536 || clean.length() > 65536;
        int end = Math.min(65536, clean.length());
        if (end > 0 && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        text = clean.substring(0, end);
    }

    public synchronized Map<String, Object> snapshot() {
        // A fact-reader failure during owner settlement must remain visible to the host,
        // which retains UNKNOWN ownership. Only a failed presentation callback degrades.
        if (!unavailable) return currentSnapshot();
        return Map.of(
                "sessionId",
                scope.sessionId(),
                "source",
                "temporary-model-stream",
                "sourceEvent",
                sourceId,
                "state",
                "UNAVAILABLE",
                "text",
                "",
                "truncated",
                truncated,
                "diagnostic",
                "DISPLAY_UNAVAILABLE");
    }

    private Map<String, Object> currentSnapshot() {
        String state = sourceId.isEmpty() ? "IDLE" : "STREAMING";
        boolean after = false;
        for (var e : reader.events()) {
            if (e.eventId().equals(sourceId)) {
                after = true;
                continue;
            }
            if (!after) continue;
            if (e.type().equals("assistant.message") && Objects.equals(e.stepId(), stepId))
                state = "PERSISTED";
            if (e.type().equals("model.observed")
                    && sourceId.equals(e.payload().get("sourceEvent"))
                    && !"SUCCESS".equals(e.payload().get("outcome"))) state = "INTERRUPTED";
            if (e.type().equals("turn.ended")
                    && Objects.equals(e.turnId(), turnId)
                    && state.equals("STREAMING")) state = "INTERRUPTED";
        }
        return Map.of(
                "sessionId",
                scope.sessionId(),
                "source",
                "temporary-model-stream",
                "sourceEvent",
                sourceId,
                "turnId",
                turnId,
                "stepId",
                stepId,
                "state",
                state,
                "text",
                text,
                "truncated",
                truncated);
    }
}
