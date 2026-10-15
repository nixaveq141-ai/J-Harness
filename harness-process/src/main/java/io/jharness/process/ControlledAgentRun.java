package io.jharness.process;

import io.jharness.api.*;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Application lifecycle bridge: cancellation endpoint owns exactly one agent run. */
public final class ControlledAgentRun implements AutoCloseable {
    private final LocalRunControl control;
    private boolean started;

    public ControlledAgentRun(LocalRunControl control) {
        this.control = Objects.requireNonNull(control);
    }

    public String runId() {
        return control.runId();
    }

    public void run(
            AgentLoop loop,
            SessionWriter writer,
            EventBus events,
            PluginScope scope,
            CancellationToken cancellation) {
        if (started)
            throw new HarnessException("RUN_ALREADY_STARTED", "One agent run per control endpoint");
        started = true;
        try {
            EventSupport.append(
                    writer,
                    events,
                    scope,
                    "run.started",
                    EventLevel.SESSION,
                    null,
                    null,
                    null,
                    Map.of(
                            "runId",
                            runId(),
                            "pid",
                            ProcessHandle.current().pid(),
                            "control",
                            "authenticated-loopback-socket"));
            EventSupport.checkpoint(writer, CheckpointDecision.DURABLE_FLUSH);
            loop.run(() -> cancellation.isCancelled() || control.cancellation().isCancelled());
        } finally {
            boolean wasCancelled = control.cancellation().isCancelled();
            try {
                close();
            } catch (IOException ex) {
                throw new HarnessException("RUN_STATE_IO", "Unable to persist exited run state");
            }
            EventSupport.append(
                    writer,
                    events,
                    scope,
                    "run.ended",
                    EventLevel.SESSION,
                    null,
                    null,
                    null,
                    Map.of("runId", runId(), "cancelled", wasCancelled));
        }
    }

    @Override
    public void close() throws IOException {
        control.close();
    }
}
