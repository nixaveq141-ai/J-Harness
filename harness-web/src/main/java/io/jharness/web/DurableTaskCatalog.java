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
                        if (!existing.get(i).equals(rehome(history.get(i), recordId)))
                            throw error("MIGRATION_CONFLICT");
                    for (int i = existing.size(); i < history.size(); i++)
                        writer.append(rehome(history.get(i), recordId));
                    writer.flush();
                }
            }
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

    private void remember(List<SessionEvent> history) {
        if (history.isEmpty()) return;
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
                    if (!history.isEmpty()) {
                        receive.accept(history);
                    }
                }
            }
        } catch (IOException ex) {
            throw error("WEB_CATALOG_IO");
        }
    }

    List<SessionEvent> byHash(String hash) {
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

    List<SessionEvent> byId(String id) {
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
        remember(found);
        return found;
    }

    int count() {
        return count;
    }

    @Override
    public boolean durable() {
        return true;
    }

    @Override
    public synchronized String append(SessionEvent event) {
        owner.flush();
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

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }
}
