package io.jharness.session;

import com.fasterxml.jackson.core.type.TypeReference;
import io.jharness.api.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded content-only index. Every cached body is checked against current sanitized facts. */
public final class TokenSessionSearch implements ContentSearchProvider {
    private static final int LIMIT = 8 * 1024 * 1024;
    private static final Set<Path> OWNERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> TYPES =
            Set.of(
                    "user.message",
                    "assistant.message",
                    "tool.completed",
                    "question.requested",
                    "question.resolved");
    private final Path root;
    private final SessionRepository repository;
    private final DataSanitizer sanitizer;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(
                                            com.fasterxml.jackson.core.StreamReadFeature
                                                    .STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            com.fasterxml.jackson.core.StreamReadConstraints
                                                    .builder()
                                                    .maxStringLength(LIMIT)
                                                    .maxNestingDepth(32)
                                                    .build())
                                    .build())
                    .enable(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .FAIL_ON_TRAILING_TOKENS);

    public TokenSessionSearch(Path directory, SessionRepository repository, DataSanitizer sanitizer)
            throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        for (Path parent = absolute; parent != null; parent = parent.getParent()) {
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                    && !parent.toRealPath().equals(parent))
                throw new IOException("Index ancestors must not be links");
        }
        Files.createDirectories(absolute);
        root = directory.toRealPath();
        if (!root.equals(directory.toAbsolutePath().normalize()))
            throw new IOException("Index root must not be a link");
        this.repository = Objects.requireNonNull(repository);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    private Path file(String id, String suffix) throws IOException {
        if (id == null
                || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || id.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))
            throw error("INVALID_SESSION_ID");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw error("SEARCH_PATH_DENIED");
        Path target = root.resolve(id + suffix);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw error("SEARCH_PATH_DENIED");
        return target;
    }

    @Override
    public synchronized Result search(
            String id,
            String text,
            String mode,
            int limit,
            boolean rebuild,
            CancellationToken cancellation)
            throws IOException {
        if (text == null
                || text.isBlank()
                || text.length() > 256
                || (mode == null || !Set.of("tokens", "phrase").contains(mode))
                || limit < 1
                || limit > 4096) throw error("INVALID_SEARCH");
        var needles = tokens(text);
        if (mode.equals("tokens") && (needles.isEmpty() || needles.size() > 32))
            throw error("INVALID_SEARCH");
        cancellation.throwIfCancelled();
        Path index = file(id, ".content.json"), lock = file(id, ".content.lock");
        if (!OWNERS.add(lock)) throw error("SEARCH_BUSY");
        try (var channel =
                        FileChannel.open(
                                lock,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS);
                var owned = channel.tryLock()) {
            if (owned == null) throw error("SEARCH_BUSY");
            Map<String, Object> cached = Map.of();
            if (!rebuild && Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
                try (var input = Files.newInputStream(index, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes = input.readNBytes(LIMIT + 1);
                    if (bytes.length <= LIMIT) {
                        var envelope =
                                JSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        var data = Values.object(envelope.get("data"));
                        if (Integer.valueOf(1).equals(data.get("version"))
                                && hash(JSON.writeValueAsBytes(data))
                                        .equals(envelope.get("sha256"))) cached = data;
                    }
                } catch (IOException | RuntimeException ignored) {
                    /* Disposable corruption is rebuilt. */
                }
            }
            List<?> previous = cached.get("documents") instanceof List<?> list ? list : List.of();
            var documents = new ArrayList<Map<String, Object>>();
            var events = repository.openReader(id).events();
            int reused = 0, bytes = 0;
            boolean prefix = true;
            for (var event : events) {
                cancellation.throwIfCancelled();
                if (!TYPES.contains(event.type())) continue;
                String content = body(sanitizer.sanitize(event.payload()), event.type());
                if (content.isBlank()) continue;
                bytes += content.getBytes(StandardCharsets.UTF_8).length;
                if (bytes > 4 * 1024 * 1024 || documents.size() >= 20000)
                    throw error("SEARCH_INDEX_LIMIT");
                var row =
                        Map.<String, Object>of(
                                "eventId",
                                event.eventId(),
                                "type",
                                event.type(),
                                "timestamp",
                                event.timestamp().toString(),
                                "turnId",
                                event.turnId() == null ? "" : event.turnId(),
                                "text",
                                content,
                                "sourceSha256",
                                hash(content.getBytes(StandardCharsets.UTF_8)));
                if (prefix
                        && documents.size() < previous.size()
                        && row.equals(previous.get(documents.size()))) reused++;
                else prefix = false;
                documents.add(row);
            }
            boolean changed =
                    cached.isEmpty()
                            || previous.size() != documents.size()
                            || reused != documents.size()
                            || rebuild;
            if (changed) {
                cancellation.throwIfCancelled();
                var data = Map.of("version", 1, "documents", documents);
                byte[] encoded =
                        JSON.writeValueAsBytes(
                                Map.of("data", data, "sha256", hash(JSON.writeValueAsBytes(data))));
                if (encoded.length > LIMIT) throw error("SEARCH_INDEX_LIMIT");
                Path temp = Files.createTempFile(root, "content-", ".tmp");
                try {
                    try (var output = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                        var buffer = ByteBuffer.wrap(encoded);
                        while (buffer.hasRemaining()) output.write(buffer);
                        output.force(true);
                    }
                    file(id, ".content.json");
                    Files.move(
                            temp,
                            index,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            var hits = new ArrayList<Hit>();
            String phrase = text.toLowerCase(Locale.ROOT);
            for (var document : documents) {
                cancellation.throwIfCancelled();
                String content = (String) document.get("text"),
                        lower = content.toLowerCase(Locale.ROOT);
                int score, found;
                if (mode.equals("phrase")) {
                    found = lower.indexOf(phrase);
                    score = found < 0 ? 0 : 100;
                } else {
                    var words = tokens(content);
                    if (!words.keySet().containsAll(needles.keySet())) continue;
                    score =
                            needles.keySet().stream()
                                    .mapToInt(w -> Math.min(100, words.get(w)))
                                    .sum();
                    found = lower.indexOf(needles.keySet().iterator().next());
                }
                if (score == 0) continue;
                if (hits.size() >= limit) throw error("SEARCH_RESULT_LIMIT");
                int start = Math.max(0, Math.min(found, content.length()) - 80);
                hits.add(
                        new Hit(
                                (String) document.get("eventId"),
                                (String) document.get("type"),
                                (String) document.get("timestamp"),
                                (String) document.get("turnId"),
                                content.substring(start, Math.min(content.length(), start + 400)),
                                score));
            }
            return new Result(
                    hits,
                    documents.size(),
                    reused,
                    cached.isEmpty()
                            || rebuild
                            || reused < Math.min(previous.size(), documents.size()));
        } catch (OverlappingFileLockException ex) {
            throw error("SEARCH_BUSY");
        } finally {
            OWNERS.remove(lock);
        }
    }

    private static String body(Map<String, Object> payload, String type) throws IOException {
        if (Set.of("user.message", "assistant.message").contains(type)) {
            Object content = payload.get("content");
            return content instanceof String text ? text : "";
        }
        return JSON.writeValueAsString(payload);
    }

    private static Map<String, Integer> tokens(String text) {
        var result = new LinkedHashMap<String, Integer>();
        var word = new StringBuilder();
        for (int cp : text.toLowerCase(Locale.ROOT).codePoints().toArray()) {
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                flush(word, result);
                result.merge(new String(Character.toChars(cp)), 1, Integer::sum);
            } else if (Character.isLetterOrDigit(cp) || cp == '_') word.appendCodePoint(cp);
            else flush(word, result);
        }
        flush(word, result);
        return result;
    }

    private static void flush(StringBuilder word, Map<String, Integer> result) {
        if (!word.isEmpty()) {
            result.merge(word.toString(), 1, Integer::sum);
            word.setLength(0);
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "Content search rejected");
    }
}
