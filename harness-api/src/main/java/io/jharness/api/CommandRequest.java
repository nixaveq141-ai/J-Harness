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
        if (callId.length() > 256)
            throw new IllegalArgumentException("callId exceeds 256 characters");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(argv, "argv");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(timeout, "timeout");
        if (argv.isEmpty() || argv.size() > 256)
            throw new IllegalArgumentException("argv count must be 1..256");
        long characters = 0;
        for (String argument : argv) {
            Objects.requireNonNull(argument, "argv element");
            if (argument.indexOf('\0') >= 0)
                throw new IllegalArgumentException("argv element contains NUL");
            characters += argument.length();
        }
        if (characters > 32768) throw new IllegalArgumentException("argv exceeds 32768 characters");
        if (argv.getFirst().isBlank())
            throw new IllegalArgumentException("argv first element is blank");
        if (mode != Mode.ARGV && argv.size() != 1)
            throw new IllegalArgumentException("argv count must be one in shell mode");
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("timeout must be positive and at most one hour");
        argv = List.copyOf(argv);
    }

    @Override
    public String toString() {
        return "CommandRequest[mode="
                + mode
                + ", argc="
                + argv.size()
                + ", timeout="
                + timeout
                + "]";
    }
}
