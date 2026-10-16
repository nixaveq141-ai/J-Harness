package io.jharness.api;

/** A scoped model execution policy. Never wraps tool bodies. */
@FunctionalInterface
public interface ModelExecution {
    ChatResponse execute(ModelAttempt attempt, CancellationToken cancellation);

    static ModelExecution once() {
        return (attempt, cancellation) -> attempt.complete(false, cancellation);
    }
}
