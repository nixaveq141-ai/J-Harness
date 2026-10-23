package io.jharness.web;

import io.jharness.api.*;
import io.jharness.session.JsonlSessionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Disk facts addressed by submission hash. Root ownership is held throughout migration/use. */
final class DurableTaskCatalog implements SessionWriter {
    private final SessionWriter owner;
    private final JsonlSessionRepository records;
    private final DataSanitizer sanitizer;
    private final Path directory;
    private final Map<String, String> recent = new LinkedHashMap<>();
    private int count;

    DurableTaskCatalog(
            JsonlSessionRepository repository,
            String id,
            SessionWriter owner,
            DataSanitizer sanitizer)
            throws IOException {
        this(repository, id, owner, sanitizer, () -> {});
    }

    DurableTaskCatalog(
            JsonlSessionRepository repository,
            String id,
            SessionWriter owner,
            DataSanitizer sanitizer,
            Runnable afterMigrationRecords)
            throws IOException {
        this.owner = owner;
        this.sanitizer = sanitizer;
        directory = repository.directory().resolve("tasks-" + hash(id).substring(0, 24));
        var legacy = repository.openReader(id).events();
        var marker = legacy.stream().filter(e -> e.type().equals("web.catalog.version")).toList();
        if (marker.size() > 3) throw error("FUTURE_WEB_CATALOG");
        for (int i = 0; i < marker.size(); i++)
            if (!Integer.valueOf(i + 2).equals(marker.get(i).payload().get("version")))
                throw error("FUTURE_WEB_CATALOG");
        if (!marker.isEmpty()
                && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || !directory
                                .getFileName()
                                .toString()
                                .equals(marker.getFirst().payload().get("store"))))
            throw error("WEB_CATALOG_MISSING");
        check();
        Files.createDirectories(directory);
        check();
        records = new JsonlSessionRepository(directory);
        if (marker.isEmpty()) {
            var grouped = new LinkedHashMap<String, List<SessionEvent>>();
            for (var event : legacy) {
                if (!Set.of("web.task.accepted", "web.task.finished", "web.task.verified")
                                .contains(event.type())
                        || !(event.payload().get("taskId") instanceof String task))
                    throw error("INVALID_WEB_CATALOG");
                grouped.computeIfAbsent(task, ignored -> new ArrayList<>()).add(event);
            }
            var uniqueHashes = new HashSet<String>();
            for (var history : grouped.values()) {
                String task = (String) history.getFirst().payload().get("taskId");
                for (var event : history) {
                    validate(event);
                    if (!task.equals(event.payload().get("taskId")))
                        throw error("INVALID_WEB_CATALOG");
                }
                Object hash = history.getFirst().payload().get("requestHash");
                if (!(hash instanceof String value) || !uniqueHashes.add(value))
                    throw error("MIGRATION_CONFLICT");
                if (history.stream().filter(e -> e.type().equals("web.task.accepted")).count() != 1)
                    throw error("INVALID_WEB_CATALOG");
            }
            Path backup = repository.backupOwned(id, owner);
            for (var history : grouped.values()) {
                var first = history.getFirst();
                if (!first.type().equals("web.task.accepted")) throw error("INVALID_WEB_CATALOG");
                String hash = (String) first.payload().get("requestHash");
                String recordId = recordId(hash);
                try (var writer = records.openWriter(recordId, WriteOwnership.EXCLUSIVE)) {
                    var existing = records.openReader(recordId).events();
                    if (existing.size() > history.size()) throw error("MIGRATION_CONFLICT");
                    for (int i = 0; i < existing.size(); i++)
                        if (!migrationMatches(existing.get(i), history.get(i), recordId))
                            throw error("MIGRATION_CONFLICT");
                    for (int i = existing.size(); i < history.size(); i++)
                        writer.append(migrationEvent(history.get(i), recordId));
                    writer.flush();
                }
            }
            afterMigrationRecords.run();
            owner.append(
                    SessionEvent.session(
                            id,
                            "web.catalog.version",
                            Map.of(
                                    "version",
                                    2,
                                    "backup",
                                    backup.getFileName().toString(),
                                    "store",
                                    directory.getFileName().toString())));
            owner.flush();
        } else if (!legacy.getLast().equals(marker.getLast())) throw error("INVALID_WEB_CATALOG");
        if (marker.size() < 2) {
            Path backup = repository.backupOwned(id, owner);
            owner.append(
                    SessionEvent.session(
                            id,
                            "web.catalog.version",
                            Map.of(
                                    "version",
                                    3,
                                    "backup",
                                    backup.getFileName().toString(),
                                    "store",
                                    directory.getFileName().toString())));
            owner.flush();
        }
        if (marker.size() < 3) {
            Path backup = repository.backupOwned(id, owner);
            owner.append(
                    SessionEvent.session(
                            id,
                            "web.catalog.version",
                            Map.of(
                                    "version",
                                    4,
                                    "backup",
                                    backup.getFileName().toString(),
                                    "store",
                                    directory.getFileName().toString())));
            owner.flush();
        }
        scan(history -> count++);
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String recordId(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw error("INVALID_WEB_CATALOG");
        return "h-" + hash;
    }

