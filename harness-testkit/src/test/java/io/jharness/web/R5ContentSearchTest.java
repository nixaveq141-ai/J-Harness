package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class R5ContentSearchTest {
    private Map<String, Object> query(String cursor, int limit, boolean rebuild) {
        return Map.of(
                "text",
                "alpha",
                "mode",
                "tokens",
                "profile",
                "",
                "view",
                "all",
                "sessionId",
                "",
                "cursor",
                cursor,
                "limit",
                limit,
                "rebuild",
                rebuild);
    }

    private Map<String, Object> row(String id) {
        return Map.of("sessionId", id, "profile", "local", "archived", false);
    }

    private HarnessWebServer.Profile profile(SessionRepository repo, ContentSearchProvider search) {
        return new HarnessWebServer.Profile(
                "local",
                repo,
                (a, b, c) -> {
                    throw new AssertionError();
                },
                null,
                null,
                search);
    }

    private ContentSearchProvider.Result hit(String id, String time) {
        return new ContentSearchProvider.Result(
                List.of(
                        new ContentSearchProvider.Hit(
                                "e-" + id, "user.message", time, "", "alpha", 1)),
                1,
                0,
                false);
    }

    @Test
    void exactPagesBindEveryQueryFieldAndRemovedProfilesAreNotAuthorized() throws Exception {
        var repo = new InMemorySessionRepository();
        for (String id : List.of("s0", "s1", "s2"))
            try (var w = repo.openWriter(id, WriteOwnership.EXCLUSIVE)) {
                w.flush();
            }
        var profiles =
                Map.of(
                        "local",
                        profile(repo, (id, t, m, l, r, c) -> hit(id, "2026-10-25T00:00:00Z")));
        var scope = List.of(row("s2"), row("s0"), row("s1"));
        var search = new WorkbenchContentSearch();
        var first = search.search(query("", 1, false), scope, profiles);
        String cursor = (String) first.get("next");
        var second = search.search(query(cursor, 1, false), scope, profiles);
        assertEquals(
                "s0", Values.object(((List<?>) first.get("matches")).getFirst()).get("sessionId"));
        assertEquals(
                "s1", Values.object(((List<?>) second.get("matches")).getFirst()).get("sessionId"));
        assertEquals(3, second.get("total"));
        assertEquals(true, second.get("more"));
        assertNotEquals("", second.get("next"));
        var last = search.search(query((String) second.get("next"), 1, false), scope, profiles);
        assertEquals(false, last.get("more"));
        assertEquals("", last.get("next"));
        for (var field :
                Map.<String, Object>of(
                                "profile", "local", "view", "active", "limit", 2, "sessionId", "s0")
                        .entrySet()) {
            var changed = new HashMap<>(query(cursor, 1, false));
            changed.put(field.getKey(), field.getValue());
            assertEquals(
                    "SEARCH_CURSOR_EXPIRED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> search.search(changed, scope, profiles))
                            .code());
        }
        assertThrows(
                HarnessException.class,
                () -> search.search(query(cursor, 1, true), scope, profiles));
        assertEquals(0, search.search(query("", 1, false), scope, Map.of()).get("total"));
    }

    @Test
    void maximumScopeListsMembershipOnceAndInvalidProviderTimeHasFixedError() throws Exception {
        var calls = new AtomicInteger();
        var ids = new ArrayList<String>();
        var scope = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 256; i++) {
            ids.add("s" + i);
            scope.add(row("s" + i));
        }
        SessionRepository repo =
                new SessionRepository() {
                    public List<String> list() {
                        calls.incrementAndGet();
                        return ids;
                    }

                    public SessionReader openReader(String id) {
                        throw new AssertionError();
                    }

                    public SessionWriter openWriter(String id, WriteOwnership o) {
                        throw new AssertionError();
                    }

                    public String fork(String id, String e) {
                        throw new AssertionError();
                    }
                };
        var profiles =
                Map.of(
                        "local",
                        profile(
                                repo,
                                (id, t, m, l, r, c) ->
                                        new ContentSearchProvider.Result(List.of(), 0, 0, false)));
        assertEquals(
                0,
                new WorkbenchContentSearch()
                        .search(query("", 1, false), scope, profiles)
                        .get("total"));
        assertEquals(1, calls.get());
        var invalid =
                Map.of(
                        "local",
                        profile(repo, (id, t, m, l, r, c) -> hit(id, "private-invalid-time")));
        var failure =
                assertThrows(
                        HarnessException.class,
                        () ->
                                new WorkbenchContentSearch()
                                        .search(query("", 1, false), scope, invalid));
        assertEquals("SEARCH_UNAVAILABLE", failure.code());
        assertFalse(failure.getMessage().contains("private-invalid-time"));
    }

    @Test
    void twoProviderQueriesCanProgressAndThirdIsRejectedWithoutGlobalMonitor() throws Exception {
        var repo = new InMemorySessionRepository();
        try (var w = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            w.flush();
        }
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var profiles =
                Map.of(
                        "local",
                        profile(
                                repo,
                                (id, t, m, l, r, c) -> {
                                    entered.countDown();
                                    try {
                                        if (!release.await(5, TimeUnit.SECONDS))
                                            throw new AssertionError("provider barrier");
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                    return hit(id, "2026-10-25T00:00:00Z");
                                }));
        var search = new WorkbenchContentSearch();
        var scope = List.of(row("s"));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> search.search(query("", 1, false), scope, profiles));
            var b = executor.submit(() -> search.search(query("", 1, false), scope, profiles));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(
                        "SEARCH_BUSY",
                        assertThrows(
                                        HarnessException.class,
                                        () -> search.search(query("", 1, false), scope, profiles))
                                .code());
            } finally {
                release.countDown();
            }
            assertEquals(1, a.get(5, TimeUnit.SECONDS).get("total"));
            assertEquals(1, b.get(5, TimeUnit.SECONDS).get("total"));
        }
    }
}
