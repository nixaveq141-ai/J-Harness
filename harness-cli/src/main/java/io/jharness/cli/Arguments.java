package io.jharness.cli;

import io.jharness.api.HarnessException;
import java.util.*;

final class Arguments {
    private static final Set<String> FLAGS = Set.of("help", "version", "dry-run", "rebuild-index");
    private static final Set<String> VALUES =
            Set.of(
                    "workspace",
                    "config",
                    "session",
                    "run",
                    "state-dir",
                    "plan",
                    "require-read",
                    "expect-file",
                    "verify-argv",
                    "verify-mode",
                    "verify-cwd",
                    "call",
                    "outcome",
                    "reason",
                    "after",
                    "before",
                    "type",
                    "tool",
                    "status",
                    "model",
                    "session-workspace",
                    "offset",
                    "limit",
                    "event",
                    "output",
                    "artifact",
                    "profile",
                    "preset",
                    "bundle",
                    "model-route",
                    "terminal-id",
                    "text",
                    "other-session",
                    "environment-id",
                    "task-id",
                    "web-catalog");
    final List<String> positional = new ArrayList<>();
    final Map<String, List<String>> options = new LinkedHashMap<>();

    Arguments(String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.equals("--")) {
                positional.addAll(Arrays.asList(argv).subList(i + 1, argv.length));
                break;
            }
            if (!arg.startsWith("--")) {
                if (arg.startsWith("-") && positional.isEmpty()) fail();
                positional.add(arg);
                continue;
            }
            int equals = arg.indexOf('=');
            String name = arg.substring(2, equals < 0 ? arg.length() : equals);
            if (!FLAGS.contains(name) && !VALUES.contains(name)) fail();
            if (equals >= 0 && FLAGS.contains(name)) fail();
            String value = "true";
            if (VALUES.contains(name)) {
                if (equals >= 0) value = arg.substring(equals + 1);
                else {
                    if (++i >= argv.length || argv[i].startsWith("--")) fail();
                    value = argv[i];
                }
                if (!Set.of("text", "reason").contains(name) && value.isBlank()) fail();
            }
            if (options.containsKey(name)
                    && !Set.of("require-read", "expect-file", "bundle").contains(name)) fail();
            options.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
    }

    String get(String key, String fallback) {
        return options.containsKey(key) ? options.get(key).getFirst() : fallback;
    }

    List<String> all(String key) {
        return List.copyOf(options.getOrDefault(key, List.of()));
    }

    boolean has(String key) {
        return options.containsKey(key);
    }

    static void fail() {
        throw new HarnessException(
                "CLI_USAGE", "Invalid/duplicate option or missing argument; use --help");
    }
}
