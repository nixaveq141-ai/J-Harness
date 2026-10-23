package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.session.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class M1056WebContentTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper();
    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> request(
            M10BrowserHost host, String method, String path, Object body, boolean authenticated)
            throws Exception {
        var b =
                HttpRequest.newBuilder(host.server.address().resolve(path))
                        .timeout(Duration.ofSeconds(10));
        if (authenticated) b.header("Authorization", "Bearer " + M10BrowserHost.TOKEN);
        if (body == null) b.GET();
        else
            b.header("Content-Type", "application/json")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    Map<String, Object> get(M10BrowserHost host, String path) throws Exception {
        return Values.object(
                JSON.readValue(request(host, "GET", path, null, true).body(), Map.class));
    }

    String create(M10BrowserHost host, String profile) throws Exception {
        var response =
                request(
                        host,
                        "POST",
                        "/v1/tasks",
                        Map.of(
                                "profile",
                                profile,
                                "goal",
                                "fixture-secret-do-not-display",
                                "requestId",
                                UUID.randomUUID().toString()),
                        true);
        assertEquals(202, response.statusCode());
        String id =
                (String) Values.object(JSON.readValue(response.body(), Map.class)).get("taskId");
        long end = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < end) {
            if ("FINISHED".equals(get(host, "/v1/tasks/" + id).get("phase"))) return id;
            Thread.sleep(20);
        }
        throw new AssertionError("not finished");
    }

    static String path(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void authenticatedPreviewUsesBoundRootAndAddsNoFacts() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            String id = create(host, "read"), base = "/v1/tasks/" + id;
            var before = get(host, base + "/events?limit=100");
            assertEquals(
                    401,
                    request(host, "GET", base + "/file?path=" + path("input.txt"), null, false)
                            .statusCode());
            assertTrue(
                    get(host, base + "/file?path=" + path("input.txt"))
                            .get("text")
                            .toString()
                            .contains("hello from a real workspace"));
            assertEquals(
                    400,
                    request(
                                    host,
                                    "GET",
                                    base + "/file?path=" + path("../write/input.txt"),
                                    null,
                                    true)
                            .statusCode());
            assertEquals(
                    400,
                    request(
                                    host,
                                    "GET",
                                    base + "/file?path=" + path(".jharness/config"),
                                    null,
                                    true)
                            .statusCode());
            assertEquals(405, request(host, "POST", base + "/file", Map.of(), true).statusCode());
            assertEquals(before, get(host, base + "/events?limit=100"));
            assertEquals(1, host.opened.get());
        }
    }

    @Test
    void largeEventCanBeReadInBoundedPagesWithoutReplayingModel() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            String id = create(host, "large"), base = "/v1/tasks/" + id;
            var events = (List<?>) get(host, base + "/events?limit=100").get("events");
            var omitted =
                    events.stream()
                            .map(Values::object)
                            .filter(
                                    e ->
                                            "assistant.message".equals(e.get("type"))
                                                    && Boolean.TRUE.equals(
                                                            Values.object(e.get("payload"))
                                                                    .get("omitted")))
                            .findFirst()
                            .orElseThrow();
            String event = (String) omitted.get("eventId");
            var page = get(host, base + "/content/" + event);
            assertEquals(16384, page.get("nextOffset"));
            assertEquals(70000, page.get("totalCharacters"));
            var second =
                    get(
                            host,
                            base
                                    + "/content/"
                                    + event
                                    + "?offset=16384&version="
                                    + page.get("contentSha256"));
            assertEquals("L".repeat(16384), second.get("text"));
            assertEquals(id, second.get("sessionId"));
            assertEquals(
                    400,
                    request(host, "GET", base + "/content/" + event + "?offset=999999", null, true)
                            .statusCode());
            assertEquals(
                    409,
                    request(
                                    host,
                                    "GET",
                                    base + "/content/" + event + "?version=" + "0".repeat(64),
                                    null,
                                    true)
                            .statusCode());
            assertEquals(1, host.opened.get());
            assertEquals(2, host.models.getFirst().requests().size());
        }
    }

    @Test
    void artifactMustHaveScopedPersistentReferenceAndValidHash() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            String id = create(host, "read"), other = create(host, "html");
            var store =
                    new LocalArtifactStore(root.resolve("read-artifacts"), new SecretRedactor());
            var artifact = store.put(id, "artifact fixture-secret-do-not-display body");
            String route = "/v1/tasks/" + id + "/artifacts/" + artifact.id();
            assertEquals(404, request(host, "GET", route, null, true).statusCode());
            var repository = new JsonlSessionRepository(root.resolve("read-sessions"));
            try (var writer = repository.openWriter(id, WriteOwnership.EXCLUSIVE)) {
                writer.append(
                        SessionEvent.session(
                                id,
                                "artifact.created",
                                Map.of("id", artifact.id(), "sha256", artifact.sha256())));
                writer.flush();
            }
            var value = get(host, route);
            assertEquals("artifact [REDACTED] body", value.get("text"));
            assertEquals(
                    404,
                    request(
                                    host,
                                    "GET",
                                    "/v1/tasks/" + other + "/artifacts/" + artifact.id(),
                                    null,
                                    true)
                            .statusCode());
            Files.writeString(
                    root.resolve("read-artifacts").resolve(id).resolve(artifact.id() + ".txt"),
                    "changed");
            var corrupt = request(host, "GET", route, null, true);
            assertEquals(400, corrupt.statusCode());
            assertTrue(corrupt.body().contains("ARTIFACT_CORRUPT"));
        }
    }
}
