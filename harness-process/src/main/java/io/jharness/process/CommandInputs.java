package io.jharness.process;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;

/** Pure validation shared by command and terminal start, before any execution. */
final class CommandInputs {
    private CommandInputs() {}

    static CommandRequest request(ToolInvocation call) {
        try {
            var args = call.arguments();
            if (!(args.get("argv") instanceof List<?> argv)
                    || argv.stream().anyMatch(v -> !(v instanceof String)))
                throw new IllegalArgumentException();
            return new CommandRequest(
                    call.callId(),
                    CommandRequest.Mode.valueOf((String) args.getOrDefault("mode", "ARGV")),
                    argv.stream().map(String.class::cast).toList(),
                    new ExecPath((String) args.getOrDefault("cwd", ".")),
                    Duration.ofMillis(
                            Values.integer(
                                    args.getOrDefault("timeoutMs", 120000), "SCHEMA_MISMATCH")));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException ex) {
            throw new HarnessException("SCHEMA_MISMATCH", "Invalid bounded command arguments", ex);
        }
    }
}
