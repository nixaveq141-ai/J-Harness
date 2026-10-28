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
    private final DirectoryIdentity identity;
    private final SessionRepository repository;

    public SessionSearchIndex(Path directory, SessionRepository repository) throws IOException {
        Files.createDirectories(directory);
        root = directory.toRealPath();
        if (!root.equals(directory.toAbsolutePath().normalize()))
            throw new IOException("Search directory must not be a link");
        // Cache roots are private metadata, so bind an anchor rather than trusting
        // a Windows creation timestamp that can be reused for a replaced name.
        identity = IndexFiles.bind(root);
        this.repository = Objects.requireNonNull(repository);
    }

    private Path file(String id, String suffix) throws IOException {
        if (id == null
                || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || id.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid search session");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw new IOException("Search root changed");
        IndexFiles.check(root, identity);
        Path path = root.resolve(id + suffix);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Search file must be regular");
        return path;
    }

    public Map<String, Object> search(
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
                        IndexFiles.open(
                                root,
                                identity,
                                lock,
                                Set.of(
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS));
                var ownership = channel.tryLock()) {
            if (ownership == null)
                throw new HarnessException("SEARCH_BUSY", "Index writer occupied");
            var events = repository.openReader(id).events();
            Map<String, Object> cached = Map.of();
            if (!rebuild) {
                try {
                    {
                        var bytes = read(index);
                        var envelope =
                                JSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        var data = Values.object(envelope.get("data"));
                        if (Integer.valueOf(1).equals(data.get("version"))
                                && IndexFiles.digest(data, LIMIT).equals(envelope.get("sha256"))) {
                            if (!(data.get("documents") instanceof List<?> rows))
                                throw new IOException("Index rows");
                            for (Object raw : rows) {
                                var row = Values.object(raw);
                                if (!(row.get("eventId") instanceof String)
                                        || !(row.get("type") instanceof String)
                                        || !(row.get("timestamp") instanceof String)
                                        || !(row.get("text") instanceof String body)
                                        || !hash(body.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8))
                                                .equals(row.get("sourceSha256")))
                                    throw new IOException("Index row");
                            }
                            cached = data;
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    /* Derived corruption is rebuilt, never repaired into the fact log. */
                }
            }
            var documents = new ArrayList<Map<String, Object>>();
            if (cached.get("documents") instanceof List<?> rows)
                rows.forEach(row -> documents.add(Values.object(row)));
            int cachedCount = documents.size();
            int reused = 0;
            while (reused < documents.size() && reused < events.size()) {
                var event = events.get(reused);
                if (!event.type().equals(documents.get(reused).get("type"))
                        || !event.timestamp()
                                .toString()
                                .equals(documents.get(reused).get("timestamp"))
                        || !event.eventId().equals(documents.get(reused).get("eventId"))
                        || !hash(io.jharness.api.JsonEncoding.encode(
                                        event.payload(),
                                        8 * 1024 * 1024,
                                        100000,
                                        32,
                                        "SEARCH_INDEX_LIMIT"))
                                .equals(documents.get(reused).get("sourceSha256"))) break;
                reused++;
            }
            while (documents.size() > reused) documents.removeLast();
            for (int i = reused; i < events.size(); i++) {
                var event = events.get(i);
                byte[] content =
                        io.jharness.api.JsonEncoding.encode(
                                event.payload(), 8 * 1024 * 1024, 100000, 32, "SEARCH_INDEX_LIMIT");
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
            boolean changed =
                    rebuild
                            || reused != events.size()
                            || cached.isEmpty()
                            || cachedCount != events.size();
            if (changed)
                IndexFiles.publish(
                        root, identity, index, Map.of("version", 1, "documents", documents), LIMIT);
            String needle = text.toLowerCase(Locale.ROOT);
            var matches = new ArrayList<Map<String, Object>>();
            int total = 0;
            for (var document : documents) {
                String content = (String) document.get("text");
                int found = content.toLowerCase(Locale.ROOT).indexOf(needle);
                if (found < 0) continue;
                if (total++ < offset || matches.size() >= limit) continue;
                matches.add(
                        Map.of(
                                "eventId",
                                document.get("eventId"),
                                "type",
                                document.get("type"),
                                "timestamp",
                                document.get("timestamp"),
                                "snippet",
                                UnicodeSearch.snippet(content, found, 80, 512)));
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
                    changed);
        } catch (OverlappingFileLockException ex) {
            throw new HarnessException("SEARCH_BUSY", "Index writer occupied");
        } finally {
            OWNERS.remove(lock);
        }
    }

    private byte[] read(Path path) throws IOException {
        try (var input =
                Channels.newInputStream(
                        IndexFiles.open(
                                root,
                                identity,
                                path,
                                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)))) {
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
