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
    private long updated;
    private int inputLength;

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
        updated = 0;
        inputLength = 0;
    }

    synchronized void accept(ModelDisplay.Update update) {
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
        long now = System.nanoTime();
        if (!text.isEmpty() && now - updated < 50000000L && value.length() - inputLength < 16384)
            return;
        updated = now;
        inputLength = value.length();
        String clean = sanitizer.streamingText(value);
        truncated |= value.length() > 65536 || clean.length() > 65536;
        int end = Math.min(65536, clean.length());
        if (end > 0 && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        text = clean.substring(0, end);
    }

    public synchronized Map<String, Object> snapshot() {
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
