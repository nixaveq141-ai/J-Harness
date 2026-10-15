package io.jharness.ecosystem;

import io.jharness.api.*;
import java.time.Duration;
import java.util.List;

/** Injectable command transport; fake results are contract evidence only. */
@FunctionalInterface
public interface CommandTransport {
    Reply call(
            List<String> command, byte[] stdin, Duration timeout, CancellationToken cancellation);

    record Reply(int exitCode, byte[] stdout, byte[] stderr, boolean started, boolean uncertain) {
        public Reply {
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
