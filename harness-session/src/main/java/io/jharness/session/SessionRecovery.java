package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** Read-only recovery audit, shared by storage-independent callers. Never executes tools. */
public record SessionRecovery(
        TaskStatus status,
        List<String> uncertainCalls,
        List<String> unsettledCalls,
        List<String> pendingRequests,
        List<String> pendingTerminals) {
    public boolean ready() {
        return uncertainCalls.isEmpty()
                && unsettledCalls.isEmpty()
                && pendingRequests.isEmpty()
                && pendingTerminals.isEmpty();
    }

    /** Exact attempt identity, for manual verification when a step contains retries. */
    public static boolean isRequestPending(List<SessionEvent> events, String requestEventId) {
        return pendingRequestEvents(events).containsKey(requestEventId);
    }

    private static Map<String, String> pendingRequestEvents(List<SessionEvent> events) {
        Map<String, String> requests = new LinkedHashMap<>();
        for (var e : events) {
            if (e.type().equals("model.requested")) requests.put(e.eventId(), e.stepId());
            if (e.type().equals("assistant.message"))
                requests.values().removeIf(step -> Objects.equals(step, e.stepId()));
            if (e.type().equals("model.rejected") && "HTTP_429".equals(e.payload().get("reason")))
                requests.remove(e.payload().get("requestEventId"), e.stepId());
            if (e.type().equals("model.verified")) {
                if (e.payload().containsKey("sourceEvent"))
                    requests.remove(e.payload().get("sourceEvent"), e.payload().get("stepId"));
                else if (requests.values().stream()
                                .filter(step -> Objects.equals(step, e.payload().get("stepId")))
                                .count()
                        == 1)
                    requests.values()
                            .removeIf(step -> Objects.equals(step, e.payload().get("stepId")));
            }
        }
        return requests;
    }

    public static SessionRecovery inspect(List<SessionEvent> events) {
        Set<String> calls = new LinkedHashSet<>();
        Map<String, String> requests = pendingRequestEvents(events);
        for (var e : events) {
            if (e.type().equals("assistant.message")
                    && e.payload().get("tool_calls") instanceof List<?> list)
                for (Object call : list) calls.add((String) Values.object(call).get("id"));
            if (e.type().equals("tool.completed")) calls.remove(e.callId());
        }
        Map<String, String> terminalStates = new LinkedHashMap<>();
        for (var event : events) {
            if (event.type().equals("tool.completed")
                    && event.payload().get("value") instanceof Map<?, ?> raw) {
                var value = Values.object(raw);
                if (value.get("id") instanceof String id
                        && value.get("state") instanceof String state
                        && value.containsKey("pty")) terminalStates.put(id, state);
            }
            if (event.type().equals("terminal.snapshot")
                    && event.payload().get("tasks") instanceof List<?> tasks)
                for (Object raw : tasks) {
                    var value = Values.object(raw);
                    if (value.get("id") instanceof String id
                            && value.get("state") instanceof String state)
                        terminalStates.put(id, state);
                }
            if (event.type().equals("terminal.verified")
                    && event.payload().get("id") instanceof String id)
                terminalStates.put(id, "VERIFIED");
        }
        var projection = new EventSessionProjection(events);
        var uncertain = new LinkedHashSet<>(projection.uncertainCalls());
        var children = new LinkedHashSet<String>();
        for (var event : events) {
            if (event.type().equals("subagent.started"))
                children.add((String) event.payload().get("taskId"));
            if (event.type().equals("subagent.completed")
                    && Set.of("COMPLETED", "FAILED", "CANCELLED", "LIMIT_REACHED")
                            .contains(String.valueOf(event.payload().get("state"))))
                children.remove(event.payload().get("taskId"));
            if (event.type().equals("subagent.verified"))
                children.remove(event.payload().get("taskId"));
        }
        children.forEach(id -> uncertain.add("subagent:" + id));
        for (var event : events)
            if (event.type().equals("environment.closed")
                    && "UNKNOWN".equals(event.payload().get("status")))
                uncertain.add("environment:" + event.payload().get("environmentId"));
            else if (event.type().equals("environment.verified"))
                uncertain.remove("environment:" + event.payload().get("environmentId"));
        return new SessionRecovery(
                projection.taskStatus(),
                List.copyOf(uncertain),
                List.copyOf(calls),
                requests.values().stream().distinct().toList(),
                terminalStates.entrySet().stream()
                        .filter(e -> Set.of("RUNNING", "UNKNOWN").contains(e.getValue()))
                        .map(Map.Entry::getKey)
                        .toList());
    }
}
