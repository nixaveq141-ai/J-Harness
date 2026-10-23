package io.jharness.session;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import io.jharness.api.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Exclusive file lock + complete JSONL records + force(true); poisoned writers never continue. */
public final class JsonlSessionRepository implements SessionRepository {
    public enum TailPolicy {
        REJECT,
        REPAIR_TAIL
    }

    @FunctionalInterface
    public interface DurabilityBarrier {
        void force(FileChannel channel) throws IOException;
    }

    private static final int RECORD_LIMIT = 8 * 1024 * 1024;
    private static final int LOG_LIMIT = 32 * 1024 * 1024;
    private static final Map<Path, Object> GATES = new ConcurrentHashMap<>();
    // POSIX locks are process-wide: closing even a failed contender's channel can drop the owner.
    private static final Set<Path> OWNERS = ConcurrentHashMap.newKeySet();
    private final Path root;
    private final TailPolicy tailPolicy;
    private final DurabilityBarrier barrier;
    private final Consumer<String> diagnostics;
    private final ObjectMapper json =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(64)
                                                    .maxStringLength(RECORD_LIMIT)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public JsonlSessionRepository(Path root) throws IOException {
        this(root, TailPolicy.REJECT, channel -> channel.force(true), code -> {});
    }

    /** Physical-provider composition only; callers must retain normal repository ownership. */
    public Path directory() {
        return root;
    }

    /** Exact-byte checkpoint while the supplied writer already owns this log. */
    public Path backupOwned(String id, SessionWriter owner) {
        if (!(owner instanceof JsonlSessionRepository.Writer writer)
                || !writer.path.equals(file(id, ".jsonl"))) throw error("OWNERSHIP_REQUIRED");
        synchronized (gate(writer.path)) {
            writer.valid();
            Path backup = root.resolve(id + ".backup-" + UUID.randomUUID() + ".bak");
            try {
                forcedFile(backup, bytes(writer.path));
                return backup;
            } catch (IOException ex) {
                throw error("SESSION_BACKUP_FAILED");
            }
        }
    }

