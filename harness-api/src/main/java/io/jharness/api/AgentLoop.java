package io.jharness.api;

/** Runtime lifecycle boundary. The default loop and its state machine arrive in M1. */
public interface AgentLoop {
    void run(CancellationToken cancellation);
}
