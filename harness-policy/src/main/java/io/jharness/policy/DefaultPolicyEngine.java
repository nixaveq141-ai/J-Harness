package io.jharness.policy;

import io.jharness.api.*;
import java.util.*;

/** Exact structural command templates; no prefix matching or Shell string allowlist. */
public final class DefaultPolicyEngine implements PolicyEngine {
    public record TrustedCommand(List<String> argv, String cwd) {
        public TrustedCommand {
            argv = List.copyOf(argv);
            cwd = Values.required(cwd, "cwd");
            if (forbiddenPath(cwd))
                throw new IllegalArgumentException("Invalid trusted command cwd");
            // Reuse the public command boundary: empty argv, NUL and oversized arguments
            // must not become trusted templates through a weaker duplicate validator.
            new CommandRequest(
                    "trusted-command",
                    CommandRequest.Mode.ARGV,
                    argv,
                    new ExecPath(cwd),
                    java.time.Duration.ofSeconds(1));
        }

        boolean matches(Map<String, Object> args) {
            return "ARGV".equals(args.getOrDefault("mode", "ARGV"))
                    && argv.equals(args.get("argv"))
                    && cwd.equals(args.getOrDefault("cwd", "."))
                    && Set.of("argv", "cwd", "mode", "timeoutMs").containsAll(args.keySet());
        }
    }

    private final List<TrustedCommand> commands;

    public DefaultPolicyEngine() {
        this(
                List.of(
                        new TrustedCommand(
                                List.of(
                                        "git",
                                        "--no-optional-locks",
                                        "-c",
                                        "core.fsmonitor=false",
                                        "status",
                                        "--short"),
                                ".")));
    }

    public DefaultPolicyEngine(List<TrustedCommand> commands) {
        this.commands = List.copyOf(commands);
    }

    @Override
    public Decision evaluate(ToolInvocation call, ToolContext context) {
        context.cancellation().throwIfCancelled();
        Object path =
                call.arguments().getOrDefault("path", call.arguments().getOrDefault("cwd", "."));
        if (path instanceof String text && forbiddenPath(text)) return Decision.DENY;
        if (call.tool().equals("grep") && call.arguments().containsKey("paths")) {
            Object paths = call.arguments().get("paths");
            if (!(paths instanceof List<?> entries)
                    || entries.stream().anyMatch(p -> !(p instanceof String s) || forbiddenPath(s)))
                return Decision.DENY;
        }
        if (call.tool().equals("execute_command")) {
            if (commands.stream().anyMatch(t -> t.matches(call.arguments()))) return Decision.ALLOW;
            return Decision.ASK;
        }
        return call.sideEffectLevel() == SideEffectLevel.READ_ONLY ? Decision.ALLOW : Decision.ASK;
    }

    private static boolean forbiddenPath(String text) {
        String path = text.replace('\\', '/');
        if (path.startsWith("/")
                || path.contains(":")
                || path.codePoints().anyMatch(Character::isISOControl)) return true;
        return Arrays.stream(path.split("/"))
                .anyMatch(
                        part ->
                                part.equals("..")
                                        || !part.equals(".")
                                                && (part.endsWith(" ") || part.endsWith("."))
                                        || part.startsWith("..")
                                                && part.substring(2).matches("[. ]+")
                                        || part.replaceAll("[. ]+$", "").equalsIgnoreCase(".git")
                                        || part.replaceAll("[. ]+$", "")
                                                .equalsIgnoreCase(".jharness")
                                        || part.replaceAll("[. ]+$", "").equalsIgnoreCase(".env")
                                        || part.toLowerCase(Locale.ROOT).startsWith(".env."));
    }
}
