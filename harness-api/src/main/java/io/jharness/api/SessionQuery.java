package io.jharness.api;

import java.time.Instant;
import java.util.List;

/** Inclusive time range, exact type/tool/status filters and bounded event pagination. */
public record SessionQuery(
        Instant after,
        Instant before,
        String type,
        String tool,
        String status,
        int offset,
        int limit) {
    public SessionQuery {
        if (offset < 0
                || limit < 1
                || limit > 1000
                || after != null && before != null && after.isAfter(before))
            throw new HarnessException("INVALID_QUERY", "Invalid range or page (limit 1..1000)");
    }

    public List<SessionEvent> apply(List<SessionEvent> events) {
        java.util.Objects.requireNonNull(events, "events");
        events.forEach(event -> java.util.Objects.requireNonNull(event, "events element"));
        return events.stream()
                .filter(e -> after == null || !e.timestamp().isBefore(after))
                .filter(e -> before == null || !e.timestamp().isAfter(before))
                .filter(e -> type == null || type.equals(e.type()))
                .filter(e -> tool == null || tool.equals(e.payload().get("tool")))
                .filter(e -> status == null || status.equals(e.payload().get("status")))
                .skip(offset)
                .limit(limit)
                .toList();
    }
}
