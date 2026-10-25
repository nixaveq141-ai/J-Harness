package io.jharness.cli;

import io.jharness.api.*;
import java.util.*;

public final class EvidenceReport {
    private EvidenceReport() {}

    public static Map<String, Object> build(
            String goal, List<SessionEvent> events, DataSanitizer sanitizer) {
        var budget = new ReportProjection(131072, 8192, 4096);
        FactList changes = new FactList(budget, sanitizer),
                commands = new FactList(budget, sanitizer),
                partialChanges = new FactList(budget, sanitizer),
                issues = new FactList(budget, sanitizer),
                previews = new FactList(budget, sanitizer);
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
        FactList models = new FactList(budget, sanitizer),
                terminals = new FactList(budget, sanitizer);
        SessionEvent lastTerminal = null;
        for (var event : events) {
            if (event.type().equals("model.observed")
                    && currentRequests.contains(event.payload().get("sourceEvent")))
                models.add(reference(event, event.payload()));
            if (event.type().equals("terminal.snapshot")) {
                lastTerminal = event;
            }
        }
        if (lastTerminal != null) terminals.add(reference(lastTerminal, lastTerminal.payload()));
        for (var e : events) {
            if (latestTurn != null
                    && e.level() != EventLevel.SESSION
                    && !latestTurn.equals(e.turnId())) continue;
            if (e.type().equals("assistant.message")
                    && e.payload().get("content") instanceof String text) narration = text;
            if (e.type().equals("turn.ended")) {
                status = safeFact(e.payload().get("status"), sanitizer);
                reason = safeFact(e.payload().get("reason"), sanitizer);
                currency = safeFact(e.payload().get("currency"), sanitizer);
                cost =
                        costText(
                                e.payload()
                                        .getOrDefault(
                                                "reservedApiCost",
                                                e.payload().get("reservedApiCostUsd")));
            }
            if (e.type().equals("tool.preview")) previews.add(reference(e, e.payload()));
            if (!e.type().equals("tool.completed")) continue;
            var value = objectOrEmpty(e.payload().get("value"));
            var display = objectOrEmpty(e.payload().get("display"));
            if ("execute_command".equals(e.payload().get("tool")) && value.containsKey("argv"))
                commands.add(reference(e, value));
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
                            objectOrEmpty(change).get("beforeVersion"),
                            objectOrEmpty(change).get("afterVersion")))
                        ("SUCCESS".equals(e.payload().get("status")) ? changes : partialChanges)
                                .add(reference(e, objectOrEmpty(change)));
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
        var report =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "goal",
                                ReportProjection.text(
                                        sanitizer.sanitize(Map.of("text", goal)).get("text")
                                                        instanceof String safeGoal
                                                ? safeGoal
                                                : "[WITHHELD]",
                                        4096),
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
        var lists =
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
                        terminals);
        var coverage = new LinkedHashMap<String, Object>();
        lists.forEach(
                (name, list) ->
                        coverage.put(
                                name,
                                Map.of(
                                        "observed",
                                        list.observed,
                                        "included",
                                        list.size(),
                                        "omitted",
                                        list.observed - list.size())));
        report.put(
                "reportCoverage",
                Map.of(
                        "complete",
                        !budget.truncated()
                                && goal.length() <= 4096
                                && narration.length() <= 16384
                                && lists.values().stream().allMatch(l -> l.observed == l.size()),
                        "sections",
                        coverage,
                        "source",
                        "Original session event log; retained entries carry eventId, callId, turnId and timestamp. Omitted entries require reading that log.",
                        "maxEntriesPerSection",
                        128,
                        "payloadCharacterBudget",
                        131072));
        return sanitizer.sanitize(report);
    }

    private static final class FactList extends ArrayList<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private final transient ReportProjection budget;
        private final transient DataSanitizer sanitizer;
        private int observed;

        FactList(ReportProjection budget, DataSanitizer sanitizer) {
            this.budget = budget;
            this.sanitizer = sanitizer;
        }

        @Override
        public boolean add(Map<String, Object> value) {
            observed++;
            if (size() >= 128) return false;
            var safe = sanitizer.sanitize(value);
            var projected = new LinkedHashMap<>(objectOrEmpty(budget.project(safe)));
            for (String key :
                    List.of("eventId", "callId", "turnId", "timestamp", "scope", "toolStatus"))
                if (safe.containsKey(key))
                    projected.put(
                            key,
                            safe.get(key) instanceof String text
                                    ? ReportProjection.text(text, 256)
                                    : safe.get(key));
            return super.add(projected);
        }

        @Override
        public void clear() {
            super.clear();
            observed = 0;
        }
    }

    private static Map<String, Object> objectOrEmpty(Object value) {
        return value instanceof Map<?, ?> ? Values.object(value) : Map.of();
    }

    private static String costText(Object value) {
        String text = factText(value);
        if (text.length() > 128) return "UNKNOWN";
        try {
            return new java.math.BigDecimal(text).signum() >= 0 ? text : "UNKNOWN";
        } catch (NumberFormatException invalid) {
            return "UNKNOWN";
        }
    }

    private static String safeNarration(String narration, DataSanitizer sanitizer) {
        var sanitized = sanitizer.sanitize(Map.of("text", narration));
        if (sanitized == null || !(sanitized.get("text") instanceof String)) return "[WITHHELD]";
        String clean = (String) sanitized.get("text");
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

    private static String safeFact(Object value, DataSanitizer sanitizer) {
        var safe = sanitizer.sanitize(Map.of("fact", value == null ? "" : value));
        return ReportProjection.text(factText(safe.get("fact")), 128);
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
                + "\n\n报告覆盖范围（截断不改变执行结果，完整证据须回读来源日志）："
                + report.get("reportCoverage")
                + "\n";
    }
}
