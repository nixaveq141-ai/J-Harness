package io.jharness.process;

import io.jharness.api.*;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Application lifecycle bridge: cancellation endpoint owns exactly one agent run. */
public final class ControlledAgentRun implements AutoCloseable {
    private final LocalRunControl control;
    private boolean started;
    private boolean closed, ended;

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
        synchronized (this) {
            if (closed) throw new HarnessException("RUN_CLOSED", "Run endpoint is closed");
            if (started)
                throw new HarnessException(
                        "RUN_ALREADY_STARTED", "One agent run per control endpoint");
            started = true;
        }
        Throwable primary = null;
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
        } catch (RuntimeException | Error ex) {
            primary = ex;
            throw ex;
        } finally {
            boolean interrupted = Thread.interrupted();
            boolean wasCancelled = control.cancellation().isCancelled();
            try {
                EventSupport.append(
                        writer,
                        events,
                        scope,
                        "run.ended",
                        EventLevel.SESSION,
                        null,
                        null,
                        null,
                        Map.of(
                                "runId",
                                runId(),
                                "cancelled",
                                wasCancelled,
                                "outcome",
                                primary != null
                                        ? "FAILED"
                                        : wasCancelled ? "CANCELLED" : "COMPLETED"));
                EventSupport.checkpoint(writer, CheckpointDecision.DURABLE_FLUSH);
            } catch (RuntimeException | Error ex) {
                if (primary != null) primary.addSuppressed(ex);
                else {
                    primary = ex;
                    throw ex;
                }
            } finally {
                synchronized (this) {
                    ended = true;
                    closed = true;
                }
                try {
                    control.close();
                } catch (IOException ex) {
                    var failure =
                            new HarnessException(
                                    "RUN_STATE_IO", "Unable to persist exited run state", ex);
                    if (primary != null) primary.addSuppressed(failure);
                    else throw failure;
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (started && !ended) control.requestCancellation();
        else control.close();
    }
}
