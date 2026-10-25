package io.jharness.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Serves only build-manifest resources; never resolves a user filesystem path. */
final class UiResources {
    private static final int LIMIT = 2 * 1024 * 1024;
    private static final Set<String> FILES = loadFiles();

    private UiResources() {}

    private static Set<String> loadFiles() {
        try (var input = UiResources.class.getResourceAsStream("/ui/ui-files.txt")) {
            if (input == null) return Set.of();
            return parseManifest(input.readNBytes(16385));
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static Set<String> parseManifest(byte[] bytes) throws IOException {
        if (bytes.length > 16384) throw new IOException("UI manifest byte limit");
        var files = new java.util.HashSet<String>();
        for (String name : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (!name.matches(
                            "(?:index\\.html|ui-license\\.txt|assets/[A-Za-z0-9_.-]+\\.(?:js|css))")
                    || !files.add(name)) throw new IOException("Invalid UI manifest");
        }
        if (!files.containsAll(Set.of("index.html", "ui-license.txt")))
            throw new IOException("Incomplete UI manifest");
        return Set.copyOf(files);
    }

    static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String file =
                (path == null || path.isEmpty() || path.equals("/"))
                        ? "index.html"
                        : path.substring(1);
        int code = 200;
        byte[] bytes;
        if (!exchange.getRequestMethod().equals("GET")
                || exchange.getRequestURI().getRawQuery() != null) {
            code = 404;
            bytes = "Not found".getBytes(StandardCharsets.UTF_8);
        } else if (FILES.isEmpty() && file.equals("index.html")) {
            code = 503;
            bytes =
                    "Web UI not built. Run npm ci and npm run build in frontend before Maven packaging."
                            .getBytes(StandardCharsets.UTF_8);
        } else if (!FILES.contains(file)) {
            code = 404;
            bytes = "Not found".getBytes(StandardCharsets.UTF_8);
        } else {
            try (var input = UiResources.class.getResourceAsStream("/ui/" + file)) {
                if (input == null) throw new IOException("UI manifest resource missing");
                bytes = input.readNBytes(LIMIT + 1);
                if (bytes.length > LIMIT) throw new IOException("UI resource exceeds bound");
            }
        }
        var headers = exchange.getResponseHeaders();
        headers.set(
                "Content-Type",
                code != 200
                        ? "text/plain; charset=utf-8"
                        : file.endsWith(".html")
                                ? "text/html; charset=utf-8"
                                : file.endsWith(".js")
                                        ? "text/javascript; charset=utf-8"
                                        : file.endsWith(".css")
                                                ? "text/css; charset=utf-8"
                                                : "text/plain; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; "
                        + "style-src-attr 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; "
                        + "font-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
