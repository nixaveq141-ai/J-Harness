package io.jharness.api;

/** A scoped model execution policy. Never wraps tool bodies. */
@FunctionalInterface
public interface ModelExecution {
    ChatResponse execute(ModelAttempt attempt, CancellationToken cancellation);

    static ModelExecution once() {
        return (attempt, cancellation) -> {
            java.util.Objects.requireNonNull(attempt, "attempt");
            java.util.Objects.requireNonNull(cancellation, "cancellation").throwIfCancelled();
            return java.util.Objects.requireNonNull(
                    attempt.complete(false, cancellation), "model response");
        };
    }
}
