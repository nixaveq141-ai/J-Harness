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
    private long revision, updateTicket;

    ModelDisplayBuffer(PluginScope scope, SessionReader reader, DataSanitizer sanitizer) {
        this.scope = scope;
        this.reader = reader;
        this.sanitizer = sanitizer;
    }

    synchronized void reset() {
        revision++;
        updateTicket++;
        text = "";
        sourceId = "";
        turnId = "";
        stepId = "";
        truncated = false;
        unavailable = false;
    }

    void accept(ModelDisplay.Update update) {
        long ticket;
        boolean needsSource;
        synchronized (this) {
            ticket = ++updateTicket;
            needsSource = sourceId.isEmpty();
        }
        try {
            // Neither durable fact reads nor host sanitizer callbacks hold the display monitor.
            var request =
                    needsSource
                            ? reader.events().reversed().stream()
                                    .filter(e -> e.type().equals("model.requested"))
                                    .findFirst()
                            : Optional.<SessionEvent>empty();
            String value = update.text();
            String clean = sanitizer.streamingText(value);
            int end = Math.min(65536, clean.length());
            if (end > 0 && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
            synchronized (this) {
                if (ticket != updateTicket) return;
                revision++;
                truncated |= update.truncated() || value.length() > 65536 || clean.length() > 65536;
                if (needsSource) {
                    if (request.isEmpty()) return;
                    var event = request.get();
                    sourceId = event.eventId();
                    turnId = event.turnId();
                    stepId = event.stepId();
                }
                text = clean.substring(0, end);
            }
        } catch (RuntimeException failure) {
            synchronized (this) {
                if (ticket != updateTicket) return;
                revision++;
                text = "";
                unavailable = true;
            }
        }
    }

    public Map<String, Object> snapshot() {
        // A fact-reader failure during owner settlement must remain visible to the host,
        // which retains UNKNOWN ownership. Only a failed presentation callback degrades.
        View captured;
        synchronized (this) {
            captured = view();
            if (captured.unavailable) return unavailableSnapshot(captured);
        }
        String state = captured.sourceId.isEmpty() ? "IDLE" : "STREAMING";
        boolean after = false;
        for (var e : reader.events()) {
            if (e.eventId().equals(captured.sourceId)) {
                after = true;
                continue;
            }
            if (!after) continue;
            if (e.type().equals("assistant.message") && Objects.equals(e.stepId(), captured.stepId))
                state = "PERSISTED";
            if (e.type().equals("model.observed")
                    && captured.sourceId.equals(e.payload().get("sourceEvent"))
                    && !"SUCCESS".equals(e.payload().get("outcome"))) state = "INTERRUPTED";
            if (e.type().equals("turn.ended")
                    && Objects.equals(e.turnId(), captured.turnId)
                    && state.equals("STREAMING")) state = "INTERRUPTED";
        }
        synchronized (this) {
            if (revision != captured.revision) {
                captured = view();
                if (captured.unavailable) return unavailableSnapshot(captured);
                // Facts read for an earlier generation cannot settle the current display.
                state = captured.sourceId.isEmpty() ? "IDLE" : "STREAMING";
            }
            return snapshot(captured, state);
        }
    }

    private View view() {
        return new View(revision, text, sourceId, turnId, stepId, truncated, unavailable);
    }

    private record View(
            long revision,
            String text,
            String sourceId,
            String turnId,
            String stepId,
            boolean truncated,
            boolean unavailable) {}

    private Map<String, Object> unavailableSnapshot(View view) {
        return Map.of(
                "sessionId",
                scope.sessionId(),
                "source",
                "temporary-model-stream",
                "sourceEvent",
                view.sourceId,
                "state",
                "UNAVAILABLE",
                "text",
                "",
                "truncated",
                view.truncated,
                "diagnostic",
                "DISPLAY_UNAVAILABLE");
    }

    private Map<String, Object> snapshot(View view, String state) {
        return Map.of(
                "sessionId",
                scope.sessionId(),
                "source",
                "temporary-model-stream",
                "sourceEvent",
                view.sourceId,
                "turnId",
                view.turnId,
                "stepId",
                view.stepId,
                "state",
                state,
                "text",
                view.text,
                "truncated",
                view.truncated);
    }
}
