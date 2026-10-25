package io.jharness.testkit;

import io.jharness.api.*;
import io.jharness.session.*;
import io.jharness.workspace.*;
import java.nio.file.Path;
import java.util.Map;

/** Own child JVM and owned fixture only; deliberately loses result settlement. */
public final class CrashWriteProbe {
    private CrashWriteProbe() {}

    public static void main(String[] args) throws Exception {
        JsonlSessionRepository repository = new JsonlSessionRepository(Path.of(args[0]));
        try (SessionWriter writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            PluginScope scope = new PluginScope("s", "probe");
            SessionEvent intent =
                    new SessionEvent(
                            "intent",
                            "s",
                            "tool.intent",
                            java.time.Instant.now(),
                            1,
                            EventLevel.TOOL,
                            "t",
                            "p",
                            "crash-call",
                            Map.of(
                                    "tool",
                                    "write_file",
                                    "arguments",
                                    Map.of("path", "effect.txt", "content", "committed"),
                                    "sideEffectLevel",
                                    "WORKSPACE_WRITE"));
            writer.append(intent);
            writer.flush();
            writer.append(
                    new SessionEvent(
                            "started",
                            "s",
                            "tool.started",
                            java.time.Instant.now(),
                            1,
                            EventLevel.TOOL,
                            "t",
                            "p",
                            "crash-call",
                            intent.payload()));
            new LocalWorkspace(Path.of(args[1]))
                    .write(
                            new ExecPath("effect.txt"),
                            "committed",
                            false,
                            null,
                            CancellationToken.NONE);
            Runtime.getRuntime().halt(17); // Explicit controlled crash after the side effect.
        }
    }
}
