package io.jharness.session;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import io.jharness.api.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Disposable bounded full-payload substring index. JSONL remains the only authoritative fact. */
public final class SessionSearchIndex {
    private static final int LIMIT = 96 * 1024 * 1024;
    private static final Set<Path> OWNERS = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxStringLength(16 * 1024 * 1024)
                                                    .maxNestingDepth(32)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path root;
    private final SessionRepository repository;

    public SessionSearchIndex(Path directory, SessionRepository repository) throws IOException {
        Files.createDirectories(directory);
        root = directory.toRealPath();
        if (!root.equals(directory.toAbsolutePath().normalize()))
            throw new IOException("Search directory must not be a link");
        this.repository = Objects.requireNonNull(repository);
    }

    private Path file(String id, String suffix) throws IOException {
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || id.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid search session");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw new IOException("Search root changed");
        Path path = root.resolve(id + suffix);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Search file must be regular");
        return path;
    }

    public synchronized Map<String, Object> search(
            String id, String text, int offset, int limit, boolean rebuild) throws IOException {
        if (text == null
                || text.isBlank()
                || text.length() > 256
                || offset < 0
                || limit < 1
                || limit > 100)
            throw new HarnessException("INVALID_SEARCH", "Search text/page limit");
        Path index = file(id, ".search.json"), lock = file(id, ".search.lock");
        if (!OWNERS.add(lock)) throw new HarnessException("SEARCH_BUSY", "Index writer occupied");
        try (var channel =
                        FileChannel.open(
                                lock,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS);
                var ownership = channel.tryLock()) {
            if (ownership == null)
                throw new HarnessException("SEARCH_BUSY", "Index writer occupied");
            var events = repository.openReader(id).events();
            Map<String, Object> cached = Map.of();
            if (!rebuild && Files.exists(index)) {
                try {
                    if (Files.size(index) <= LIMIT) {
                        var bytes = read(index);
                        var envelope =
                                JSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        var data = Values.object(envelope.get("data"));
                        if (Integer.valueOf(1).equals(data.get("version"))
                                && hash(JSON.writeValueAsBytes(data))
                                        .equals(envelope.get("sha256"))) cached = data;
                    }
                } catch (IOException | RuntimeException ignored) {
                    /* Derived corruption is rebuilt, never repaired into the fact log. */
                }
            }
            var documents = new ArrayList<Map<String, Object>>();
            if (cached.get("documents") instanceof List<?> rows)
                rows.forEach(row -> documents.add(Values.object(row)));
            int reused = 0;
            while (reused < documents.size() && reused < events.size()) {
                var event = events.get(reused);
                if (!event.eventId().equals(documents.get(reused).get("eventId"))
                        || !hash(JSON.writeValueAsBytes(event.payload()))
                                .equals(documents.get(reused).get("sourceSha256"))) break;
                reused++;
            }
            while (documents.size() > reused) documents.removeLast();
            for (int i = reused; i < events.size(); i++) {
                var event = events.get(i);
                byte[] content = JSON.writeValueAsBytes(event.payload());
                documents.add(
                        Map.of(
                                "eventId",
                                event.eventId(),
                                "type",
                                event.type(),
                                "timestamp",
                                event.timestamp().toString(),
                                "sourceSha256",
                                hash(content),
                                "text",
                                new String(content, java.nio.charset.StandardCharsets.UTF_8)));
            }
            if (rebuild || reused != events.size() || cached.isEmpty()) {
                var data = Map.of("version", 1, "documents", documents);
                byte[] bytes =
                        JSON.writeValueAsBytes(
                                Map.of("data", data, "sha256", hash(JSON.writeValueAsBytes(data))));
                if (bytes.length > LIMIT)
                    throw new HarnessException("SEARCH_INDEX_LIMIT", "Derived index limit");
                Path temp = root.resolve("index-" + UUID.randomUUID() + ".tmp");
                try {
                    try (var out =
                            FileChannel.open(
                                    temp,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE)) {
                        var buffer = java.nio.ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) out.write(buffer);
                        out.force(true);
                    }
                    file(id, ".search.json");
                    Files.move(
                            temp,
                            index,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            String needle = text.toLowerCase(Locale.ROOT);
            var matches = new ArrayList<Map<String, Object>>();
            int total = 0;
            for (var document : documents) {
                String content = (String) document.get("text");
                int found = content.toLowerCase(Locale.ROOT).indexOf(needle);
                if (found < 0) continue;
                if (total++ < offset || matches.size() >= limit) continue;
                int start = Math.max(0, Math.min(found, content.length()) - 80);
                matches.add(
                        Map.of(
                                "eventId",
                                document.get("eventId"),
                                "type",
                                document.get("type"),
                                "timestamp",
                                document.get("timestamp"),
                                "snippet",
                                content.substring(start, Math.min(content.length(), start + 512))));
            }
            return Map.of(
                    "sessionId",
                    id,
                    "matches",
                    matches,
                    "total",
                    total,
                    "offset",
                    offset,
                    "indexedEvents",
                    documents.size(),
                    "reusedEvents",
                    reused,
                    "rebuilt",
                    cached.isEmpty() || rebuild);
        } catch (OverlappingFileLockException ex) {
            throw new HarnessException("SEARCH_BUSY", "Index writer occupied");
        } finally {
            OWNERS.remove(lock);
        }
    }

    private static byte[] read(Path path) throws IOException {
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("Index limit");
            return bytes;
        }
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