    public JsonlSessionRepository(
            Path root, TailPolicy policy, DurabilityBarrier barrier, Consumer<String> diagnostics)
            throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        this.tailPolicy = Objects.requireNonNull(policy);
        this.barrier = Objects.requireNonNull(barrier);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    private Path file(String id, String suffix) {
        if (id == null
                || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || id.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))
            throw new HarnessException("INVALID_SESSION_ID", "Unsafe session id");
        Path path = root.resolve(id + suffix);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException(
                    "SESSION_PATH_DENIED", "Session file must be regular and not a link");
        return path;
    }

    private static Object gate(Path path) {
        return GATES.computeIfAbsent(path, ignored -> new Object());
    }

    @Override
    public SessionWriter openWriter(String id, WriteOwnership ownership) {
        Objects.requireNonNull(ownership);
        Path log = file(id, ".jsonl"), lockPath = file(id, ".lock");
        if (!OWNERS.add(lockPath)) throw error("WRITE_CONFLICT");
        FileChannel lockChannel = null, channel = null;
        FileLock lock = null;
        try {
            lockChannel =
                    FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            try {
                lock = lockChannel.tryLock();
            } catch (OverlappingFileLockException ex) {
                throw error("WRITE_CONFLICT");
            }
            if (lock == null) throw error("WRITE_CONFLICT");
            synchronized (gate(log)) {
                channel =
                        FileChannel.open(
                                log,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS);
                Parsed parsed;
                if (channel.size() == 0) {
                    byte[] header =
                            encode(
                                    Map.of(
                                            "format",
                                            "jharness-jsonl",
                                            "version",
                                            1,
                                            "sessionId",
                                            id));
                    write(channel, header);
                    barrier.force(channel);
                    parsed = new Parsed(List.of(), header.length, false, 0);
                } else {
                    parsed = parse(log, id);
                    if (parsed.damaged) {
                        if (tailPolicy == TailPolicy.REJECT) throw error("TAIL_DAMAGED");
                        channel.truncate(parsed.validBytes);
                        barrier.force(channel);
                        diagnose("TAIL_REPAIRED");
                    }
                }
                channel.position(channel.size());
                return new Writer(id, log, channel, lockChannel, lock, parsed.events, parsed.nodes);
            }
        } catch (IOException ex) {
            closeQuietly(channel);
            closeQuietly(lock);
            closeQuietly(lockChannel);
            OWNERS.remove(lockPath);
            diagnose("CHECKPOINT_FAILED");
            throw error("CHECKPOINT_FAILED");
        } catch (RuntimeException ex) {
            closeQuietly(channel);
            closeQuietly(lock);
            closeQuietly(lockChannel);
            OWNERS.remove(lockPath);
            throw ex;
        }
    }

    @Override
    public SessionReader openReader(String id) {
        Path log = file(id, ".jsonl");
        return () -> {
            synchronized (gate(log)) {
                try {
                    if (!Files.exists(log)) throw error("SESSION_NOT_FOUND");
                    Parsed parsed = parse(log, id);
                    if (parsed.damaged) throw error("TAIL_DAMAGED");
                    return parsed.events;
                } catch (IOException ex) {
                    throw error("SESSION_READ_FAILED");
                }
            }
        };
    }

    public record Inspection(int formatVersion, boolean writable, String diagnostic) {}

    /** Future formats are reportable as metadata only; their event payloads are never guessed. */
    public Inspection inspect(String id) {
        Path path = file(id, ".jsonl");
        synchronized (gate(path)) {
            try {
                byte[] bytes = bytes(path);
                int newline = newline(bytes, 0);
                if (newline < 0) throw error("HEADER_DAMAGED");
                Map<String, Object> header = decode(bytes, 0, newline);
                if (!"jharness-jsonl".equals(header.get("format"))
                        || !id.equals(header.get("sessionId"))) throw error("HEADER_DAMAGED");
                int version = integer(header, "version");
                if (version != 1)
                    return new Inspection(
                            version,
                            false,
                            version > 1 ? "FUTURE_VERSION_READ_ONLY" : "MIGRATION_REQUIRED");
                Parsed parsed = parse(path, id);
                return new Inspection(
                        1, !parsed.damaged, parsed.damaged ? "TAIL_DAMAGED" : "VALID");
            } catch (IOException ex) {
                throw error("SESSION_READ_FAILED");
            }
        }
    }

    @Override
    public List<String> list() {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.jsonl")) {
            List<String> ids = new ArrayList<>();
            for (Path path : files)
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    String name = path.getFileName().toString();
                    if (ids.size() >= 10000) throw error("SESSION_CATALOG_LIMIT");
                    ids.add(name.substring(0, name.length() - 6));
                }
            Collections.sort(ids);
            return List.copyOf(ids);
        } catch (IOException ex) {
            throw error("SESSION_READ_FAILED");
        }
    }

    @Override
    public String fork(String id, String eventId) {
        return fork(id, eventId, target -> {});
    }

    /** Snapshot with inherited event IDs, preserving request/evidence references. */
    public String fork(String id, String eventId, Consumer<String> prepareArtifacts) {
        return forkTo(id, eventId, "s-" + UUID.randomUUID(), prepareArtifacts);
    }

    /** Deterministic destination for host-owned idempotent snapshot admission. */
    public String forkTo(
            String id, String eventId, String target, Consumer<String> prepareArtifacts) {
        return forkTo(id, eventId, target, prepareArtifacts, ignored -> {});
    }

    public String forkTo(
            String id,
            String eventId,
            String target,
            Consumer<String> prepareArtifacts,
            Consumer<String> rollbackArtifacts) {
        file(target, ".jsonl");
        if (id.equals(target)) throw error("SESSION_EXISTS");
        try (Maintenance ownership = maintenance(id);
                Maintenance destination = ownership(target)) {
            ownership.ensureValid();
            destination.ensureValid();
            if (Files.exists(file(target, ".jsonl"), LinkOption.NOFOLLOW_LINKS))
                throw error("SESSION_EXISTS");
            Path source = file(id, ".jsonl");
            synchronized (gate(source)) {
                Parsed parsed = parse(source, id);
                if (parsed.damaged) throw error("TAIL_DAMAGED");
                int end = -1;
                for (int i = 0; i < parsed.events.size(); i++)
                    if (parsed.events.get(i).eventId().equals(eventId)) end = i;
                if (end < 0) throw error("EVENT_NOT_FOUND");
                byte[] snapshot = bytes(source);
                List<Map<String, Object>> prefix = new ArrayList<>();
                int start = 0;
                for (int i = 0; i < end + 2; i++) {
                    int lineEnd = newline(snapshot, start);
                    var copy = new LinkedHashMap<>(decode(snapshot, start, lineEnd));
                    copy.put("sessionId", target);
                    prefix.add(copy);
                    start = lineEnd + 1;
                }
                prefix.add(
                        Map.of(
                                "eventId",
                                UUID.randomUUID().toString(),
                                "sessionId",
                                target,
                                "type",
                                "session.forked",
                                "timestamp",
                                Instant.now().toString(),
                                "schemaVersion",
                                1,
                                "level",
                                "SESSION",
                                "payload",
                                Map.of("parentSessionId", id, "fromEventId", eventId)));
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                for (var record : prefix) {
                    byte[] encoded = encode(record);
                    encoded[encoded.length - 1] = '\n';
                    buffer.write(encoded);
                    if (buffer.size() > LOG_LIMIT) throw error("SESSION_LIMIT");
                }
                if (end + 2 > 10000) throw error("SESSION_LIMIT");
                byte[] candidate = buffer.toByteArray();
                // Validate every budget, schema and association before copying any artifacts.
                Path validation = root.resolve(".fork-check-" + UUID.randomUUID());
                try {
                    forcedFile(validation, candidate);
                    if (parse(validation, target).damaged) throw error("TAIL_DAMAGED");
                } finally {
                    Files.deleteIfExists(validation);
                }
                prepareArtifacts.accept(target);
                try {
                    publish(target, candidate);
                } catch (IOException | RuntimeException failure) {
                    try {
                        rollbackArtifacts.accept(target);
                    } catch (RuntimeException cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                return target;
            }
        } catch (IOException ex) {
            throw new HarnessException("SESSION_WRITE_FAILED", "SESSION_WRITE_FAILED", ex);
        }
    }

    /** Portable JSONL snapshot. Destination must not exist. */
    public void export(String id, Path destination) {
        Path source = file(id, ".jsonl");
        synchronized (gate(source)) {
            try (Maintenance ownership = maintenance(id)) {
                ownership.ensureValid();
                Parsed parsed = parse(source, id);
                if (parsed.damaged) throw error("TAIL_DAMAGED");
                forcedFile(destination, bytes(source));
            } catch (IOException ex) {
                throw new HarnessException("SESSION_EXPORT_FAILED", "SESSION_EXPORT_FAILED", ex);
            }
        }
    }

    public String migrate(String id) {
        return maintain(id, false);
    }

    public String repairTail(String id) {
        return maintain(id, true);
    }

    /** Legacy v0 differs only in header version; event schema must already be v1. */
    private String maintain(String id, boolean repair) {
        try (Maintenance ownership = maintenance(id)) {
            ownership.ensureValid();
            Path source = file(id, ".jsonl");
            synchronized (gate(source)) {
                byte[] original = bytes(source);
                int end = newline(original, 0);
                if (end < 0) throw error("HEADER_DAMAGED");
                var header = decode(original, 0, end);
                if (!"jharness-jsonl".equals(header.get("format"))
                        || !id.equals(header.get("sessionId"))) throw error("HEADER_DAMAGED");
                int version = integer(header, "version");
                if (version > 1) throw error("FUTURE_VERSION_READ_ONLY");
                if (!repair && version == 1) {
                    Parsed current = parse(source, id);
                    if (current.damaged) throw error("TAIL_DAMAGED");
                    return "ALREADY_CURRENT";
                }
                if (repair && version != 1 || !repair && version != 0)
                    throw error("UNSUPPORTED_MIGRATION");
                byte[] candidate = original;
                if (!repair) {
                    var current = new LinkedHashMap<>(header);
                    current.put("version", 1);
                    byte[] encoded = encode(current);
                    encoded[encoded.length - 1] = '\n';
                    candidate = new byte[encoded.length + original.length - end - 1];
                    System.arraycopy(encoded, 0, candidate, 0, encoded.length);
                    System.arraycopy(
                            original,
                            end + 1,
                            candidate,
                            encoded.length,
                            original.length - end - 1);
                }
                Path temp = root.resolve(".maintenance-" + UUID.randomUUID());
                try {
                    forcedFile(temp, candidate);
                    Parsed parsed = parse(temp, id);
                    if (parsed.damaged && !repair) throw error("TAIL_DAMAGED");
                    if (repair && !parsed.damaged) return "ALREADY_VALID";
                    if (repair) {
                        Files.delete(temp);
                        forcedFile(temp, Arrays.copyOf(candidate, parsed.validBytes));
                        parse(temp, id);
                    }
                    String backup = id + ".backup-" + UUID.randomUUID() + ".bak";
                    forcedFile(root.resolve(backup), original);
                    Files.move(
                            temp,
                            source,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    return backup;
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException ex) {
            throw new HarnessException(
                    "SESSION_MAINTENANCE_FAILED", "SESSION_MAINTENANCE_FAILED", ex);
        }
    }

    private void forcedFile(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel =
                FileChannel.open(
                        path,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            barrier.force(channel);
        }
    }

    private void publish(String id, byte[] bytes) throws IOException {
        Path temp = root.resolve(".fork-" + UUID.randomUUID());
        try {
            forcedFile(temp, bytes);
            Parsed parsed = parse(temp, id);
            if (parsed.damaged) throw error("TAIL_DAMAGED");
            Files.move(temp, file(id, ".jsonl"), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Maintenance maintenance(String id) throws IOException {
        if (!Files.exists(file(id, ".jsonl"))) throw error("SESSION_NOT_FOUND");
        return ownership(id);
    }

    private Maintenance ownership(String id) throws IOException {
        Path lockPath = file(id, ".lock");
        if (!OWNERS.add(lockPath)) throw error("WRITE_CONFLICT");
        FileChannel channel = null;
        try {
            channel =
                    FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.tryLock();
            if (lock == null) throw error("WRITE_CONFLICT");
            return new Maintenance(lockPath, channel, lock);
        } catch (IOException | RuntimeException ex) {
            closeQuietly(channel);
            OWNERS.remove(lockPath);
            if (ex instanceof OverlappingFileLockException) throw error("WRITE_CONFLICT");
            throw ex;
        }
    }

    private record Maintenance(Path path, FileChannel channel, FileLock lock)
            implements AutoCloseable {
        void ensureValid() {
            if (!lock.isValid()) throw error("WRITE_CONFLICT");
        }

        @Override
        public void close() {
            closeQuietly(lock);
            closeQuietly(channel);
            OWNERS.remove(path);
        }
    }

    private Parsed parse(Path path, String id) throws IOException {
        byte[] bytes = bytes(path);
        int headerEnd = newline(bytes, 0);
        if (headerEnd < 0) throw error("HEADER_DAMAGED");
        Map<String, Object> header = decode(bytes, 0, headerEnd);
        if (!"jharness-jsonl".equals(header.get("format")) || !id.equals(header.get("sessionId")))
            throw error("HEADER_DAMAGED");
        int version = integer(header, "version");
        if (version > 1) throw error("FUTURE_VERSION_READ_ONLY");
        if (version < 1) throw error("MIGRATION_REQUIRED");
        List<SessionEvent> events = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int[] nodeBudget = {0};
        int start = headerEnd + 1, valid = start, validNodes = 0;
        while (start < bytes.length) {
            if (events.size() >= 10000) throw error("SESSION_LIMIT");
            int end = newline(bytes, start);
            if (end < 0) return new Parsed(List.copyOf(events), valid, true, validNodes);
            try {
                Map<String, Object> record = decode(bytes, start, end, nodeBudget);
                int schema = integer(record, "schemaVersion");
                if (schema > 1) throw error("FUTURE_VERSION_READ_ONLY");
                if (schema < 1) throw error("UNSUPPORTED_EVENT_SCHEMA");
                SessionEvent event =
                        new SessionEvent(
                                text(record, "eventId"),
                                text(record, "sessionId"),
                                text(record, "type"),
                                Instant.parse(text(record, "timestamp")),
                                schema,
                                EventLevel.valueOf(text(record, "level")),
                                optional(record, "turnId"),
                                optional(record, "stepId"),
                                optional(record, "callId"),
                                Values.object(record.get("payload")));
                if (!id.equals(event.sessionId())) throw error("SESSION_MISMATCH");
                if (!ids.add(event.eventId())) throw error("DUPLICATE_EVENT");
                events.add(event);
                valid = end + 1;
                validNodes = nodeBudget[0];
            } catch (HarnessException ex) {
                if (Set.of(
                                "FUTURE_VERSION_READ_ONLY",
                                "UNSUPPORTED_EVENT_SCHEMA",
                                "SESSION_MISMATCH",
                                "DUPLICATE_EVENT",
                                "RECORD_LIMIT")
                        .contains(ex.code())) throw ex;
                if (end != bytes.length - 1) throw error("MID_LOG_DAMAGED");
                return new Parsed(List.copyOf(events), valid, true, validNodes);
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
                if (end != bytes.length - 1) throw error("MID_LOG_DAMAGED");
                return new Parsed(List.copyOf(events), valid, true, validNodes);
            }
            start = end + 1;
        }
        return new Parsed(List.copyOf(events), valid, false, validNodes);
    }

    private byte[] bytes(Path path) throws IOException {
        if (Files.size(path) > LOG_LIMIT) throw error("SESSION_LIMIT");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(LOG_LIMIT + 1);
            if (bytes.length > LOG_LIMIT) throw error("SESSION_LIMIT");
            return bytes;
        }
    }

    private Map<String, Object> decode(byte[] bytes, int start, int end) {
        return decode(bytes, start, end, new int[] {0});
    }

    private Map<String, Object> decode(byte[] bytes, int start, int end, int[] aggregate) {
        if (end - start > RECORD_LIMIT) throw error("RECORD_LIMIT");
        try {
            checkNodes(bytes, start, end, aggregate);
            return Values.object(
                    json.readValue(
                            Arrays.copyOfRange(bytes, start, end),
                            new TypeReference<Map<String, Object>>() {}));
        } catch (IOException ex) {
            throw error("INVALID_RECORD");
        }
    }

    private void checkNodes(byte[] bytes, int start, int end, int[] aggregate) throws IOException {
        try (JsonParser parser = json.getFactory().createParser(bytes, start, end - start)) {
            int nodes = 0;
            while (parser.nextToken() != null) {
                if (++nodes > 100000 || ++aggregate[0] > 250000) throw error("RECORD_LIMIT");
            }
        }
    }

    private byte[] encode(Map<String, Object> record) throws IOException {
        byte[] bytes = json.writeValueAsBytes(record);
        if (bytes.length > RECORD_LIMIT) throw error("RECORD_LIMIT");
        checkNodes(bytes, 0, bytes.length, new int[] {0});
        return Arrays.copyOf(
                bytes, bytes.length + 1); // Last byte set below, no embedded raw newlines in JSON.
    }

    private static void write(FileChannel channel, byte[] bytes) throws IOException {
        bytes[bytes.length - 1] = '\n';
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    private static int newline(byte[] bytes, int from) {
        for (int i = from; i < bytes.length; i++) if (bytes[i] == '\n') return i;
        return -1;
    }

    private static int integer(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof Integer value)) throw error("INVALID_RECORD");
        return value;
    }

    private static String text(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String value) || value.isBlank())
            throw error("INVALID_RECORD");
        return value;
    }

    private static String optional(Map<String, Object> map, String key) {
        return map.get(key) == null ? null : text(map, key);
    }

    private void diagnose(String code) {
        try {
            diagnostics.accept(code);
        } catch (RuntimeException ignored) {
        }
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null)
            try {
                resource.close();
            } catch (Exception ignored) {
            }
    }

    private record Parsed(List<SessionEvent> events, int validBytes, boolean damaged, int nodes) {}

    private final class Writer implements SessionWriter {
        private final String id;
        private final Path path;
        private final FileChannel channel, ownership;
        private final FileLock lock;
        private final Set<String> eventIds = new HashSet<>();
        private boolean closed, poisoned;
        private int nodes;

        Writer(
                String id,
                Path path,
                FileChannel channel,
                FileChannel ownership,
                FileLock lock,
                List<SessionEvent> events,
                int nodes) {
            this.id = id;
            this.path = path;
            this.channel = channel;
            this.ownership = ownership;
            this.lock = lock;
            this.nodes = nodes;
            events.forEach(event -> eventIds.add(event.eventId()));
        }

        @Override
        public boolean durable() {
            return true;
        }

        private void valid() {
            if (closed || poisoned) throw error(closed ? "WRITER_CLOSED" : "WRITER_POISONED");
        }

        @Override
        public String append(SessionEvent event) {
            synchronized (gate(path)) {
                valid();
                if (!id.equals(event.sessionId())) throw error("SESSION_MISMATCH");
                if (event.schemaVersion() != 1) throw error("FUTURE_VERSION_READ_ONLY");
                if (eventIds.contains(event.eventId())) throw error("DUPLICATE_EVENT");
                if (eventIds.size() >= 10000) throw error("SESSION_LIMIT");
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("eventId", event.eventId());
                record.put("sessionId", id);
                record.put("type", event.type());
                record.put("timestamp", event.timestamp().toString());
                record.put("schemaVersion", event.schemaVersion());
                record.put("level", event.level().name());
                record.put("turnId", event.turnId());
                record.put("stepId", event.stepId());
                record.put("callId", event.callId());
                record.put("payload", event.payload());
                try {
                    byte[] bytes = encode(record);
                    int[] nextNodes = {nodes};
                    checkNodes(bytes, 0, bytes.length - 1, nextNodes);
                    if (channel.size() + bytes.length > LOG_LIMIT) throw error("SESSION_LIMIT");
                    write(channel, bytes);
                    barrier.force(channel);
                    eventIds.add(event.eventId());
                    nodes = nextNodes[0];
                    return event.eventId();
                } catch (IOException ex) {
                    poisoned = true;
                    diagnose("CHECKPOINT_FAILED");
                    throw error("CHECKPOINT_FAILED");
                }
            }
        }

        @Override
        public void flush() {
            synchronized (gate(path)) {
                valid();
                try {
                    barrier.force(channel);
                } catch (IOException ex) {
                    poisoned = true;
                    diagnose("CHECKPOINT_FAILED");
                    throw error("CHECKPOINT_FAILED");
                }
            }
        }

        @Override
        public void close() {
            synchronized (gate(path)) {
                if (closed) return;
                closed = true;
                closeQuietly(channel);
                closeQuietly(lock);
                closeQuietly(ownership);
                OWNERS.remove(path.resolveSibling(id + ".lock"));
            }
        }
    }
}
