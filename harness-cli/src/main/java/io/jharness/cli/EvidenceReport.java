package io.jharness.cli;

import io.jharness.api.*;
import java.util.*;

public final class EvidenceReport {
    private EvidenceReport() {}

    public static Map<String, Object> build(
            String goal, List<SessionEvent> events, DataSanitizer sanitizer) {
        List<Map<String, Object>> changes = new ArrayList<>(),
                commands = new ArrayList<>(),
                partialChanges = new ArrayList<>(),
                issues = new ArrayList<>(),
                previews = new ArrayList<>();
        String narration = "",
                status = "INCOMPLETE",
                reason = "NO_TERMINAL_EVENT",
                currency = "UNKNOWN",
                cost = "UNKNOWN";
        String latestTurn =
                events.stream()
                        .filter(e -> e.type().equals("turn.started"))
                        .map(SessionEvent::turnId)
                        .reduce((a, b) -> b)
                        .orElse(null);
        Set<String> currentRequests = new HashSet<>();
        for (var event : events)
            if (event.type().equals("model.requested")
                    && Objects.equals(latestTurn, event.turnId()))
                currentRequests.add(event.eventId());
        List<Map<String, Object>> models = new ArrayList<>(), terminals = new ArrayList<>();
        for (var event : events) {
            if (event.type().equals("model.observed")
                    && currentRequests.contains(event.payload().get("sourceEvent")))
                models.add(reference(event, event.payload()));
            if (event.type().equals("terminal.snapshot")) {
                terminals.clear();
                terminals.add(reference(event, event.payload()));
            }
        }
        for (var e : events) {
            if (latestTurn != null
                    && e.level() != EventLevel.SESSION
                    && !latestTurn.equals(e.turnId())) continue;
            if (e.type().equals("assistant.message")
                    && e.payload().get("content") instanceof String text) narration = text;
            if (e.type().equals("turn.ended")) {
                status = factText(e.payload().get("status"));
                reason = factText(e.payload().get("reason"));
                currency = factText(e.payload().get("currency"));
                cost =
                        factText(
                                e.payload()
                                        .getOrDefault(
                                                "reservedApiCost",
                                                e.payload().get("reservedApiCostUsd")));
            }
            if (e.type().equals("tool.preview")) previews.add(reference(e, e.payload()));
            if (!e.type().equals("tool.completed")) continue;
            var value = Values.object(e.payload().getOrDefault("value", Map.of()));
            var display = Values.object(e.payload().getOrDefault("display", Map.of()));
            if ("execute_command".equals(e.payload().get("tool")) && value.containsKey("argv"))
                commands.add(reference(e, commandSummary(value)));
            if ("SUCCESS".equals(e.payload().get("status"))
                    && "WORKSPACE_WRITE".equals(e.payload().get("sideEffectLevel"))
                    && display.containsKey("path")
                    && !Objects.equals(display.get("beforeVersion"), display.get("afterVersion"))) {
                var change = new LinkedHashMap<>(display);
                for (String key :
                        List.of("diff", "artifactRef", "artifactSha256", "outputTruncated"))
                    if (value.containsKey(key)) change.put(key, value.get(key));
                changes.add(reference(e, change));
            }
            if ("WORKSPACE_WRITE".equals(e.payload().get("sideEffectLevel"))
                    && value.getOrDefault("changes", display.get("changes"))
                            instanceof List<?> entries)
                for (Object change : entries)
                    if (!Objects.equals(
                            Values.object(change).get("beforeVersion"),
                            Values.object(change).get("afterVersion")))
                        ("SUCCESS".equals(e.payload().get("status")) ? changes : partialChanges)
                                .add(reference(e, Values.object(change)));
            if (!"SUCCESS".equals(e.payload().get("status")))
                issues.add(
                        reference(
                                e,
                                Map.of(
                                        "status",
                                        factText(e.payload().get("status")),
                                        "error",
                                        e.payload().get("error") instanceof Map<?, ?> error
                                                ? error
                                                : Map.of("code", "UNVERIFIED_EVENT"))));
        }
        return sanitizer.sanitize(
                Map.of(
                        "goal",
                        goal,
                        "status",
                        status,
                        "reason",
                        reason,
                        "recordedFacts",
                        Map.of(
                                "changes",
                                changes,
                                "commands",
                                commands,
                                "partialOrUncertainChanges",
                                partialChanges,
                                "issues",
                                issues,
                                "previews",
                                previews,
                                "models",
                                models,
                                "terminals",
                                terminals),
                        "modelExplanation",
                        safeNarration(narration, sanitizer),
                        "unresolvedProblems",
                        status.equals("COMPLETED")
                                ? List.of(
                                        "No failed declared acceptance criterion; historical issues remain in recordedFacts")
                                : List.of(reason),
                        "unexecutedValidation",
                        commands.isEmpty()
                                ? List.of(
                                        "No command execution is recorded; model prose is not test evidence")
                                : List.of(
                                        "Only the recorded commands were executed; other checks are unverified"),
                        "cost",
                        Map.of(
                                "currency",
                                currency,
                                "upperBound",
                                cost,
                                "billing",
                                "provider usage at configured worst-case rates, or retained reservation; not an invoice")));
    }

    private static String safeNarration(String narration, DataSanitizer sanitizer) {
        String clean = (String) sanitizer.sanitize(Map.of("text", narration)).get("text");
        if (clean.length() <= 16384) return clean;
        int end = Character.isHighSurrogate(clean.charAt(16383)) ? 16383 : 16384;
        return clean.substring(0, end) + "[TRUNCATED]";
    }

    private static Map<String, Object> reference(SessionEvent event, Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>(data);
        out.put("eventId", event.eventId());
        out.put("callId", event.callId());
        out.put("turnId", event.turnId());
        out.put("timestamp", event.timestamp().toString());
        if (event.type().equals("terminal.snapshot"))
            out.put("scope", "historical-last-known-snapshot");
        if (event.payload().containsKey("status"))
            out.put("toolStatus", event.payload().get("status"));
        return out;
    }

    private static String factText(Object value) {
        return value instanceof String s && !s.isBlank() ? s : "UNKNOWN";
    }

    private static Map<String, Object> commandSummary(Map<String, Object> value) {
        Map<String, Object> summary = new LinkedHashMap<>(value);
        for (String key : List.of("stdout", "stderr"))
            if (summary.get(key) instanceof String text && text.length() > 2048) {
                summary.put(key, text.substring(0, 2048));
                summary.put(key + "ReportTruncated", true);
            }
        return summary;
    }

    public static String markdown(Map<String, Object> report) {
        var facts = Values.object(report.get("recordedFacts"));
        return "# J-Harness 交付报告\n\n目标："
                + report.get("goal")
                + "\n\n状态："
                + report.get("status")
                + "；原因："
                + report.get("reason")
                + "\n\n## 记录事实（带 eventId / callId）\n\n```json\n"
                + Json.text(facts)
                + "\n```\n\n## 模型说明（不作为执行证据）\n\n"
                + report.get("modelExplanation")
                + "\n\n## 未执行的验证\n\n"
                + report.get("unexecutedValidation")
                + "\n\n## 尚未解决的问题\n\n"
                + report.get("unresolvedProblems")
                + "\n\n费用上界："
                + report.get("cost")
                + "\n";
    }
}
