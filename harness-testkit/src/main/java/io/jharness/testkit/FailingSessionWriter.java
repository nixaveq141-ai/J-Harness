package io.jharness.testkit;

import io.jharness.api.*;

/** Fault injection at append/flush boundaries. */
public final class FailingSessionWriter implements SessionWriter {
    @Override
    public String append(SessionEvent event) {
        throw new HarnessException("CHECKPOINT_FAILED", "Injected append failure");
    }

    @Override
    public void flush() {
        throw new HarnessException("CHECKPOINT_FAILED", "Injected flush failure");
    }

    @Override
    public void close() {}
}
