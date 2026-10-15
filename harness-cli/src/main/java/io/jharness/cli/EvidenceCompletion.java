package io.jharness.cli;

import io.jharness.api.*;
import java.util.*;

/** Criteria apply to the actual current turn, never a past turn that happened to execute a tool. */
final class EvidenceCompletion implements CompletionEvaluator {
    private final Arguments args;
    private final FileSystemView files;

    EvidenceCompletion(Arguments args, FileSystemView files) {
        this.args = args;
        this.files = files;
    }

    @Override
    public CompletionDecision evaluate(ChatResponse response, SessionProjection projection) {
        if (projection.latestTurnId().isEmpty()) return no("GOAL_UNVERIFIED");
        String turn = projection.latestTurnId().get();
        var results =
                projection.toolHistory().stream()
                        .filter(e -> e.turnId().equals(turn) && e.type().equals("tool.completed"))
                        .toList();
        if (!args.has("verify-argv") && !args.has("require-read") && !args.has("expect-file"))
            return no("GOAL_UNVERIFIED: provide --require-read/--expect-file/--verify-argv");
        for (String raw : args.all("require-read")) {
            String path = normalize(raw);
            if (results.stream()
                    .noneMatch(
                            e ->
                                    success(e)
                                            && "read_file".equals(e.payload().get("tool"))
                                            && path.equals(value(e).get("path"))))
                return no("READ_EVIDENCE_MISSING:" + path);
        }
        for (String raw : args.all("expect-file")) {
            String path = normalize(raw), version = null;
            for (var event : results)
                if (success(event)
                        && "WORKSPACE_WRITE".equals(event.payload().get("sideEffectLevel")))
                    for (var change : changes(event))
                        if (path.equals(change.get("path"))
                                && change.containsKey("beforeVersion")
                                && !Objects.equals(
                                        change.get("beforeVersion"), change.get("afterVersion")))
                            version = (String) change.get("afterVersion");
            if (version == null) return no("CHANGE_EVIDENCE_MISSING:" + path);
            try {
                if (!version.equals(files.versionOf(new ExecPath(path))))
                    return no("CURRENT_FILE_CONFLICT:" + path);
            } catch (java.io.IOException | HarnessException ex) {
                return no("CURRENT_FILE_VERIFICATION_FAILED:" + path);
            }
        }
        if (args.has("verify-argv")) {
            Object argv = Json.parse(args.get("verify-argv", ""));
            int lastWrite = -1, lastCommand = -1, verification = -1;
            boolean passed = false;
            for (int i = 0; i < results.size(); i++) {
                var event = results.get(i);
                var value = value(event);
                if ("WORKSPACE_WRITE".equals(event.payload().get("sideEffectLevel"))) lastWrite = i;
                if ("execute_command".equals(event.payload().get("tool"))) {
                    if (Boolean.TRUE.equals(event.payload().get("bodyEntered"))) lastCommand = i;
                    if (argv.equals(value.get("argv"))
                            && args.get("verify-mode", "ARGV").equals(value.get("mode"))
                            && normalize(args.get("verify-cwd", "."))
                                    .equals(normalize(String.valueOf(value.get("cwd"))))) {
                        verification = i;
                        passed =
                                success(event)
                                        && ((Number) value.getOrDefault("exitCode", -1)).intValue()
                                                == 0;
                    }
                }
            }
            if (verification < 0 || verification <= lastWrite || lastCommand > verification)
                return no("VERIFICATION_MISSING_OR_STALE");
            if (!passed) return no("VERIFICATION_FAILED");
        }
        return new CompletionDecision(true, "EXPLICIT_EVIDENCE_CRITERIA_PASSED");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').replaceAll("^(\\./)+", "");
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
        return ((List<?>) entries).stream().map(Values::object).toList();
    }

    private static CompletionDecision no(String reason) {
        return new CompletionDecision(false, reason);
    }
}
