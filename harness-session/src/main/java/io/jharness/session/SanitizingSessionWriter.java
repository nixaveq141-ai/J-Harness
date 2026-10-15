package io.jharness.session;

import io.jharness.api.*;
import java.util.Objects;

/** Decorator for inputs and extension facts, not just model or tool results. */
public final class SanitizingSessionWriter implements SessionWriter {
    private final SessionWriter delegate;
    private final DataSanitizer sanitizer;

    public SanitizingSessionWriter(SessionWriter delegate, DataSanitizer sanitizer) {
        this.delegate = Objects.requireNonNull(delegate);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    @Override
    public String append(SessionEvent event) {
        return delegate.append(
                new SessionEvent(
                        event.eventId(),
                        event.sessionId(),
                        event.type(),
                        event.timestamp(),
                        event.schemaVersion(),
                        event.level(),
                        event.turnId(),
                        event.stepId(),
                        event.callId(),
                        sanitizer.sanitize(event.payload())));
    }

    @Override
    public boolean durable() {
        return delegate.durable();
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
