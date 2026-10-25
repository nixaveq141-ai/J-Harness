package io.jharness.ecosystem;

import io.jharness.api.*;
import java.time.Duration;
import java.util.List;

/**
 * Injectable command transport. timeout/token are mandatory and cooperative. started means the
 * owned process was launched; uncertain includes incomplete stdin, timeout, cancellation and
 * unconfirmed cleanup. Observed exit/output never prove an arbitrary side effect did not happen.
 * Fake replies are contract evidence only; returned byte arrays are owned snapshots. failureCode is
 * optional transport telemetry, not an execution-state decision. Consumers must use
 * started/uncertain and the validated response; arbitrary failure strings are not user output.
 */
@FunctionalInterface
public interface CommandTransport {
    Reply call(
            List<String> command, byte[] stdin, Duration timeout, CancellationToken cancellation);

    record Reply(
            int exitCode,
            byte[] stdout,
            byte[] stderr,
            boolean started,
            boolean uncertain,
            String failureCode) {
        public Reply(
                int exitCode, byte[] stdout, byte[] stderr, boolean started, boolean uncertain) {
            this(exitCode, stdout, stderr, started, uncertain, "");
        }

        public Reply {
            java.util.Objects.requireNonNull(failureCode, "failureCode");
            if (stdout.length > 4 * 1024 * 1024 || stderr.length > 65536)
                throw new HarnessException(
                        "CONTROL_OUTPUT_LIMIT", "Transport reply exceeds public bound");
            stdout = stdout.clone();
            stderr = stderr.clone();
        }

        @Override
        public byte[] stdout() {
            return stdout.clone();
        }

        @Override
        public byte[] stderr() {
            return stderr.clone();
        }
    }
}
