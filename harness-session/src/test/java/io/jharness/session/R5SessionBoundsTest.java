package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R5SessionBoundsTest {
    @TempDir Path root;

    private static final class SnapshotRepository implements SessionRepository {
        volatile List<SessionEvent> events = List.of();

        public SessionReader openReader(String id) {
            return () -> events;
        }

        public List<String> list() {
            return List.of("s");
        }

        public SessionWriter openWriter(String id, WriteOwnership ownership) {
            throw new UnsupportedOperationException();
        }

        public String fork(String id, String event) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void shortenedSourcePersistsBothIndexesAndMalformedCachedRowsAreDisposable() throws Exception {
        var repo = new SnapshotRepository();
        var first =
                SessionEvent.session(
                        "s", "user.message", Map.of("role", "user", "content", "alpha first"));
        repo.events =
                List.of(
                        first,
                        SessionEvent.session(
                                "s",
                                "user.message",
                                Map.of("role", "user", "content", "alpha second")));
        var raw = new SessionSearchIndex(root.toRealPath().resolve("raw"), repo);
        var token =
                new TokenSessionSearch(
                        root.toRealPath().resolve("token"), repo, new SecretRedactor(List.of()));
        raw.search("s", "alpha", 0, 10, false);
        token.search("s", "alpha", "tokens", 10, false, CancellationToken.NONE);
        repo.events = List.of(first);
        assertEquals(true, raw.search("s", "alpha", 0, 10, false).get("rebuilt"));
        assertTrue(
                token.search("s", "alpha", "tokens", 10, false, CancellationToken.NONE).rebuilt());
        assertEquals(1, raw.search("s", "alpha", 0, 10, false).get("reusedEvents"));
        assertEquals(
                1,
                token.search("s", "alpha", "tokens", 10, false, CancellationToken.NONE)
                        .reusedEvents());
        var malformed = Map.of("version", 1, "documents", List.of("invalid row"));
        var envelope = Map.of("data", malformed, "sha256", IndexFiles.digest(malformed, 100000));
        Files.write(
                root.resolve("raw/s.search.json"), new ObjectMapper().writeValueAsBytes(envelope));
        assertEquals(1, raw.search("s", "alpha", 0, 10, false).get("total"));
        assertEquals(false, raw.search("s", "alpha", 0, 10, false).get("rebuilt"));
    }

    @Test
    void bothSearchSnippetsUseOriginalUnicodeCoordinates() throws Exception {
        var repo = new SnapshotRepository();
        repo.events =
                List.of(
                        SessionEvent.session(
                                "s",
                                "user.message",
                                Map.of("content", "İ".repeat(500) + "😀needle" + "z".repeat(900))));
        var raw = new SessionSearchIndex(root.toRealPath().resolve("raw"), repo);
        var token =
                new TokenSessionSearch(
                        root.toRealPath().resolve("token"), repo, new SecretRedactor(List.of()));
        var match =
                Values.object(
                        ((List<?>) raw.search("s", "needle", 0, 10, false).get("matches"))
                                .getFirst());
        assertTrue(((String) match.get("snippet")).contains("😀needle"));
        assertTrue(
                token.search("s", "needle", "phrase", 10, false, CancellationToken.NONE)
                        .hits()
                        .getFirst()
                        .snippet()
                        .contains("😀needle"));
    }

    @Test
    void differentSessionsReadConcurrentlyWhileSanitizerCallsStaySerialized() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        SessionRepository repo =
                new SessionRepository() {
                    public SessionReader openReader(String id) {
                        return () -> {
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("reader barrier");
                            } catch (InterruptedException e) {
                                throw new AssertionError(e);
                            }
                            return List.of(
                                    SessionEvent.session(
                                            id, "user.message", Map.of("content", "alpha")));
                        };
                    }

                    public List<String> list() {
                        return List.of("a", "b");
                    }

                    public SessionWriter openWriter(String id, WriteOwnership o) {
                        throw new UnsupportedOperationException();
                    }

                    public String fork(String id, String e) {
                        throw new UnsupportedOperationException();
                    }
                };
        var index = new TokenSessionSearch(root.toRealPath(), repo, new SecretRedactor(List.of()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a =
                    executor.submit(
                            () ->
                                    index.search(
                                            "a",
                                            "alpha",
                                            "tokens",
                                            10,
                                            false,
                                            CancellationToken.NONE));
            var b =
                    executor.submit(
                            () ->
                                    index.search(
                                            "b",
                                            "alpha",
                                            "tokens",
                                            10,
                                            false,
                                            CancellationToken.NONE));
            try {
                assertTrue(
                        entered.await(5, TimeUnit.SECONDS),
                        "Different sessions must reach their reader independently");
            } finally {
                release.countDown();
            }
            assertEquals(1, a.get(5, TimeUnit.SECONDS).hits().size());
            assertEquals(1, b.get(5, TimeUnit.SECONDS).hits().size());
        }
    }

    @Test
    void memoryCountAndByteRejectionsDoNotConsumeEventIdsOrPublishFailedForks() {
        var repo = new InMemorySessionRepository(new InMemorySessionRepository.Limits(2, 2000));
        var first = SessionEvent.session("s", "user.message", Map.of("content", "small"));
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(first);
            var huge =
                    SessionEvent.session("s", "user.message", Map.of("content", "x".repeat(3000)));
            assertEquals(
                    "SESSION_LIMIT",
                    assertThrows(HarnessException.class, () -> writer.append(huge)).code());
            writer.append(SessionEvent.session("s", "user.message", Map.of("content", "small2")));
            assertThrows(
                    HarnessException.class,
                    () -> writer.append(SessionEvent.session("s", "user.message", Map.of())));
        }
        assertEquals(2, repo.openReader("s").events().size());
        String last = repo.openReader("s").events().getLast().eventId();
        assertThrows(HarnessException.class, () -> repo.fork("s", last));
        assertEquals(List.of("s"), repo.list());
        String fork = repo.fork("s", first.eventId());
        assertEquals(2, repo.openReader(fork).events().size());
    }

    @Test
    void directoryReplacementCannotReuseAnExistingIndexInstance() throws Exception {
        var repo = new SnapshotRepository();
        repo.events = List.of();
        Path directory = Files.createDirectory(root.resolve("index")).toRealPath();
        var index = new SessionSearchIndex(directory, repo);
        var token = new TokenSessionSearch(directory, repo, new SecretRedactor(List.of()));
        var created =
                Files.readAttributes(directory, java.nio.file.attribute.BasicFileAttributes.class)
                        .creationTime();
        Files.move(directory, root.resolve("saved"));
        Files.createDirectory(directory);
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Windows can preserve/reuse creation time for a replaced name. Reproduce that
            // identity collision deterministically instead of relying on filesystem timing.
            Files.getFileAttributeView(
                            directory, java.nio.file.attribute.BasicFileAttributeView.class)
                    .setTimes(null, null, created);
            assertEquals(
                    created,
                    Files.readAttributes(
                                    directory, java.nio.file.attribute.BasicFileAttributes.class)
                            .creationTime());
        }
        assertThrows(java.io.IOException.class, () -> index.search("s", "alpha", 0, 10, false));
        assertThrows(
                java.io.IOException.class,
                () -> token.search("s", "alpha", "tokens", 10, false, CancellationToken.NONE));
        assertFalse(Files.exists(directory.resolve("s.search.json")));
        assertFalse(Files.exists(directory.resolve("s.content.json")));
    }
}
