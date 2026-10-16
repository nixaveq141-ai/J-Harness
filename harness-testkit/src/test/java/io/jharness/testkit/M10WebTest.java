package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M10WebTest {
    @Test
    void settingsRequireOperatorAuthAndUnknownBindingBlocksMutation() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            var saved = new java.util.concurrent.atomic.AtomicInteger();
            host.server.settings(
                    new io.jharness.web.HarnessWebServer.Settings() {
                        public Map<String, Object> read() {
                            return Map.of("revision", "fixture");
                        }

                        public Map<String, Object> save(Map<String, Object> input) {
                            saved.incrementAndGet();
                            return read();
                        }
                    });
            var input =
                    Map.of(
                            "revision",
                            "fixture",
                            "models",
                            List.of(),
                            "selected",
                            "default",
                            "extensions",
                            Map.of(),
                            "apiKeys",
                            Map.of());
            assertEquals(401, request(host, "POST", "/v1/settings", input, false).statusCode());
            assertEquals(
                    403,
                    request(
                                    host,
                                    "POST",
                                    "/v1/settings",
                                    input,
                                    true,
                                    "Origin",
                                    "https://evil.invalid")
                            .statusCode());
            assertEquals(200, request(host, "GET", "/v1/settings", null, true).statusCode());
            assertEquals(200, request(host, "POST", "/v1/settings", input, true).statusCode());
            assertEquals(1, saved.get());
            var task =
                    object(
                            request(
                                    host,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "unknown",
                                            "goal",
                                            "fixture",
                                            "requestId",
                                            "settings-test"),
                                    true));
            String id = (String) task.get("taskId");
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!object(request(host, "GET", "/v1/tasks/" + id, null, true))
                    .get("phase")
                    .equals("UNKNOWN")) {
                assertTrue(System.nanoTime() < deadline);
                Thread.sleep(20);
            }
            assertEquals(409, request(host, "POST", "/v1/settings", input, true).statusCode());
            assertEquals(1, saved.get());
        }
    }

    @TempDir Path root;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> request(
            M10BrowserHost host,
            String method,
            String path,
            Object body,
            boolean auth,
            String... headers)
            throws Exception {
        var b =
                HttpRequest.newBuilder(host.server.address().resolve(path))
                        .timeout(Duration.ofSeconds(5));
        if (auth) b.header("Authorization", "Bearer " + M10BrowserHost.TOKEN);
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        if (body != null) b.header("Content-Type", "application/json");
        return client.send(
                b.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                JSON.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> object(HttpResponse<String> r) throws Exception {
        return JSON.readValue(r.body(), new TypeReference<Map<String, Object>>() {});
    }

    @Test
    void browserTrustAndStaticManifestKeepApiPrivate() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            String origin = host.server.address().toString();
            assertEquals(
                    200,
                    request(host, "GET", "/v1/info", null, true, "Origin", origin).statusCode());
            assertEquals(
                    401,
                    request(host, "GET", "/v1/info", null, false, "Origin", origin).statusCode());
            for (String hostile :
                    List.of(
                            "null",
                            origin + "/",
                            "https://127.0.0.1",
                            "http://evil.invalid",
                            origin + ".evil"))
                assertEquals(
                        403,
                        request(host, "GET", "/v1/info", null, true, "Origin", hostile)
                                .statusCode());
            assertEquals(
                    403,
                    request(host, "GET", "/v1/tasks", null, true, "Sec-Fetch-Site", "cross-site")
                            .statusCode());
            assertEquals(
                    403,
                    request(host, "GET", "/v1/info", null, true, "Origin", origin, "Origin", origin)
                            .statusCode());
            var index = request(host, "GET", "/", null, false);
            assertTrue(Set.of(200, 503).contains(index.statusCode()));
            assertTrue(
                    index.headers()
                            .firstValue("Content-Security-Policy")
                            .orElseThrow()
                            .contains("frame-ancestors 'none'"));
            assertEquals("no-store", index.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(index.body().contains(M10BrowserHost.TOKEN));
            for (String path :
                    List.of(
                            "/.agent",
                            "/../pom.xml",
                            "/ui-files.txt",
                            "/assets/missing.js",
                            "/?token=bad"))
                assertEquals(404, request(host, "GET", path, null, false).statusCode(), path);
            assertEquals(400, request(host, "GET", "/%2e%2e/pom.xml", null, false).statusCode());
            assertEquals(0, host.opened.get());
        }
    }

    @Test
    void reportSearchAndSubmissionLookupAreReadOnlyAndBounded() throws Exception {
        try (var host = new M10BrowserHost(root)) {
            var created =
                    object(
                            request(
                                    host,
                                    "POST",
                                    "/v1/tasks",
                                    Map.of(
                                            "profile",
                                            "read",
                                            "goal",
                                            "read fixture-secret-do-not-display",
                                            "requestId",
                                            "m10-read"),
                                    true));
            String id = (String) created.get("taskId");
            long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < end) {
                var t = object(request(host, "GET", "/v1/tasks/" + id, null, true));
                if (Set.of("FINISHED", "UNKNOWN").contains(t.get("phase"))) break;
                Thread.sleep(20);
            }
            assertEquals(
                    id,
                    object(request(host, "GET", "/v1/submissions/m10-read", null, true))
                            .get("taskId"));
            assertEquals(
                    404, request(host, "GET", "/v1/submissions/missing", null, true).statusCode());
            var report = request(host, "GET", "/v1/tasks/" + id + "/report", null, true);
            assertEquals(200, report.statusCode());
            assertEquals("session-events", object(report).get("source"));
            assertFalse(report.body().contains("fixture-secret-do-not-display"));
            assertEquals(
                    404,
                    request(host, "GET", "/v1/tasks/" + id + "/diff", null, true).statusCode());
            var first =
                    object(
                            request(
                                    host,
                                    "POST",
                                    "/v1/tasks/" + id + "/search",
                                    Map.of("text", "tool", "after", "", "limit", 1),
                                    true));
            assertEquals(1, ((List<?>) first.get("events")).size());
            var second =
                    object(
                            request(
                                    host,
                                    "POST",
                                    "/v1/tasks/" + id + "/search",
                                    Map.of("text", "tool", "after", first.get("next"), "limit", 1),
                                    true));
            assertNotEquals(first.get("events"), second.get("events"));
            assertEquals(
                    400,
                    request(
                                    host,
                                    "POST",
                                    "/v1/tasks/" + id + "/search",
                                    Map.of("text", "x", "after", "invalid", "limit", 1),
                                    true)
                            .statusCode());
            for (Object limit : List.of(0, 51, 1.5, "1"))
                assertEquals(
                        400,
                        request(
                                        host,
                                        "POST",
                                        "/v1/tasks/" + id + "/search",
                                        Map.of("text", "x", "after", "", "limit", limit),
                                        true)
                                .statusCode());
            var secret =
                    object(
                            request(
                                    host,
                                    "POST",
                                    "/v1/tasks/" + id + "/search",
                                    Map.of(
                                            "text",
                                            "fixture-secret-do-not-display",
                                            "after",
                                            "",
                                            "limit",
                                            20),
                                    true));
            assertTrue(((List<?>) secret.get("events")).isEmpty());
            assertEquals(1, host.opened.get());
        }
    }
}
