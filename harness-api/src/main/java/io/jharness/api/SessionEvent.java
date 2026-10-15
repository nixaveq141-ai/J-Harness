package io.jharness.api;

import java.time.Instant;
import java.util.*;

/** Immutable fact; association fields are validated by type and explicit level. */
public record SessionEvent(
        String eventId,
        String sessionId,
        String type,
        Instant timestamp,
        int schemaVersion,
        EventLevel level,
        String turnId,
        String stepId,
        String callId,
        Map<String, Object> payload) {
    public SessionEvent {
        eventId = Values.required(eventId, "eventId");
        sessionId = Values.required(sessionId, "sessionId");
        type = Values.required(type, "type");
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(level);
        if (schemaVersion < 1) throw new IllegalArgumentException("Invalid schemaVersion");
        EventLevel expected =
                switch (type) {
                    case "session.created", "checkpoint.failed" -> null;
                    case "turn.started", "turn.ended" -> EventLevel.TURN;
                    case "step.started",
                                    "step.ended",
                                    "model.requested",
                                    "assistant.message",
                                    "request.context",
                                    "budget.reserved" ->
                            EventLevel.STEP;
                    case "budget.settled" -> EventLevel.STEP;
                    case "tool.requested",
                                    "tool.intent",
                                    "tool.started",
                                    "tool.completed",
                                    "tool.verified",
                                    "approval.decided",
                                    "approval.requested" ->
                            EventLevel.TOOL;
                    default ->
                            type.startsWith("plugin.") || type.startsWith("config.")
                                    ? EventLevel.SESSION
                                    : null;
                };
        if (type.equals("session.created") && level != EventLevel.SESSION)
            throw new IllegalArgumentException("Session event level");
        if (expected != null && expected != level)
            throw new IllegalArgumentException("Event level does not match type");
        if (level != EventLevel.SESSION) turnId = Values.required(turnId, "turnId");
        if (level == EventLevel.STEP || level == EventLevel.TOOL)
            stepId = Values.required(stepId, "stepId");
        if (level == EventLevel.TOOL) callId = Values.required(callId, "callId");
        if (level == EventLevel.SESSION && (turnId != null || stepId != null || callId != null)
                || level == EventLevel.TURN && (stepId != null || callId != null)
                || level == EventLevel.STEP && callId != null)
            throw new IllegalArgumentException("Unexpected association fields");
        payload = Values.snapshot(payload);
    }

    public static SessionEvent session(String sessionId, String type, Map<String, Object> payload) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                sessionId,
                type,
                Instant.now(),
                1,
                EventLevel.SESSION,
                null,
                null,
                null,
                payload);
    }
}
