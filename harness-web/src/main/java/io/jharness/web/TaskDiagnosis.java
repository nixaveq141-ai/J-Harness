package io.jharness.web;

import io.jharness.api.*;
import io.jharness.session.SessionRecovery;
import java.util.*;

/** Read-only, bounded explanation of recorded facts. Never authorizes recovery or execution. */
public final class TaskDiagnosis {
    private TaskDiagnosis() {}

    public static Map<String, Object> inspect(List<SessionEvent> events) {
        return inspect(events, value -> value);
    }

    /** Redact full values before shortening them, so truncation cannot expose secret prefixes. */
    public static Map<String, Object> inspect(List<SessionEvent> events, DataSanitizer sanitizer) {
        var recovery = SessionRecovery.inspect(events);
        var requested = new HashMap<String, Map<String, Object>>();
        var decisions = new HashMap<String, Map<String, Object>>();
        var issues = new ArrayDeque<Map<String, Object>>();
        var turns = new LinkedHashSet<String>();
        Map<String, Object> configuration = Map.of();
        int issueCount = 0;
        for (var e : events) {
            if (e.turnId() != null && turns.size() < 64) turns.add(e.turnId());
            if (!Set.of(
                            "config.selected",
                            "tool.requested",
                            "approval.decided",
                            "tool.completed",
                            "turn.ended")
                    .contains(e.type())) continue;
            var p = sanitizer.sanitize(diagnosticPayload(e));
            if (e.type().equals("config.selected")) {
                var resolved = object(p.get("resolved"));
                var selection = object(p.get("selection"));
                var c = new LinkedHashMap<String, Object>();
                c.put("eventId", safeString(e.eventId(), sanitizer));
                copy(selection, c, "settingsRevision", "modelRoute", "resolvedSha256");
                copy(resolved, c, "policy", "durationSeconds", "requestTimeoutSeconds");
                configuration = c;
            }
            if (e.type().equals("tool.requested")) requested.put(e.callId(), p);
            if (e.type().equals("approval.decided")) decisions.put(e.callId(), p);
            boolean tool = e.type().equals("tool.completed");
            boolean ended = e.type().equals("turn.ended");
            String status = string(p.get("status"));
            if (!(tool && !status.equals("SUCCESS") || ended && !status.equals("COMPLETED")))
                continue;
            var value = object(p.get("value"));
            var error = object(p.get("error"));
            String code = string(tool ? error.get("code") : p.get("reason"));
            var row = new LinkedHashMap<String, Object>();
            row.put("eventId", safeString(e.eventId(), sanitizer));
            row.put("turnId", safeString(e.turnId(), sanitizer));
            row.put("stepId", safeString(e.stepId(), sanitizer));
            row.put("callId", safeString(e.callId(), sanitizer));
            row.put("operation", tool ? string(p.get("tool")) : "turn");
            row.put("status", status);
            row.put("code", code);
            row.put("category", category(status, code));
            // Pipeline wrapper entry is not proof that a command process was started.
            row.put("bodyEntered", p.get("bodyEntered") instanceof Boolean b ? b : "unknown");
            var decision = decisions.get(e.callId());
            if (decision != null) row.put("approvalReason", string(decision.get("reason")));
            row.put(
                    "commandStarted",
                    value.get("cleanupStatus") != null
                                    && value.get("cleanupStatus").equals("not_started")
                            ? "no"
                            : value.containsKey("sideEffectsMayHaveOccurred")
                                            && Boolean.TRUE.equals(
                                                    value.get("sideEffectsMayHaveOccurred"))
                                    ? "yes"
                                    : "unknown");
            row.put(
                    "effects",
                    Boolean.TRUE.equals(value.get("sideEffectsMayHaveOccurred"))
                                    || Boolean.TRUE.equals(value.get("sideEffectUncertain"))
                            ? "possible"
                            : "not_started".equals(value.get("cleanupStatus"))
                                            || Boolean.FALSE.equals(p.get("bodyEntered"))
                                    ? "not_started"
                                    : "READ_ONLY".equals(p.get("sideEffectLevel"))
                                            ? "read_only"
                                            : "unknown");
            copy(p, row, "durationMs", "sideEffectLevel");
            copy(
                    value,
                    row,
                    "cleanupStatus",
                    "containerCleanup",
                    "observedClean",
                    "timedOut",
                    "cancelled",
                    "sideEffectUncertain");
            if (value.get("durationMs") instanceof Number n) row.put("commandDurationMs", n);
            var args = object(requested.getOrDefault(e.callId(), Map.of()).get("arguments"));
            copy(args, row, "mode", "timeoutMs");
            if (value.get("mode") instanceof String mode && !mode.isBlank())
                row.put("mode", string(mode));
            if (issues.size() == 12) issues.removeFirst();
            issues.add(row);
            issueCount++;
        }
        var pending = new LinkedHashMap<String, Object>();
        pending.put("uncertainCalls", limited(recovery.uncertainCalls(), sanitizer));
        pending.put("unsettledCalls", limited(recovery.unsettledCalls(), sanitizer));
        pending.put("pendingRequests", limited(recovery.pendingRequests(), sanitizer));
        pending.put("pendingTerminals", limited(recovery.pendingTerminals(), sanitizer));
        var unsettledSources = new ArrayList<Map<String, Object>>();
        var sourceCalls = new HashSet<Object>();
        for (var event : events.reversed()) {
            if (!event.type().equals("assistant.message")
                    || !(event.payload().get("tool_calls") instanceof List<?> calls)) continue;
            for (var call : calls) {
                var id = object(call).get("id");
                if (!recovery.unsettledCalls().contains(id)
                        || unsettledSources.size() >= 64
                        || !sourceCalls.add(id)) continue;
                unsettledSources.add(
                        Map.of(
                                "callId",
                                safeString(id, sanitizer),
                                "eventId",
                                safeString(event.eventId(), sanitizer),
                                "turnId",
                                safeString(event.turnId(), sanitizer),
                                "stepId",
                                safeString(event.stepId(), sanitizer)));
            }
        }
        pending.put("unsettledSources", unsettledSources);
        pending.put(
                "limited",
                recovery.uncertainCalls().size() > 64
                        || recovery.unsettledCalls().size() > 64
                        || recovery.pendingRequests().size() > 64
                        || recovery.pendingTerminals().size() > 64);
        return Map.of(
                "schemaVersion",
                1,
                "source",
                "session-events",
                "eventCount",
                events.size(),
                "turnIds",
                limited(new ArrayList<>(turns), sanitizer),
                "configuration",
                configuration,
                "issues",
                List.copyOf(issues),
                "issuesLimited",
                issueCount > issues.size(),
                "pending",
                pending,
                "sdkFactsSettled",
                recovery.ready());
    }

