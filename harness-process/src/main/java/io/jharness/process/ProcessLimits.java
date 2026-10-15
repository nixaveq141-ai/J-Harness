package io.jharness.process;

import java.nio.charset.Charset;
import java.time.Duration;

/** Trusted host configuration; model may shorten timeout but cannot raise these ceilings. */
public record ProcessLimits(
        Duration timeout,
        int maxConcurrent,
        int maxTreeProcesses,
        int outputBytes,
        Charset outputEncoding) {
    public ProcessLimits(
            Duration timeout, int maxConcurrent, int maxTreeProcesses, int outputBytes) {
        this(
                timeout,
                maxConcurrent,
                maxTreeProcesses,
                outputBytes,
                Charset.forName(System.getProperty("native.encoding", "UTF-8")));
    }

    public ProcessLimits {
        java.util.Objects.requireNonNull(outputEncoding);
        if (timeout == null
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofHours(1)) > 0
                || maxConcurrent < 1
                || maxConcurrent > 16
                || maxTreeProcesses < 1
                || maxTreeProcesses > 256
                || outputBytes < 128
                || outputBytes > 1024 * 1024)
            throw new IllegalArgumentException("Invalid process limits");
    }

    public static ProcessLimits defaults() {
        return new ProcessLimits(Duration.ofMinutes(2), 2, 64, 65536);
    }
}
