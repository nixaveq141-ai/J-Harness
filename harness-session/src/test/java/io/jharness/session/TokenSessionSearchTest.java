package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class TokenSessionSearchTest {
    @Test
    void submissionUserMessageIsSearchableWithoutIndexingItsControlFields() throws Exception {
        try (var writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            "s",
                            "sdk.input.applied",
                            Map.of(
                                    "requestHash",
                                    "a".repeat(64),
                                    "userMessage",
                                    Map.of("role", "user", "content", "searchable submission"))));
        }
        assertEquals(1, query("searchable submission", "phrase").hits().size());
        assertTrue(query("requestHash", "tokens").hits().isEmpty());
    }

    @TempDir Path root;
    JsonlSessionRepository repository;
    TokenSessionSearch search;

    @BeforeEach
    void setup() throws Exception {
        root = root.toRealPath();
        repository = new JsonlSessionRepository(root.resolve("sessions"));
        search =
                new TokenSessionSearch(
                        root.resolve("index"),
                        repository,
                        new SecretRedactor(List.of("synthetic-search-key")));
    }

    private void append(String id, String text) {
        try (var writer = repository.openWriter(id, WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session(id, "user.message", Map.of("content", text)));
            writer.flush();
        }
    }

    private ContentSearchProvider.Result query(String text, String mode) throws Exception {
        return search.search("s", text, mode, 4096, false, CancellationToken.NONE);
    }

    @Test
    void excessiveIndexedBodyFailsWithoutChangingFactsOrPublishingPartialIndex() throws Exception {
        for (int i = 0; i < 43; i++) append("s", "alpha " + "x".repeat(100000));
        byte[] before = Files.readAllBytes(root.resolve("sessions/s.jsonl"));
        assertEquals(
                "SEARCH_INDEX_LIMIT",
                assertThrows(HarnessException.class, () -> query("alpha", "tokens")).code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("sessions/s.jsonl")));
        assertFalse(Files.exists(root.resolve("index/s.content.json")));
    }

    @Test
    void tokensAreAndWordsNotSubstringsAndPhraseKeepsOrderAndChinese() throws Exception {
        append("s", "Alpha beta alpha; 检索中文内容");
        append("s", "alphabet beta");
        append("s", "beta alpha");
        var tokens = query("ALPHA beta", "tokens");
        assertEquals(2, tokens.hits().size());
        assertEquals(3, tokens.hits().getFirst().score());
        assertEquals(1, query("alpha beta", "phrase").hits().size());
        assertEquals(1, query("中文", "tokens").hits().size());
        assertTrue(query("alpha missing", "tokens").hits().isEmpty());
        assertThrows(HarnessException.class, () -> query("   ", "tokens"));
    }

    @Test
    void incrementalIndexCorruptionAndExplicitRebuildNeverAlterFacts() throws Exception {
        append("s", "alpha first");
        assertTrue(query("alpha", "tokens").rebuilt());
        assertEquals(1, query("alpha", "tokens").reusedEvents());
        append("s", "alpha second");
        byte[] before = Files.readAllBytes(root.resolve("sessions/s.jsonl"));
        var update = query("alpha", "tokens");
        assertEquals(1, update.reusedEvents());
        assertEquals(2, update.hits().size());
        Files.writeString(root.resolve("index/s.content.json"), "broken");
        assertTrue(query("alpha", "tokens").rebuilt());
        assertTrue(
                search.search("s", "alpha", "tokens", 10, true, CancellationToken.NONE).rebuilt());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("sessions/s.jsonl")));
    }

    @Test
    void recomputedChecksumCannotForgeCacheTextOrLeakNewlyKnownSecrets() throws Exception {
        append("s", "public synthetic-search-key");
        query("public", "tokens");
        Path file = root.resolve("index/s.content.json");
        assertFalse(Files.readString(file).contains("synthetic-search-key"));
        var json = new ObjectMapper();
        var envelope =
                json.readValue(
                        Files.readAllBytes(file), new TypeReference<Map<String, Object>>() {});
        var data = new LinkedHashMap<>(Values.object(envelope.get("data")));
        var document =
                new LinkedHashMap<>(Values.object(((List<?>) data.get("documents")).getFirst()));
        document.put("text", "injected secret-body");
        data.put("documents", List.of(document));
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(json.writeValueAsBytes(data)));
        Files.write(file, json.writeValueAsBytes(Map.of("data", data, "sha256", hash)));
        assertTrue(query("injected", "tokens").hits().isEmpty());
        assertEquals(1, query("public", "tokens").hits().size());
        assertTrue(query("synthetic-search-key", "phrase").hits().isEmpty());
    }

    @Test
    void modelRequestsAndConfigurationAreExcludedFromContent() throws Exception {
        append("s", "message");
        try (var writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    SessionEvent.session(
                            "s", "config.selected", Map.of("body", "private-config-word")));
            writer.flush();
        }
        assertTrue(query("private-config-word", "phrase").hits().isEmpty());
        assertEquals(1, query("message", "tokens").indexedEvents());
    }

    @Test
    void indexLocksCancellationTraversalAndResultCapacityAreEnforced() throws Exception {
        append("s", "alpha");
        append("s", "alpha");
        query("alpha", "tokens");
        try (var channel =
                        FileChannel.open(
                                root.resolve("index/s.content.lock"), StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertEquals(
                    "SEARCH_BUSY",
                    assertThrows(HarnessException.class, () -> query("alpha", "tokens")).code());
        }
        assertThrows(
                HarnessException.class,
                () ->
                        search.search(
                                "../outside",
                                "alpha",
                                "tokens",
                                10,
                                false,
                                CancellationToken.NONE));
        assertThrows(
                HarnessException.class,
                () -> search.search("s", "alpha", "tokens", 10, false, () -> true));
        assertEquals(
                "SEARCH_RESULT_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                "s",
                                                "alpha",
                                                "tokens",
                                                1,
                                                false,
                                                CancellationToken.NONE))
                        .code());
        assertEquals(
                "INVALID_SEARCH",
                assertThrows(HarnessException.class, () -> query("?!", "tokens")).code());
    }
}
