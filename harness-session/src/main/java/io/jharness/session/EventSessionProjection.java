package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** Deterministic snapshot projection. Request composition/reconstruction is implemented in M1. */
public final class EventSessionProjection implements SessionProjection {
    private final List<Map<String, Object>> messages;
    private final List<SessionEvent> tools;
    private final List<SessionEvent> approvals;
    private final TaskStatus status;
    private final String latestTurn;

    public EventSessionProjection(List<SessionEvent> events) {
        List<Map<String, Object>> messageList = new ArrayList<>();
        List<SessionEvent> toolList = new ArrayList<>();
        List<SessionEvent> approvalList = new ArrayList<>();
        TaskStatus current = TaskStatus.CREATED;
        String sessionId = null;
        String turn = null;
        Set<String> ids = new HashSet<>();
        Set<String> requestedApprovals = new HashSet<>();
        var referencedContexts = new HashSet<String>();
        var contexts = new HashMap<String, SessionEvent>();
        for (var event : events) {
            if (event.type().equals("request.context")) contexts.put(event.eventId(), event);
            if (event.type().equals("model.requested")
                    && !(event.payload().get("contextEventId") instanceof String)
                    && contexts.values().stream()
                            .anyMatch(
                                    c ->
                                            c.sessionId().equals(event.sessionId())
                                                    && Objects.equals(c.turnId(), event.turnId())
                                                    && Objects.equals(c.stepId(), event.stepId())))
                throw new HarnessException(
                        "REQUEST_CONTEXT_MISSING",
                        "Request omits its matching final context reference");
            if (event.type().equals("model.requested")
                    && event.payload().get("contextEventId") instanceof String id) {
                var context = contexts.get(id);
                if (context == null
                        || !context.sessionId().equals(event.sessionId())
                        || !Objects.equals(context.turnId(), event.turnId())
                        || !Objects.equals(context.stepId(), event.stepId()))
                    throw new HarnessException(
                            "REQUEST_CONTEXT_MISSING", "Unassociated request context");
                referencedContexts.add(id);
            }
        }
        for (SessionEvent event : List.copyOf(events)) {
            if (sessionId == null) sessionId = event.sessionId();
            if (!sessionId.equals(event.sessionId()))
                throw new HarnessException("SESSION_MISMATCH", "Mixed sessions in projection");
            if (event.schemaVersion() != 1)
                throw new HarnessException("FUTURE_VERSION", "Unsupported event schema");
            if (!ids.add(event.eventId()))
                throw new HarnessException("DUPLICATE_EVENT", "Duplicate event in projection");
            switch (event.type()) {
                case "user.message", "assistant.message", "context.injected" -> {
                    if (event.type().equals("context.injected")
                            && (!(event.payload().get("role") instanceof String role)
                                    || !Set.of("user", "assistant", "system", "tool")
                                            .contains(role)))
                        throw new HarnessException(
                                "INVALID_EVENT", "Message requires a valid role");
                    messageList.add(event.payload());
                }
                case "sdk.input.applied" -> {
                    if (event.payload().containsKey("userMessage")) {
                        var message = Values.object(event.payload().get("userMessage"));
                        if (!"user".equals(message.get("role"))
                                || !(message.get("content") instanceof String))
                            throw new HarnessException("INVALID_EVENT", "Invalid atomic SDK input");
                        messageList.add(message);
                    }
                }
                case "system.message" -> {
                    messageList.removeIf(message -> "system".equals(message.get("role")));
                    messageList.addFirst(event.payload());
                }
                case "request.context" -> {
                    if (!referencedContexts.contains(event.eventId())) break;
                    messageList.clear();
                    messageList.addAll(EventRequestJournal.fromPayload(event.payload()).messages());
                }
                case "turn.started", "step.started" -> {
                    current = TaskStatus.RUNNING;
                    if (event.type().equals("turn.started")) turn = event.turnId();
                }
                case "approval.requested" -> {
                    requestedApprovals.add(event.callId());
                    current = TaskStatus.WAITING_APPROVAL;
                }
                case "approval.decided" -> {
                    approvalList.add(event);
                    if (requestedApprovals.remove(event.callId())
                            || toolList.stream()
                                    .anyMatch(
                                            e ->
                                                    e.type().equals("tool.requested")
                                                            && Objects.equals(
                                                                    e.callId(), event.callId())))
                        current = TaskStatus.RUNNING;
                }
                case "tool.requested",
                        "tool.intent",
                        "tool.started",
                        "tool.completed",
                        "tool.verified" -> {
                    toolList.add(event);
                    if (event.type().equals("tool.started")) current = TaskStatus.EXECUTING_TOOL;
                    if (event.type().equals("tool.completed")) current = TaskStatus.RUNNING;
                    if (event.type().equals("tool.completed")
                            && event.payload().get("content") instanceof String content)
                        messageList.add(
                                Map.of(
                                        "role",
                                        "tool",
                                        "tool_call_id",
                                        event.callId(),
                                        "content",
                                        content));
                    if (event.type().equals("tool.verified"))
                        messageList.add(
                                Map.of(
                                        "role",
                                        "user",
                                        "content",
                                        "Operator verification of call "
                                                + event.callId()
                                                + ": "
                                                + verificationSummary(event.payload())));
                }
                case "turn.ended" -> {
                    Object value = event.payload().get("status");
                    if (!(value instanceof String text))
                        throw new HarnessException("INVALID_EVENT", "turn.ended requires status");
                    try {
                        current = TaskStatus.valueOf(text);
                    } catch (IllegalArgumentException ex) {
                        throw new HarnessException("INVALID_EVENT", "Unknown task status", ex);
                    }
                    if (current != TaskStatus.COMPLETED
                            && current != TaskStatus.FAILED
                            && current != TaskStatus.CANCELLED
                            && current != TaskStatus.LIMIT_REACHED)
                        throw new HarnessException("INVALID_EVENT", "turn.ended must be terminal");
                }
                default -> {
                    /* Unknown extension facts do not mutate the base projection. */
                }
            }
        }
        messages = List.copyOf(messageList);
        tools = List.copyOf(toolList);
        approvals = List.copyOf(approvalList);
        status = current;
        latestTurn = turn;
    }

    @Override
    public List<Map<String, Object>> messages() {
        return messages;
    }

    @Override
    public Optional<String> latestTurnId() {
        return Optional.ofNullable(latestTurn);
    }

    @Override
    public List<SessionEvent> toolHistory() {
        return tools;
    }

    @Override
    public List<SessionEvent> approvals() {
        return approvals;
    }

    @Override
    public TaskStatus taskStatus() {
        return status;
    }

    private static String verificationSummary(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        for (String key : List.of("outcome", "reason")) {
            Object value = payload.get(key);
            if (value instanceof String message)
                text.append(key)
                        .append("=")
                        .append(message.length())
                        .append(":")
                        .append(message)
                        .append(";");
        }
        return text.toString();
    }
}
