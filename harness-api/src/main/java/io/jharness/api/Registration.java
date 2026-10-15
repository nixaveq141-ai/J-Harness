package io.jharness.api;

/** An idempotent revocation handle. Closing removes this contribution only. */
@FunctionalInterface
public interface Registration extends AutoCloseable {
    @Override
    void close();
}
