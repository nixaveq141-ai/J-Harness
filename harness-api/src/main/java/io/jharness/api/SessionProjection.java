package io.jharness.api;

import java.util.*;

public interface SessionProjection {
    default Optional<String> latestTurnId() {
        return Optional.empty();
    }

    default List<String> uncertainCalls() {
        java.util.Set<String> pending = new java.util.LinkedHashSet<>();
        for (SessionEvent event : toolHistory()) {
            if ((event.type().equals("tool.intent") || event.type().equals("tool.started"))
                    && !"READ_ONLY".equals(event.payload().get("sideEffectLevel")))
                pending.add(event.callId());
            if (event.type().equals("tool.completed")) {
                if ("UNKNOWN".equals(event.payload().get("status"))
                        || event.payload().get("value") instanceof Map<?, ?> value
                                && Boolean.TRUE.equals(value.get("sideEffectUncertain")))
                    pending.add(event.callId());
                else pending.remove(event.callId());
            }
            if (event.type().equals("tool.verified")) pending.remove(event.callId());
        }
        return List.copyOf(pending);
    }

    List<Map<String, Object>> messages();

    List<SessionEvent> toolHistory();

    List<SessionEvent> approvals();

    TaskStatus taskStatus();
}
