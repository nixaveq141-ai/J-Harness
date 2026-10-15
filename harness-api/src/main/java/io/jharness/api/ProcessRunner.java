package io.jharness.api;

import java.util.*;

public interface ProcessRunner {
    default ToolResult execute(CommandRequest request, CancellationToken cancellation) {
        throw new HarnessException(
                "UNSUPPORTED",
                "Provider must implement the bounded CommandRequest contract explicitly");
    }

    ToolResult execute(
            List<String> argv, ExecPath workingDirectory, CancellationToken cancellation);
}