    private static Map<String, Object> diagnosticPayload(SessionEvent event) {
        var source = event.payload();
        var out = new LinkedHashMap<String, Object>();
        out.putAll(
                project(
                        source,
                        "status",
                        "reason",
                        "tool",
                        "bodyEntered",
                        "durationMs",
                        "sideEffectLevel"));
        out.put("arguments", project(object(source.get("arguments")), "mode", "timeoutMs"));
        out.put(
                "value",
                project(
                        object(source.get("value")),
                        "cleanupStatus",
                        "containerCleanup",
                        "sideEffectsMayHaveOccurred",
                        "sideEffectUncertain",
                        "observedClean",
                        "timedOut",
                        "cancelled",
                        "durationMs",
                        "mode"));
        out.put("error", project(object(source.get("error")), "code"));
        out.put(
                "selection",
                project(
                        object(source.get("selection")),
                        "settingsRevision",
                        "modelRoute",
                        "resolvedSha256"));
        out.put(
                "resolved",
                project(
                        object(source.get("resolved")),
                        "policy",
                        "durationSeconds",
                        "requestTimeoutSeconds"));
        return out;
    }

    private static Map<String, Object> project(Map<String, Object> source, String... fields) {
        var out = new LinkedHashMap<String, Object>();
        for (String field : fields)
            if (source.containsKey(field)) out.put(field, source.get(field));
        return out;
    }

    private static String category(String status, String code) {
        if (status.equals("UNKNOWN") || code.equals("UNKNOWN_SIDE_EFFECT")) return "UNKNOWN";
        if (status.equals("DENIED") || code.contains("DENIED")) return "DENIED";
        if (status.equals("CANCELLED") || code.equals("CANCELLED")) return "CANCELLED";
        if (status.equals("TIMEOUT") || code.contains("TIMEOUT") || code.equals("TIME_LIMIT"))
            return "TIMEOUT";
        if (code.equals("SHELL_REQUIRED")
                || code.startsWith("INVALID_")
                || code.contains("ARGUMENT")
                || code.contains("SCHEMA")) return "VALIDATION";
        if (code.contains("HTTP")
                || code.contains("MODEL")
                || code.contains("NETWORK")
                || code.contains("LLM")
                || code.contains("CONNECTION")) return "MODEL_NETWORK";
        return status.equals("FAILED") ? "FAILED" : "UNKNOWN";
    }

    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? Values.object(value) : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String s ? s.substring(0, Math.min(256, s.length())) : "";
    }

    private static List<String> limited(List<String> values, DataSanitizer sanitizer) {
        return values.stream().limit(64).map(v -> safeString(v, sanitizer)).toList();
    }

    private static String safeString(Object value, DataSanitizer sanitizer) {
        return string(sanitizer.sanitize(Map.of("value", value == null ? "" : value)).get("value"));
    }

    private static void copy(
            Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object v = source.get(key);
            if (v instanceof String || v instanceof Number || v instanceof Boolean)
                target.put(key, v instanceof String ? string(v) : v);
        }
    }
}
