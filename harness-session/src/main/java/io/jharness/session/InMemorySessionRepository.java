package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** Test-only storage: flush confirms memory visibility, NEVER physical durability. */
public final class InMemorySessionRepository implements SessionRepository {
    public record Limits(int events, int bytes) {
        public Limits {
            if (events < 1 || events > 10000 || bytes < 1 || bytes > 32 * 1024 * 1024)
                throw new IllegalArgumentException("Memory limits");
        }
    }

    private final Limits limits;

    public InMemorySessionRepository() {
        this(new Limits(10000, 32 * 1024 * 1024));
    }

    public InMemorySessionRepository(Limits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    private final Map<String, Store> stores = new LinkedHashMap<>();

    @Override
    public synchronized SessionWriter openWriter(String sessionId, WriteOwnership ownership) {
        Values.required(sessionId, "sessionId");
        Objects.requireNonNull(ownership);
        if (!stores.containsKey(sessionId) && stores.size() >= 10000)
            throw new HarnessException("SESSION_CATALOG_LIMIT", "Session count limit");
        Store store = stores.computeIfAbsent(sessionId, ignored -> new Store());
        synchronized (store) {
            if (store.writer != null)
                throw new HarnessException(
                        "WRITE_CONFLICT", "Session already has a writer: " + sessionId);
            Writer writer = new Writer(sessionId, store, limits);
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
    public String fork(String sessionId, String fromEventId) {
        List<SessionEvent> prefix;
        synchronized (this) {
            Store source = stores.get(sessionId);
            if (source == null) throw new HarnessException("SESSION_NOT_FOUND", "Unknown parent");
            synchronized (source) {
                if (source.writer != null)
                    throw new HarnessException("WRITE_CONFLICT", "Parent is active");
                int end = -1;
                for (int i = 0; i < source.events.size(); i++)
                    if (source.events.get(i).eventId().equals(fromEventId)) end = i;
                if (end < 0) throw new HarnessException("EVENT_NOT_FOUND", "Unknown fork point");
                prefix = List.copyOf(source.events.subList(0, end + 1));
            }
        }
        String target = "s-" + UUID.randomUUID();
        Store copy = new Store();
        Writer writer = new Writer(target, copy, limits);
        copy.writer = writer;
        try (writer) {
            for (var e : prefix)
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
        synchronized (this) {
            if (stores.size() >= 10000)
                throw new HarnessException("SESSION_CATALOG_LIMIT", "Session count limit");
            stores.put(target, copy);
        }
        return target;
    }

    private static final class Store {
        final List<SessionEvent> events = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        Writer writer;
        long bytes;
    }

    private static final class Writer implements SessionWriter {
        private final String sessionId;
        private final Store store;
        private boolean closed;
        private final Limits limits;

        Writer(String sessionId, Store store, Limits limits) {
            this.limits = limits;
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
                    throw new HarnessException(
                            "FUTURE_VERSION_READ_ONLY", "Unsupported event schema");
                if (store.ids.contains(event.eventId()))
                    throw new HarnessException("DUPLICATE_EVENT", "Duplicate eventId");
                if (store.events.size() >= limits.events())
                    throw new HarnessException("SESSION_LIMIT", "Memory event count limit");
                var row = new LinkedHashMap<String, Object>();
                row.put("eventId", event.eventId());
                row.put("sessionId", event.sessionId());
                row.put("type", event.type());
                row.put("timestamp", event.timestamp().toString());
                row.put("schemaVersion", event.schemaVersion());
                row.put("level", event.level().name());
                row.put("turnId", event.turnId());
                row.put("stepId", event.stepId());
                row.put("callId", event.callId());
                row.put("payload", event.payload());
                int size =
                        JsonEncoding.encode(
                                                row,
                                                Math.min(8 * 1024 * 1024, limits.bytes()),
                                                100000,
                                                64,
                                                "SESSION_LIMIT")
                                        .length
                                + 1;
                if (store.bytes + size > limits.bytes())
                    throw new HarnessException("SESSION_LIMIT", "Memory byte limit");
                store.bytes += size;
                store.ids.add(event.eventId());
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
