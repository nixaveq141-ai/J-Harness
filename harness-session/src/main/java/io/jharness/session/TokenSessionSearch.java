package io.jharness.session;

import com.fasterxml.jackson.core.type.TypeReference;
import io.jharness.api.*;
import java.io.*;
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
                    "sdk.input.applied",
                    "assistant.message",
                    "tool.completed",
                    "question.requested",
                    "question.resolved");
    private final Path root;
    private final DirectoryIdentity identity;
    private final Object sanitizerLock = new Object();
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
        // Cache roots are private metadata, so bind an anchor rather than trusting
        // a Windows creation timestamp that can be reused for a replaced name.
        identity = IndexFiles.bind(root);
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
        IndexFiles.check(root, identity);
        Path target = root.resolve(id + suffix);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw error("SEARCH_PATH_DENIED");
        return target;
    }

    @Override
    public Result search(
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
                        IndexFiles.open(
                                root,
                                identity,
                                lock,
                                Set.of(
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS));
                var owned = channel.tryLock()) {
            if (owned == null) throw error("SEARCH_BUSY");
            Map<String, Object> cached = Map.of();
            if (!rebuild) {
                try (var input =
                        Channels.newInputStream(
                                IndexFiles.open(
                                        root,
                                        identity,
                                        index,
                                        Set.of(
                                                StandardOpenOption.READ,
                                                LinkOption.NOFOLLOW_LINKS)))) {
                    byte[] bytes = input.readNBytes(LIMIT + 1);
                    if (bytes.length <= LIMIT) {
                        var envelope =
                                JSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
                        var data = Values.object(envelope.get("data"));
                        if (Integer.valueOf(1).equals(data.get("version"))
                                && IndexFiles.digest(data, LIMIT).equals(envelope.get("sha256")))
                            cached = data;
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
                Map<String, Object> sanitized;
                synchronized (sanitizerLock) {
                    sanitized = sanitizer.sanitize(event.payload());
                }
                String content = body(sanitized, event.type());
                if (content.isBlank()) continue;
                bytes +=
                        io.jharness.api.JsonEncoding.utf8Length(
                                content, 4 * 1024 * 1024, "SEARCH_INDEX_LIMIT");
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
                IndexFiles.publish(
                        root, identity, index, Map.of("version", 1, "documents", documents), LIMIT);
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
                hits.add(
                        new Hit(
                                (String) document.get("eventId"),
                                (String) document.get("type"),
                                (String) document.get("timestamp"),
                                (String) document.get("turnId"),
                                UnicodeSearch.snippet(content, found, 80, 400),
                                score));
            }
            return new Result(hits, documents.size(), reused, changed);
        } catch (OverlappingFileLockException ex) {
            throw error("SEARCH_BUSY");
        } finally {
            OWNERS.remove(lock);
        }
    }

    private static String body(Map<String, Object> payload, String type) throws IOException {
        if ("sdk.input.applied".equals(type)) {
            Object message = payload.get("userMessage");
            return message instanceof Map<?, ?> row && row.get("content") instanceof String text
                    ? text
                    : "";
        }
        if (Set.of("user.message", "assistant.message").contains(type)) {
            Object content = payload.get("content");
            return content instanceof String text ? text : "";
        }
        return io.jharness.api.JsonEncoding.text(payload, 4 * 1024 * 1024, "SEARCH_INDEX_LIMIT");
    }

    private static Map<String, Integer> tokens(String text) {
        var result = new LinkedHashMap<String, Integer>();
        var word = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
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