    private boolean migrationMatches(SessionEvent stored, SessionEvent source, String id) {
        if (stored.payload().containsKey("migrationSourceHash"))
            return stored.eventId().equals(source.eventId())
                    && stored.sessionId().equals(id)
                    && sourceHash(source).equals(stored.payload().get("migrationSourceHash"));
        return stored.equals(rehome(source, id));
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put((String) key, canonical(item)));
            return sorted;
        }
        if (value instanceof List<?> list)
            return list.stream().map(DurableTaskCatalog::canonical).toList();
        return value;
    }

    private static String sourceHash(SessionEvent event) {
        try {
            return hash(
                    new String(
                            WebJson.encode(
                                    Map.of(
                                            "id",
                                            event.eventId(),
                                            "type",
                                            event.type(),
                                            "timestamp",
                                            event.timestamp().toString(),
                                            "payload",
                                            canonical(event.payload()))),
                            StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw error("INVALID_WEB_CATALOG");
        }
    }

    private SessionEvent migrationEvent(SessionEvent source, String id) {
        var event = rehome(source, id);
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("migrationSourceHash", sourceHash(source));
        return new SessionEvent(
                event.eventId(),
                id,
                event.type(),
                event.timestamp(),
                event.schemaVersion(),
                event.level(),
                event.turnId(),
                event.stepId(),
                event.callId(),
                payload);
    }

    private SessionEvent rehome(SessionEvent event, String id) {
        return new SessionEvent(
                event.eventId(),
                id,
                event.type(),
                event.timestamp(),
                event.schemaVersion(),
                event.level(),
                event.turnId(),
                event.stepId(),
                event.callId(),
                sanitizer.sanitize(event.payload()));
    }

    private void check() throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        var attrs =
                Files.readAttributes(
                        directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isDirectory()
                || attrs.isSymbolicLink()
                || attrs.isOther()
                || !directory.toRealPath().equals(directory.toAbsolutePath().normalize()))
            throw error("WEB_CATALOG_PATH_DENIED");
    }

    private synchronized void remember(List<SessionEvent> history) {
        if (history.isEmpty()) throw error("EMPTY_WEB_TASK_RECORD");
        String task = null;
        for (var event : history) {
            validate(event);
            if (task == null) task = (String) event.payload().get("taskId");
            if (!task.equals(event.payload().get("taskId"))) throw error("INVALID_WEB_CATALOG");
        }
        var p = history.getFirst().payload();
        String id = (String) p.get("taskId"), hash = (String) p.get("requestHash");
        if (!history.getFirst().type().equals("web.task.accepted")
                || id == null
                || !history.getFirst().sessionId().equals(recordId(hash)))
            throw error("INVALID_WEB_CATALOG");
        recent.remove(id);
        recent.put(id, hash);
        if (recent.size() > 32) recent.remove(recent.keySet().iterator().next());
    }

    void scan(Consumer<List<SessionEvent>> receive) {
        scanSnapshot(
                history -> {
                    synchronized (this) {
                        remember(history);
                    }
                    receive.accept(history);
                });
    }

    /**
     * Read-only bounded disk scan; does not mutate the admission cache or hold the HTTP monitor.
     */
    void scanSnapshot(Consumer<List<SessionEvent>> receive) {
        try {
            check();
            try (var files = Files.newDirectoryStream(directory, "h-*.jsonl")) {
                int seen = 0;
                for (Path file : files) {
                    if (++seen > 10000) throw error("WEB_HISTORY_QUOTA");
                    String id = file.getFileName().toString().replace(".jsonl", "");
                    if (!id.matches("h-[a-f0-9]{64}")) throw error("INVALID_WEB_CATALOG");
                    var history = records.openReader(id).events();
                    if (history.isEmpty()) throw error("EMPTY_WEB_TASK_RECORD");
                    receive.accept(history);
                }
            }
        } catch (IOException ex) {
            throw error("WEB_CATALOG_IO");
        }
    }

    synchronized List<SessionEvent> byHash(String hash) {
        try {
            check();
            String id = recordId(hash);
            if (!Files.exists(directory.resolve(id + ".jsonl"), LinkOption.NOFOLLOW_LINKS))
                return List.of();
            var history = records.openReader(id).events();
            remember(history);
            return history;
        } catch (IOException ex) {
            throw error("WEB_CATALOG_IO");
        }
    }

    synchronized List<SessionEvent> byId(String id) {
        if (recent.containsKey(id)) return byHash(recent.get(id));
        if (id.matches("web-[a-f0-9]{32}-[a-f0-9]{64}")) {
            var history = byHash(id.substring(37));
            return !history.isEmpty() && id.equals(history.getFirst().payload().get("taskId"))
                    ? history
                    : List.of();
        }
        var found = new ArrayList<SessionEvent>();
        scan(
                history -> {
                    if (id.equals(history.getFirst().payload().get("taskId")))
                        found.addAll(history);
                });
        if (!found.isEmpty()) remember(found);
        return found;
    }

    synchronized int count() {
        return count;
    }

    @Override
    public boolean durable() {
        return true;
    }

    @Override
    public synchronized String append(SessionEvent event) {
        owner.flush();
        validate(event);
        String id = (String) event.payload().get("taskId");
        String hash;
        boolean accepted = event.type().equals("web.task.accepted");
        if (accepted) {
            hash = (String) event.payload().get("requestHash");
            if (!byHash(hash).isEmpty()) throw error("REQUEST_ID_CONFLICT");
            if (count >= 10000) throw error("WEB_HISTORY_QUOTA");
        } else {
            var history = byId(id);
            if (history.isEmpty()) throw error("TASK_NOT_FOUND");
            hash = (String) history.getFirst().payload().get("requestHash");
        }
        try {
            check();
            try (var writer = records.openWriter(recordId(hash), WriteOwnership.EXCLUSIVE)) {
                writer.append(rehome(event, recordId(hash)));
                writer.flush();
            }
        } catch (IOException ex) {
            throw error("WEB_CATALOG_IO");
        }
        recent.put(id, hash);
        if (recent.size() > 32) recent.remove(recent.keySet().iterator().next());
        if (accepted) count++;
        return event.eventId();
    }

    @Override
    public void flush() {
        owner.flush();
    }

    @Override
    public void close() {
        owner.close();
    }

    static void validate(SessionEvent event) {
        var p = event.payload();
        if (!(p.get("taskId") instanceof String task)
                || !task.matches("[A-Za-z0-9_-]{1,128}")
                || !Set.of(
                                "web.task.accepted",
                                "web.task.finished",
                                "web.task.verified",
                                "web.task.changed",
                                "web.turn.accepted",
                                "web.turn.started",
                                "web.turn.finished",
                                "web.turn.cancelled",
                                "web.turn.paused",
                                "web.turn.interrupted")
                        .contains(event.type())) throw error("INVALID_WEB_CATALOG");
        if (event.type().equals("web.task.accepted")) {
            recordId(p.get("requestHash") instanceof String hash ? hash : null);
            if (!(p.get("profile") instanceof String profile)
                    || !profile.matches("[A-Za-z0-9_-]{1,32}")
                    || !(p.get("goalHash") instanceof String goal)
                    || !goal.matches("[a-f0-9]{64}")
                    || !(p.get("workspaceBinding") instanceof String binding)
                    || binding.isBlank()
                    || binding.length() > 1024) throw error("INVALID_WEB_CATALOG");
        }
        if (event.type().equals("web.task.finished")) {
            if (!(p.get("phase") instanceof String phase)
                    || !Set.of("FINISHED", "UNKNOWN").contains(phase)
                    || !(p.get("status") instanceof String status)
                    || !Set.of(
                                    "UNKNOWN",
                                    "CREATED",
                                    "RUNNING",
                                    "WAITING_APPROVAL",
                                    "EXECUTING_TOOL",
                                    "COMPLETED",
                                    "FAILED",
                                    "CANCELLED",
                                    "LIMIT_REACHED")
                            .contains(status)
                    || !(p.get("error") instanceof String)) throw error("INVALID_WEB_CATALOG");
        }
        if (event.type().equals("web.task.changed")
                && (!(p.get("name") instanceof String)
                        || !(p.get("pinned") instanceof Boolean)
                        || !(p.get("archived") instanceof Boolean)))
            throw error("INVALID_WEB_CATALOG");
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }
}
