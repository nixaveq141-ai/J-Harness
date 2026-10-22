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
            var p = sanitizer.sanitize(e.payload());
            if (e.turnId() != null) turns.add(e.turnId());
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
                    "not_started".equals(value.get("cleanupStatus"))
                                    || Boolean.FALSE.equals(p.get("bodyEntered"))
                            ? "not_started"
                            : Boolean.TRUE.equals(value.get("sideEffectsMayHaveOccurred"))
                                            || Boolean.TRUE.equals(value.get("sideEffectUncertain"))
                                    ? "possible"
                                    : "READ_ONLY".equals(p.get("sideEffectLevel"))
                                            ? "read_only"
                                            : "unknown");
            copy(p, row, "durationMs", "sideEffectLevel");
            copy(
                    value,
                    row,
                    "cleanupStatus",
                    "observedClean",
                    "timedOut",
                    "cancelled",
                    "sideEffectUncertain");
            if (value.get("durationMs") instanceof Number n) row.put("commandDurationMs", n);
            var args = object(requested.getOrDefault(e.callId(), Map.of()).get("arguments"));
            copy(args, row, "mode", "timeoutMs");
            if (value.get("mode") instanceof String mode) row.put("mode", mode);
            if (issues.size() == 12) issues.removeFirst();
            issues.add(row);
            issueCount++;
        }
        var pending = new LinkedHashMap<String, Object>();
        pending.put("uncertainCalls", limited(recovery.uncertainCalls(), sanitizer));
        pending.put("unsettledCalls", limited(recovery.unsettledCalls(), sanitizer));
        pending.put("pendingRequests", limited(recovery.pendingRequests(), sanitizer));
        pending.put("pendingTerminals", limited(recovery.pendingTerminals(), sanitizer));
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
        return "FAILED";
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
