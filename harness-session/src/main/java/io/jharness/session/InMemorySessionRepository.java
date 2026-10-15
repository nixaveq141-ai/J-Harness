package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** Test-only storage: flush confirms memory visibility, NEVER physical durability. */
public final class InMemorySessionRepository implements SessionRepository {
    private final Map<String, Store> stores = new LinkedHashMap<>();

    @Override
    public synchronized SessionWriter openWriter(String sessionId, WriteOwnership ownership) {
        Values.required(sessionId, "sessionId");
        Objects.requireNonNull(ownership);
        Store store = stores.computeIfAbsent(sessionId, ignored -> new Store());
        synchronized (store) {
            if (store.writer != null)
                throw new HarnessException(
                        "WRITE_CONFLICT", "Session already has a writer: " + sessionId);
            Writer writer = new Writer(sessionId, store);
            store.writer = writer;
            return writer;
        }
    }

    @Override
    public synchronized SessionReader openReader(String sessionId) {
        Store store = stores.get(sessionId);
        if (store == null)
            throw new HarnessException("SESSION_NOT_FOUND", "Unknown session: " + sessionId);
        return () -> {
            synchronized (store) {
                return List.copyOf(store.events);
            }
        };
    }

    @Override
    public synchronized List<String> list() {
        return List.copyOf(stores.keySet());
    }

    @Override
    public synchronized String fork(String sessionId, String fromEventId) {
        Store source = stores.get(sessionId);
        if (source == null) throw new HarnessException("SESSION_NOT_FOUND", "Unknown parent");
        synchronized (source) {
            if (source.writer != null)
                throw new HarnessException("WRITE_CONFLICT", "Parent is active");
            int end = -1;
            for (int i = 0; i < source.events.size(); i++)
                if (source.events.get(i).eventId().equals(fromEventId)) end = i;
            if (end < 0) throw new HarnessException("EVENT_NOT_FOUND", "Unknown fork point");
            String target = "s-" + UUID.randomUUID();
            try (SessionWriter writer = openWriter(target, WriteOwnership.EXCLUSIVE)) {
                for (SessionEvent e : source.events.subList(0, end + 1))
                    writer.append(
                            new SessionEvent(
                                    e.eventId(),
                                    target,
                                    e.type(),
                                    e.timestamp(),
                                    e.schemaVersion(),
                                    e.level(),
                                    e.turnId(),
                                    e.stepId(),
                                    e.callId(),
                                    e.payload()));
                writer.append(
                        SessionEvent.session(
                                target,
                                "session.forked",
                                Map.of("parentSessionId", sessionId, "fromEventId", fromEventId)));
            }
            return target;
        }
    }

    private static final class Store {
        final List<SessionEvent> events = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        Writer writer;
    }

    private static final class Writer implements SessionWriter {
        private final String sessionId;
        private final Store store;
        private boolean closed;

        Writer(String sessionId, Store store) {
            this.sessionId = sessionId;
            this.store = store;
        }

        private void ensureOpen() {
            if (closed || store.writer != this)
                throw new HarnessException("WRITER_CLOSED", "Session writer is closed");
        }

        @Override
        public String append(SessionEvent event) {
            Objects.requireNonNull(event);
            synchronized (store) {
                ensureOpen();
                if (!sessionId.equals(event.sessionId()))
                    throw new HarnessException(
                            "SESSION_MISMATCH", "Event belongs to another session");
                if (event.schemaVersion() > 1)
                    throw new HarnessException("FUTURE_VERSION", "Unsupported event schema");
                if (!store.ids.add(event.eventId()))
                    throw new HarnessException("DUPLICATE_EVENT", "Duplicate eventId");
                store.events.add(event);
                return event.eventId();
            }
        }

        @Override
        public void flush() {
            synchronized (store) {
                ensureOpen();
            }
        }

        @Override
        public void close() {
            synchronized (store) {
                if (!closed) {
                    closed = true;
                    store.writer = null;
                }
            }
        }
    }
}
