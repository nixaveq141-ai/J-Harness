package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** A final envelope fact plus a referring request marker, always replayed from a prefix. */
public final class EventRequestJournal implements RequestJournal {
    @Override
    public String record(
            ChatRequest request,
            String sessionId,
            String turnId,
            String stepId,
            SessionWriter writer,
            EventBus events) {
        PluginScope scope = new PluginScope(sessionId, "journal");
        SessionEvent context =
                EventSupport.append(
                        writer,
                        events,
                        scope,
                        "request.context",
                        EventLevel.STEP,
                        turnId,
                        stepId,
                        null,
                        Map.of(
                                "messages",
                                request.messages(),
                                "tools",
                                request.tools(),
                                "parameters",
                                request.parameters(),
                                "reason",
                                "composed-intercepted-prepared"));
        return EventSupport.append(
                        writer,
                        events,
                        scope,
                        "model.requested",
                        EventLevel.STEP,
                        turnId,
                        stepId,
                        null,
                        Map.of("contextEventId", context.eventId()))
                .eventId();
    }

    @Override
    public ChatRequest reconstruct(List<SessionEvent> events, String requestedEventId) {
        Map<String, SessionEvent> contexts = new HashMap<>();
        Set<String> seen = new HashSet<>();
        String sessionId = null;
        for (SessionEvent event : events) {
            if (sessionId == null) sessionId = event.sessionId();
            if (!sessionId.equals(event.sessionId()))
                throw new HarnessException("SESSION_MISMATCH", "Mixed request sessions");
            if (event.schemaVersion() != 1)
                throw new HarnessException(
                        "FUTURE_VERSION_READ_ONLY", "Unsupported request schema");
            if (!seen.add(event.eventId()))
                throw new HarnessException("DUPLICATE_EVENT", "Duplicate request event");
            if (event.type().equals("request.context")) contexts.put(event.eventId(), event);
            if (!event.eventId().equals(requestedEventId)) continue;
            if (!event.type().equals("model.requested"))
                throw new HarnessException("INVALID_EVENT", "Target is not a model request");
            Object id = event.payload().get("contextEventId");
            SessionEvent context = id instanceof String text ? contexts.get(text) : null;
            if (context == null
                    || !context.type().equals("request.context")
                    || !Objects.equals(context.turnId(), event.turnId())
                    || !Objects.equals(context.stepId(), event.stepId()))
                throw new HarnessException(
                        "REQUEST_CONTEXT_MISSING",
                        "Request does not refer to a preceding matching context");
            return fromPayload(context.payload());
        }
        throw new HarnessException("REQUEST_NOT_FOUND", "Requested event not found");
    }

    public static ChatRequest fromPayload(Map<String, Object> payload) {
        if (!(payload.get("messages") instanceof List<?> messages)
                || !(payload.get("tools") instanceof List<?> tools))
            throw new HarnessException("INVALID_EVENT", "Missing request messages or tools");
        try {
            return new ChatRequest(
                    messages.stream().map(Values::object).toList(),
                    tools.stream().map(Values::object).toList(),
                    Values.object(payload.get("parameters")));
        } catch (RuntimeException ex) {
            throw new HarnessException("INVALID_EVENT", "Malformed request envelope", ex);
        }
    }
}
