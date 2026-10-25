package io.jharness.session;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;

/** No index: one bounded cold log at a time; errors are returned per session. */
public final class SessionCatalog {
    private SessionCatalog() {}

    public static List<Map<String, Object>> query(
            SessionRepository repo,
            String workspace,
            String model,
            Instant after,
            Instant before,
            String tool,
            String status,
            int offset,
            int limit) {
        new SessionQuery(after, before, null, tool, status, offset, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        int matched = 0;
        for (String id : repo.list()) {
            try {
                var events = repo.openReader(id).events();
                if (workspace != null
                        && events.stream()
                                .noneMatch(
                                        e ->
                                                e.type().equals("session.created")
                                                                && workspace.equals(
                                                                        e.payload()
                                                                                .get("workspace"))
                                                        || e.type().equals("sdk.created")
                                                                && workspace.equals(
                                                                        Values.object(
                                                                                        e.payload()
                                                                                                .getOrDefault(
                                                                                                        "binding",
                                                                                                        Map
                                                                                                                .of()))
                                                                                .get(
                                                                                        "workspaceBinding"))))
                    continue;
                if (model != null
                        && events.stream()
                                .noneMatch(
                                        e ->
                                                e.type().equals("request.context")
                                                        && model.equals(
                                                                Values.object(
                                                                                e.payload()
                                                                                        .get(
                                                                                                "parameters"))
                                                                        .get("model")))) continue;
                if ((after != null || before != null || tool != null)
                        && new SessionQuery(after, before, null, tool, null, 0, 1)
                                .apply(events)
                                .isEmpty()) continue;
                var recovery = SessionRecovery.inspect(events);
                if (status != null && !status.equals(recovery.status().name())) continue;
                if (matched++ < offset) continue;
                result.add(
                        Map.of(
                                "session",
                                id,
                                "status",
                                recovery.status().name(),
                                "eventCount",
                                events.size(),
                                "ready",
                                recovery.ready(),
                                "archived",
                                SessionLifecycle.archived(events)));
            } catch (RuntimeException ex) {
                if (workspace == null
                        && model == null
                        && after == null
                        && before == null
                        && tool == null
                        && status == null
                        && matched++ >= offset)
                    result.add(
                            Map.of(
                                    "session",
                                    id,
                                    "diagnostic",
                                    ex instanceof HarnessException h ? h.code() : "INVALID_EVENT",
                                    "ready",
                                    false));
            }
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }
}
