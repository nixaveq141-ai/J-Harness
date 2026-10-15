package io.jharness.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Shell mode must be explicit. Environment values are host configuration, never model arguments.
 */
public record CommandRequest(
        String callId, Mode mode, List<String> argv, ExecPath cwd, Duration timeout) {
    public enum Mode {
        ARGV,
        CMD,
        POWERSHELL,
        SH
    }

    public CommandRequest {
        callId = Values.required(callId, "callId");
        Objects.requireNonNull(mode);
        argv = List.copyOf(argv);
        Objects.requireNonNull(cwd);
        Objects.requireNonNull(timeout);
        if (argv.isEmpty()
                || argv.size() > 256
                || argv.stream().anyMatch(s -> s == null || s.indexOf('\0') >= 0)
                || argv.stream().mapToLong(String::length).sum() > 32768
                || argv.getFirst().isBlank()
                || (mode != Mode.ARGV && argv.size() != 1)
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid bounded command");
    }
}
