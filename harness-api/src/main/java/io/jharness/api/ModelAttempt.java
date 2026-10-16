package io.jharness.api;

/** One fully prepared, budgeted and checkpointed attempt, owned by the loop. */
@FunctionalInterface
public interface ModelAttempt {
    ChatResponse complete(boolean singleSend, CancellationToken cancellation);
}
