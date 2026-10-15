package io.jharness.testkit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jharness.api.*;
import io.jharness.process.LocalProcessRunner;
import io.jharness.session.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in packaged CLI -> real HTTP approval -> SDK -> local/Docker evidence. No paid models. */
public final class M7WebDockerProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "probe_web_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private M7WebDockerProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException("docker image baseDir workerJar outputJson");
        Path base = Path.of(args[2]).toAbsolutePath().resolve("web-" + UUID.randomUUID());
        Files.createDirectories(base);
        Path jar = Path.of(args[3]).toRealPath();
        var cases = new LinkedHashMap<String, Object>();
        cases.put("local", run(args[0], args[1], base.resolve("local"), jar, false));
        cases.put("docker", run(args[0], args[1], base.resolve("docker"), jar, true));
        var report = new LinkedHashMap<String, Object>();
        report.put("milestone", "M7");
        report.put("slice", "packaged-cli-web-sdk-docker");
        report.put("actualDocker", true);
        report.put("image", args[1]);
        report.put("cases", cases);
        report.put("result", "SUCCESS");
        report.put("cleanupVerified", true);
        report.put("capturedAt", java.time.Instant.now().toString());
        report.put(
                "jarSha256",
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(jar))));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[4]).toFile(), report);
        System.out.println("M7 packaged CLI Web/local/Docker probe SUCCESS");
    }

    private static Map<String, Object> run(
            String docker, String image, Path root, Path jar, boolean container) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("input.txt"), "before");
        var count = new AtomicInteger();
        var captured = new CopyOnWriteArrayList<Map<String, Object>>();
        var model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.createContext(
                "/v1/chat/completions",
                exchange -> {
                    captured.add(
                            JSON.readValue(
                                    exchange.getRequestBody().readNBytes(1024 * 1024),
                                    new TypeReference<Map<String, Object>>() {}));
                    int step = count.getAndIncrement();
                    var message = new LinkedHashMap<String, Object>();
                    message.put("role", "assistant");
                    message.put("content", "fixture");
                    if (step < 2) {
                        String tool = step == 0 ? "read_file" : "write_file";
                        var arguments =
                                step == 0
                                        ? Map.of("path", "input.txt")
                                        : Map.of("path", "marker.txt", "content", "approved");
                        message.put(
                                "tool_calls",
                                List.of(
                                        Map.of(
                                                "id",
                                                "call-" + step,
                                                "type",
                                                "function",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        tool,
                                                        "arguments",
                                                        JSON.writeValueAsString(arguments)))));
                    }
                    byte[] reply =
                            JSON.writeValueAsBytes(
                                    Map.of(
                                            "id",
                                            "fixture-" + step,
                                            "choices",
                                            List.of(
                                                    Map.of(
                                                            "index",
                                                            0,
                                                            "message",
                                                            message,
                                                            "finish_reason",
                                                            step < 2 ? "tool_calls" : "stop")),
                                            "usage",
                                            Map.of(
                                                    "prompt_tokens",
                                                    100,
                                                    "completion_tokens",
                                                    20,
                                                    "total_tokens",
                                                    120)));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, reply.length);
                    try (exchange) {
                        exchange.getResponseBody().write(reply);
                    }
                });
        model.start();
        Process process = null;
        try (var client = HttpClient.newHttpClient()) {
            var execution =
                    container
                            ? Map.of(
                                    "kind",
                                    "docker",
                                    "dockerExecutable",
                                    docker,
                                    "image",
                                    image,
                                    "workerJar",
                                    jar.toString())
                            : Map.of("kind", "local");
            var config =
                    Map.of(
                            "baseUrl",
                            "http://127.0.0.1:" + model.getAddress().getPort() + "/v1",
                            "model",
                            "fixture",
                            "apiKeyEnv",
                            "FIXTURE_API_KEY",
                            "maxSteps",
                            4,
                            "durationSeconds",
                            60,
                            "requestTimeoutSeconds",
                            10,
                            "ecosystem",
                            Map.of(
                                    "environment",
                                    execution,
                                    "web",
                                    Map.of(
                                            "port",
                                            0,
                                            "tokenEnv",
                                            "FIXTURE_WEB_TOKEN",
                                            "approvalSeconds",
                                            15)));
            Path configPath = root.resolve("host-config.json");
            JSON.writeValue(configPath.toFile(), config);
            var command =
                    List.of(
                            Path.of(
                                            System.getProperty("java.home"),
                                            "bin",
                                            System.getProperty("os.name").startsWith("Windows")
                                                    ? "java.exe"
                                                    : "java")
                                    .toString(),
                            "-jar",
                            jar.toString(),
                            "serve",
                            "--workspace",
                            root.toString(),
                            "--config",
                            configPath.toString(),
                            "--require-read",
                            "input.txt",
                            "--expect-file",
                            "marker.txt");
            var builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(LocalProcessRunner.buildEnvironment());
            builder.environment().put("FIXTURE_API_KEY", "fixture-only-not-paid");
            builder.environment().put("FIXTURE_WEB_TOKEN", TOKEN);
            process = builder.start();
            final Process owned = process;
            var lines = new LinkedBlockingQueue<String>(128);
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try (var input =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        owned.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = input.readLine()) != null)
                                        if (!lines.offer(line)) break;
                                } catch (IOException ignored) {
                                }
                            });
            String startup = lines.poll(15, TimeUnit.SECONDS);
            require(startup != null && startup.startsWith("WEB "), "CLI Web startup: " + startup);
            URI address = URI.create(startup.split(" ")[1]);
            require(
                    http(client, address, "GET", "/v1/tasks", null, false).statusCode() == 401,
                    "Authentication");
            var submission =
                    Map.of(
                            "profile",
                            "default",
                            "goal",
                            "read input and write marker",
                            "requestId",
                            "fixed-web-probe");
            var created = http(client, address, "POST", "/v1/tasks", submission, true);
            require(created.statusCode() == 202, "Create: " + created.body());
            String task = (String) object(created).get("taskId");
            Map<String, Object> pending = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (System.nanoTime() < deadline) {
                var list =
                        (List<?>)
                                object(
                                                http(
                                                        client,
                                                        address,
                                                        "GET",
                                                        "/v1/tasks/" + task + "/approvals",
                                                        null,
                                                        true))
                                        .get("approvals");
                if (!list.isEmpty()) {
                    pending = Values.object(list.getFirst());
                    break;
                }
                Thread.sleep(50);
            }
            require(pending != null, "Approval available");
            require(!Files.exists(root.resolve("marker.txt")), "No write before approval");
            String approval = "/v1/tasks/" + task + "/approvals/" + pending.get("approvalId");
            require(
                    http(
                                            client,
                                            address,
                                            "POST",
                                            approval,
                                            Map.of(
                                                    "approved",
                                                    true,
                                                    "reason",
                                                    "checked fixed fixture",
                                                    "callId",
                                                    pending.get("callId")),
                                            true)
                                    .statusCode()
                            == 200,
                    "Exact approval");
            Map<String, Object> state = Map.of();
            while (System.nanoTime() < deadline) {
                state = object(http(client, address, "GET", "/v1/tasks/" + task, null, true));
                if (Set.of("FINISHED", "UNKNOWN").contains(state.get("phase"))) break;
                Thread.sleep(50);
            }
            require(
                    "FINISHED".equals(state.get("phase"))
                            && "COMPLETED".equals(state.get("status")),
                    "Task end: " + state);
            require(
                    "approved".equals(Files.readString(root.resolve("marker.txt"))),
                    "Actual workspace mutation");
            require(
                    task.equals(
                            object(http(client, address, "POST", "/v1/tasks", submission, true))
                                    .get("taskId")),
                    "Submission deduplicated");
            require(count.get() == 3, "No model replay");
            var page =
                    http(
                            client,
                            address,
                            "GET",
                            "/v1/tasks/" + task + "/events?limit=100",
                            null,
                            true);
            require(
                    page.statusCode() == 200
                            && page.body().contains("tool.completed")
                            && !page.body().contains(TOKEN),
                    "Events and redaction");
            var repository = new JsonlSessionRepository(root.resolve(".jharness/sessions"));
            var events = repository.openReader(task).events();
            int reconstructed = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested")) {
                    var request =
                            new EventRequestJournal()
                                    .reconstruct(events.subList(0, i + 1), events.get(i).eventId());
                    var wire = new LinkedHashMap<>(request.parameters());
                    var messages = new ArrayList<Map<String, Object>>();
                    for (var message : request.messages()) {
                        var encoded = new LinkedHashMap<String, Object>();
                        encoded.put("role", message.get("role"));
                        encoded.put("content", message.get("content"));
                        if (message.containsKey("tool_call_id"))
                            encoded.put("tool_call_id", message.get("tool_call_id"));
                        if (message.get("tool_calls") instanceof List<?> calls) {
                            var encodedCalls = new ArrayList<Map<String, Object>>();
                            for (Object raw : calls) {
                                var call = Values.object(raw);
                                var function = Values.object(call.get("function"));
                                encodedCalls.add(
                                        Map.of(
                                                "id",
                                                call.get("id"),
                                                "type",
                                                "function",
                                                "function",
                                                Map.of(
                                                        "name",
                                                        function.get("name"),
                                                        "arguments",
                                                        JSON.writeValueAsString(
                                                                function.get("arguments")))));
                            }
                            encoded.put("tool_calls", encodedCalls);
                        }
                        messages.add(encoded);
                    }
                    wire.put("messages", messages);
                    if (!request.tools().isEmpty()) wire.put("tools", request.tools());
                    require(
                            JSON.readValue(
                                            JSON.writeValueAsBytes(wire),
                                            new TypeReference<Map<String, Object>>() {})
                                    .equals(captured.get(reconstructed++)),
                            "Reconstructed actual HTTP request");
                }
            require(
                    reconstructed == 3 && SessionRecovery.inspect(events).ready(),
                    "Durable reconstruction and recovery");
            var closed =
                    events.stream()
                            .filter(e -> e.type().equals("environment.closed"))
                            .findFirst()
                            .orElseThrow();
            require(
                    "SUCCESS".equals(closed.payload().get("status")),
                    "Environment close confirmed");
            if (container) {
                var verify =
                        new ProcessBuilder(
                                        docker,
                                        "ps",
                                        "--all",
                                        "--filter",
                                        "label=io.jharness.owner="
                                                + closed.payload().get("environmentId"),
                                        "--format",
                                        "{{.Names}}")
                                .start();
                require(
                        verify.waitFor(5, TimeUnit.SECONDS)
                                && verify.exitValue() == 0
                                && verify.getInputStream().readAllBytes().length == 0,
                        "Owned container removed");
            }
            process.getOutputStream().write(":quit\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            require(
                    process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0,
                    "Web host clean exit");
            return Map.of(
                    "status",
                    "COMPLETED",
                    "actualHttp",
                    true,
                    "approvalBeforeWrite",
                    true,
                    "requestsReconstructedAgainstWire",
                    reconstructed,
                    "submissionDeduplicated",
                    true,
                    "environmentClosed",
                    true,
                    "hostExitCode",
                    0);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            model.stop(0);
        }
    }

    private static HttpResponse<String> http(
            HttpClient client,
            URI address,
            String method,
            String path,
            Object body,
            boolean authorized)
            throws Exception {
        var builder = HttpRequest.newBuilder(address.resolve(path)).timeout(Duration.ofSeconds(8));
        if (authorized) builder.header("Authorization", "Bearer " + TOKEN);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofByteArray(
                                                JSON.writeValueAsBytes(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> object(HttpResponse<String> response) throws IOException {
        return JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
