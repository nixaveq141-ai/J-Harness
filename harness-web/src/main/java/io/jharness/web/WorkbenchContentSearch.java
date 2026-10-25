package io.jharness.web;

import io.jharness.api.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Per-host bounded result snapshots. Catalog membership remains the authorization source. */
final class WorkbenchContentSearch {
    private record Snapshot(
            String query,
            String scope,
            long expires,
            List<Map<String, Object>> matches,
            int reused,
            int rebuilt) {}

    private final java.util.concurrent.Semaphore searches = new java.util.concurrent.Semaphore(2);
    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();

    Map<String, Object> search(
            Map<String, Object> input,
            List<Map<String, Object>> candidates,
            Map<String, HarnessWebServer.Profile> profiles)
            throws IOException {
        String text = field(input, "text", 256),
                mode = field(input, "mode", 16),
                profile = field(input, "profile", 32),
                view = field(input, "view", 16),
                session = field(input, "sessionId", 128),
                cursor = field(input, "cursor", 64);
        if (text.isBlank()
                || !Set.of("tokens", "phrase").contains(mode)
                || !Set.of("active", "archived", "all").contains(view)
                || !profile.isEmpty() && !profiles.containsKey(profile)
                || !session.isEmpty() && !session.matches("[A-Za-z0-9_-]{1,128}")
                || !(input.get("limit") instanceof Integer limit)
                || limit < 1
                || limit > 32
                || !(input.get("rebuild") instanceof Boolean rebuild))
            throw error("INVALID_SEARCH");
        var scope =
                candidates.stream()
                        .filter(
                                row ->
                                        profiles.containsKey(row.get("profile"))
                                                && (profile.isEmpty()
                                                        || profile.equals(row.get("profile")))
                                                && (session.isEmpty()
                                                        || session.equals(row.get("sessionId")))
                                                && (view.equals("all")
                                                        || Boolean.TRUE.equals(row.get("archived"))
                                                                == view.equals("archived")))
                        .sorted(Comparator.comparing(row -> (String) row.get("sessionId")))
                        .toList();
        if (!session.isEmpty() && scope.isEmpty()) throw error("SEARCH_SCOPE_DENIED");
        if (scope.size() > 256) throw error("SEARCH_SCOPE_LIMIT");
        String scopeHash =
                scope.stream()
                        .map(
                                row ->
                                        row.get("profile")
                                                + ":"
                                                + row.get("sessionId")
                                                + ":"
                                                + row.get("archived"))
                        .reduce("", (a, b) -> a + "|" + b);
        String query =
                Base64.getEncoder()
                        .encodeToString(
                                WebJson.encode(
                                        Map.of(
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
                                                "limit",
                                                limit)));
        synchronized (snapshots) {
            snapshots.entrySet().removeIf(entry -> entry.getValue().expires < System.nanoTime());
        }
        String id;
        int offset;
        Snapshot snapshot;
        if (cursor.isEmpty()) {
            if (!searches.tryAcquire()) throw error("SEARCH_BUSY");
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                CancellationToken cancellation =
                        () ->
                                System.nanoTime() >= deadline
                                        || Thread.currentThread().isInterrupted();
                var matches = new ArrayList<Map<String, Object>>();
                int reused = 0, rebuilt = 0;
                var memberships = new HashMap<String, Set<String>>();
                var timestamps = new HashMap<String, Instant>();
                for (var row : scope) {
                    cancellation.throwIfCancelled();
                    var selected = profiles.get(row.get("profile"));
                    if (selected == null || selected.search() == null)
                        throw error("SEARCH_UNAVAILABLE");
                    // Missing log means admission has not yet produced any content, never a guessed
                    // result.
                    if (!memberships
                            .computeIfAbsent(
                                    (String) row.get("profile"),
                                    ignored -> Set.copyOf(selected.sessions().list()))
                            .contains(row.get("sessionId"))) continue;
                    var result =
                            selected.search()
                                    .search(
                                            (String) row.get("sessionId"),
                                            text,
                                            mode,
                                            4096,
                                            rebuild,
                                            cancellation);
                    reused += result.reusedEvents();
                    if (result.rebuilt()) rebuilt++;
                    for (var hit : result.hits()) {
                        if (matches.size() >= 4096) throw error("SEARCH_RESULT_LIMIT");
                        try {
                            timestamps.computeIfAbsent(hit.timestamp(), Instant::parse);
                        } catch (RuntimeException invalid) {
                            throw error("SEARCH_UNAVAILABLE");
                        }
                        var value = new LinkedHashMap<>(row);
                        value.put("eventId", hit.eventId());
                        value.put("turnId", hit.turnId());
                        value.put("type", hit.type());
                        value.put("timestamp", hit.timestamp());
                        value.put("snippet", hit.snippet());
                        value.put("score", hit.score());
                        matches.add(Map.copyOf(value));
                    }
                }
                matches.sort(
                        Comparator.<Map<String, Object>>comparingInt(
                                        row -> (Integer) row.get("score"))
                                .reversed()
                                .thenComparing(
                                        row -> timestamps.get((String) row.get("timestamp")),
                                        Comparator.reverseOrder())
                                .thenComparing(row -> (String) row.get("sessionId"))
                                .thenComparing(row -> (String) row.get("eventId")));
                id = UUID.randomUUID().toString();
                offset = 0;
                snapshot =
                        new Snapshot(
                                query,
                                scopeHash,
                                System.nanoTime() + Duration.ofMinutes(2).toNanos(),
                                List.copyOf(matches),
                                reused,
                                rebuilt);
                synchronized (snapshots) {
                    if (snapshots.size() >= 4)
                        snapshots.remove(snapshots.keySet().iterator().next());
                    snapshots.put(id, snapshot);
                }
            } finally {
                searches.release();
            }
        } else {
            int separator = cursor.lastIndexOf('_');
            if (separator < 0) throw error("SEARCH_CURSOR_EXPIRED");
            id = cursor.substring(0, separator);
            try {
                offset = Integer.parseInt(cursor.substring(separator + 1));
            } catch (NumberFormatException ex) {
                throw error("SEARCH_CURSOR_EXPIRED");
            }
            synchronized (snapshots) {
                snapshot = snapshots.get(id);
            }
            if (rebuild
                    || snapshot == null
                    || !snapshot.query.equals(query)
                    || !snapshot.scope.equals(scopeHash)
                    || offset < 1
                    || offset >= snapshot.matches.size()
                    || offset % limit != 0) throw error("SEARCH_CURSOR_EXPIRED");
        }
        int next = Math.min(offset + limit, snapshot.matches.size());
        return Map.of(
                "matches",
                snapshot.matches.subList(offset, next),
                "total",
                snapshot.matches.size(),
                "more",
                next < snapshot.matches.size(),
                "next",
                next < snapshot.matches.size() ? id + "_" + next : "",
                "snapshotId",
                id,
                "source",
                "sanitized-session-content",
                "reusedEvents",
                snapshot.reused,
                "rebuiltSessions",
                snapshot.rebuilt);
    }

    private static String field(Map<String, Object> input, String name, int max) {
        if (!(input.get(name) instanceof String value) || value.length() > max)
            throw error("INVALID_SEARCH");
        return value;
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "Content search rejected");
    }
}
