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
        return appendPrepared(event).eventId();
    }

    @Override
    public SessionEvent appendPrepared(SessionEvent event) {
        for (String id :
                new String[] {
                    event.eventId(),
                    event.sessionId(),
                    event.type(),
                    event.turnId(),
                    event.stepId(),
                    event.callId()
                })
            if (id != null
                    && !id.equals(
                            sanitizer.sanitize(java.util.Map.of("identity", id)).get("identity")))
                throw new HarnessException(
                        "UNSAFE_EVENT_IDENTITY", "Event identifiers cannot contain known secrets");
        return delegate.appendPrepared(
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
