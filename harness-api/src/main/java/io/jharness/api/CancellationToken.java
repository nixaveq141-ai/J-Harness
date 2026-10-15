package io.jharness.api;

@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancelled();

    default void throwIfCancelled() {
        if (isCancelled()) throw new HarnessException("CANCELLED", "Operation cancelled");
    }
}
