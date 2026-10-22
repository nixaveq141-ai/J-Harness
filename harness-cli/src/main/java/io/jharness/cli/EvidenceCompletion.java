package io.jharness.cli;

import io.jharness.api.*;
import java.util.*;

/** Current-turn evidence; malformed or uncertain facts never authorize completion. */
final class EvidenceCompletion implements CompletionEvaluator {
    private final Arguments args;
    private final FileSystemView files;
    private final List<String> verificationArgv;

    EvidenceCompletion(Arguments args, FileSystemView files) {
        this.args = args;
        this.files = files;
        for (String path : args.all("require-read")) criterion(path);
        for (String path : args.all("expect-file")) criterion(path);
        criterion(args.get("verify-cwd", "."));
        List<String> parsed = null;
        if (args.has("verify-argv")) {
            try {
                Object raw = Json.parse(args.get("verify-argv", ""));
                if (!(raw instanceof List<?> parts)
                        || parts.isEmpty()
                        || parts.stream().anyMatch(p -> !(p instanceof String s) || s.isBlank()))
                    throw new IllegalArgumentException();
                parsed = parts.stream().map(String.class::cast).toList();
            } catch (RuntimeException invalid) {
                throw new HarnessException(
                        "CLI_USAGE", "verify-argv requires a nonempty JSON string array");
            }
        }
        verificationArgv = parsed;
    }

    public CompletionDecision evaluate(ChatResponse response, SessionProjection projection) {
        try {
            return evaluateFacts(projection);
        } catch (HarnessException | ClassCastException | IllegalArgumentException invalid) {
            return no("EVIDENCE_INVALID");
        }
    }

    private CompletionDecision evaluateFacts(SessionProjection projection) {
        if (projection.latestTurnId().isEmpty()) return no("GOAL_UNVERIFIED");
        String turn = projection.latestTurnId().get();
        var results =
                projection.toolHistory().stream()
                        .filter(e -> e.turnId().equals(turn) && e.type().equals("tool.completed"))
                        .toList();
        if (verificationArgv == null && !args.has("require-read") && !args.has("expect-file"))
            return no("GOAL_UNVERIFIED: provide --require-read/--expect-file/--verify-argv");
        for (String raw : args.all("require-read")) {
            String path = criterion(raw);
            if (results.stream()
                    .noneMatch(
                            e ->
                                    success(e)
                                            && "read_file".equals(e.payload().get("tool"))
                                            && path.equals(normalize(value(e).get("path")))))
                return no("READ_EVIDENCE_MISSING:" + path);
        }
        for (String raw : args.all("expect-file")) {
            String path = criterion(raw), version = null;
            for (var event : results)
                if (success(event)
                        && "WORKSPACE_WRITE".equals(event.payload().get("sideEffectLevel")))
                    for (var change : changes(event))
                        if (path.equals(normalize(change.get("path")))
                                && change.containsKey("beforeVersion")
                                && !Objects.equals(
                                        change.get("beforeVersion"), change.get("afterVersion"))) {
                            if (!(change.get("afterVersion") instanceof String after)
                                    || after.isBlank())
                                return no("CHANGE_EVIDENCE_INVALID:" + path);
                            version = after;
                        }
            if (version == null) return no("CHANGE_EVIDENCE_MISSING:" + path);
            try {
                if (!version.equals(files.versionOf(new ExecPath(path))))
                    return no("CURRENT_FILE_CONFLICT:" + path);
            } catch (java.io.IOException | HarnessException ex) {
                return no("CURRENT_FILE_VERIFICATION_FAILED:" + path);
            }
        }
        if (verificationArgv != null) {
            int lastWrite = -1, lastCommand = -1, verification = -1;
            boolean passed = false;
            for (int i = 0; i < results.size(); i++) {
                var event = results.get(i);
                var value = value(event);
                boolean denied =
                        "DENIED".equals(event.payload().get("status"))
                                || Boolean.FALSE.equals(event.payload().get("bodyEntered"));
                if ("WORKSPACE_WRITE".equals(event.payload().get("sideEffectLevel")) && !denied)
                    lastWrite = i;
                if ("execute_command".equals(event.payload().get("tool"))
                        && Boolean.TRUE.equals(event.payload().get("bodyEntered"))) {
                    lastCommand = i;
                    if (verificationArgv.equals(value.get("argv"))
                            && args.get("verify-mode", "ARGV").equals(value.get("mode"))
                            && criterion(args.get("verify-cwd", "."))
                                    .equals(normalize(value.get("cwd")))) {
                        verification = i;
                        Object exit = value.get("exitCode");
                        passed =
                                success(event)
                                        && exit instanceof Number n
                                        && Double.isFinite(n.doubleValue())
                                        && n.doubleValue() == 0.0;
                    }
                }
            }
            if (verification < 0 || verification <= lastWrite || lastCommand > verification)
                return no("VERIFICATION_MISSING_OR_STALE");
            if (!passed) return no("VERIFICATION_FAILED");
        }
        return new CompletionDecision(true, "EXPLICIT_EVIDENCE_CRITERIA_PASSED");
    }

    private static String criterion(String path) {
        String normalized = normalize(path);
        if (normalized == null)
            throw new HarnessException(
                    "CLI_USAGE",
                    "Evidence path must be workspace relative without parent traversal");
        return normalized;
    }

    private static String normalize(Object raw) {
        if (!(raw instanceof String text)) return null;
        String path = text.replace('\\', '/');
        if (path.startsWith("/")
                || path.contains(":")
                || path.codePoints().anyMatch(Character::isISOControl)) return null;
        var parts = new ArrayList<String>();
        for (String p : path.split("/")) {
            if (p.equals("..")) return null;
            if (!p.isEmpty() && !p.equals(".")) parts.add(p);
        }
        return parts.isEmpty() ? "." : String.join("/", parts);
    }

    private static boolean success(SessionEvent event) {
        return "SUCCESS".equals(event.payload().get("status"));
    }

    private static Map<String, Object> value(SessionEvent event) {
        return Values.object(event.payload().getOrDefault("value", Map.of()));
    }

    private static List<Map<String, Object>> changes(SessionEvent event) {
        var display = Values.object(event.payload().getOrDefault("display", Map.of()));
        if (display.containsKey("path")) return List.of(display);
        Object entries =
                value(event).getOrDefault("changes", display.getOrDefault("changes", List.of()));
        if (!(entries instanceof List<?> list))
            throw new HarnessException("EVIDENCE_INVALID", "Changes must be an array");
        return list.stream().map(Values::object).toList();
    }

    private static CompletionDecision no(String reason) {
        return new CompletionDecision(false, reason);
    }
}
