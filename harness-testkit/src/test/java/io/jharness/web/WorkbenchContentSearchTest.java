package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchContentSearchTest {
    @TempDir Path base;
    private final WorkbenchContentSearch search = new WorkbenchContentSearch();

    private Map<String, Object> query(
            String text,
            String mode,
            String profile,
            String view,
            String session,
            String cursor,
            int limit,
            boolean rebuild) {
        return Map.of(
                "text",
                text,
                "mode",
                mode,
                "profile",
                profile,
                "view",
                view,
                "sessionId",
                session,
                "cursor",
                cursor,
                "limit",
                limit,
                "rebuild",
                rebuild);
    }

    @Test
    void scopeArchivesRelevanceStablePaginationAndCursorBinding() throws Exception {
        base = base.toRealPath();
        var repo = new JsonlSessionRepository(base.resolve("sessions"));
        var provider =
                new TokenSessionSearch(
                        base.resolve("index"),
                        repo,
                        new SecretRedactor(List.of("synthetic-snippet-private")));
        var profile =
                new HarnessWebServer.Profile(
                        "local",
                        repo,
                        (a, b, c) -> {
                            throw new AssertionError();
                        },
                        null,
                        null,
                        provider);
        var profiles = Map.of("local", profile, "other", profile);
        var scope = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 4; i++) {
            String id = "s" + i;
            try (var writer = repo.openWriter(id, WriteOwnership.EXCLUSIVE)) {
                writer.append(
                        SessionEvent.session(
                                id,
                                "user.message",
                                Map.of(
                                        "content",
                                        i == 0
                                                ? "alpha beta alpha synthetic-snippet-private"
                                                : "alpha beta synthetic-snippet-private")));
                writer.flush();
            }
            scope.add(
                    Map.of(
                            "sessionId",
                            id,
                            "profile",
                            i == 3 ? "other" : "local",
                            "archived",
                            i == 2,
                            "name",
                            id));
        }
        var first =
                search.search(
                        query("alpha beta", "tokens", "local", "all", "", "", 1, false),
                        scope,
                        profiles);
        assertEquals(3, first.get("total"));
        assertFalse(first.toString().contains("synthetic-snippet-private"));
        assertTrue(first.toString().contains("[REDACTED]"));
        assertEquals(
                "s0", Values.object(((List<?>) first.get("matches")).getFirst()).get("sessionId"));
        String cursor = (String) first.get("next");
        var next =
                search.search(
                        query("alpha beta", "tokens", "local", "all", "", cursor, 1, false),
                        scope,
                        profiles);
        assertEquals(first.get("snapshotId"), next.get("snapshotId"));
        assertNotEquals(first.get("matches"), next.get("matches"));
        assertEquals(
                "SEARCH_CURSOR_EXPIRED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                query(
                                                        "alpha", "tokens", "local", "all", "",
                                                        cursor, 1, false),
                                                scope,
                                                profiles))
                        .code());
        assertEquals(
                2,
                search.search(
                                query("alpha", "tokens", "local", "active", "", "", 20, false),
                                scope,
                                profiles)
                        .get("total"));
        assertEquals(
                1,
                search.search(
                                query("alpha", "tokens", "local", "archived", "", "", 20, false),
                                scope,
                                profiles)
                        .get("total"));
        assertEquals(
                "SEARCH_SCOPE_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                query(
                                                        "alpha", "tokens", "local", "all", "s3", "",
                                                        20, false),
                                                scope,
                                                profiles))
                        .code());
        var archived = new ArrayList<>(scope);
        archived.set(
                1, Map.of("sessionId", "s1", "profile", "local", "archived", true, "name", "s1"));
        assertEquals(
                "SEARCH_CURSOR_EXPIRED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                query(
                                                        "alpha beta",
                                                        "tokens",
                                                        "local",
                                                        "all",
                                                        "",
                                                        cursor,
                                                        1,
                                                        false),
                                                archived,
                                                profiles))
                        .code());
    }

    @Test
    void excessiveScopeAndUnavailableProviderAreExplicitFailures() throws Exception {
        base = base.toRealPath();
        var repo = new JsonlSessionRepository(base.resolve("sessions"));
        var profile =
                new HarnessWebServer.Profile(
                        "local",
                        repo,
                        (a, b, c) -> {
                            throw new AssertionError();
                        });
        var scope = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 257; i++)
            scope.add(
                    Map.of(
                            "sessionId",
                            "s" + i,
                            "profile",
                            "local",
                            "archived",
                            false,
                            "name",
                            ""));
        assertEquals(
                "SEARCH_SCOPE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                query(
                                                        "alpha", "tokens", "local", "active", "",
                                                        "", 20, false),
                                                scope,
                                                Map.of("local", profile)))
                        .code());
        assertEquals(
                "SEARCH_UNAVAILABLE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        search.search(
                                                query(
                                                        "alpha", "tokens", "local", "active", "",
                                                        "", 20, false),
                                                scope.subList(0, 1),
                                                Map.of("local", profile)))
                        .code());
    }
}
