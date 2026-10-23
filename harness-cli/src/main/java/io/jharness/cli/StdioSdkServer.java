package io.jharness.cli;

import io.jharness.api.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;

/** Versioned bounded JSON-RPC adapter to the same host-owned catalog/SDK services as Web. */
final class StdioSdkServer {
    static final String VERSION = "1.0";
    private final URI address;
    private final String credential;
    private final HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

    private static final class Handle {
        final String profile;
        String session = "", firstRequest = "";

        Handle(String profile) {
            this.profile = profile;
        }
    }

    private final Map<String, Handle> handles = new LinkedHashMap<>();
    private boolean initialized, closing;

    StdioSdkServer(URI address, String credential) {
        this.address = address;
        this.credential = credential;
    }

    int run(InputStream input, PrintStream output) throws IOException {
        try (client) {
            while (!closing) {
                String line;
                try {
                    line = frame(input);
                } catch (HarnessException ex) {
                    output.write(
                            (Json.text(failure(null, ex.code())) + "\n")
                                    .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    return 2;
                }
                if (line == null) break;
                String id = null;
                Map<String, Object> response;
                try {
                    var request = Values.object(Json.parse(line));
                    if (request.get("id") instanceof String) id = identifier(request, "id");
                    if (!request.keySet().equals(Set.of("jsonrpc", "id", "method", "params"))
                            || !"2.0".equals(request.get("jsonrpc")))
                        throw error("INVALID_REQUEST");
                    id = identifier(request, "id");
                    String method = field(request, "method", 64);
                    var result = dispatch(method, Values.object(request.get("params")));
                    response = Map.of("jsonrpc", "2.0", "id", id, "result", result);
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    response =
                            failure(
                                    id,
                                    ex instanceof HarnessException h
                                            ? h.code()
                                            : "SDK_OPERATION_FAILED");
                }
                String encoded = Json.text(response);
                if (encoded.getBytes(StandardCharsets.UTF_8).length > 524288)
                    encoded = Json.text(failure(id, "RPC_RESPONSE_LIMIT"));
                output.write((encoded + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (output.checkError()) return 2;
            }
        }
        return 0;
    }

    private Map<String, Object> dispatch(String method, Map<String, Object> p)
            throws IOException, InterruptedException {
        if (method.equals("initialize")) {
            keys(p, "protocolVersion");
            if (!VERSION.equals(p.get("protocolVersion"))) throw error("VERSION_UNSUPPORTED");
            if (initialized) throw error("ALREADY_INITIALIZED");
            initialized = true;
            return Map.of(
                    "protocolVersion",
                    VERSION,
                    "sdkVersion",
                    ApiVersion.CURRENT,
                    "maxPending",
                    32,
                    "maxRequestBytes",
                    65536,
                    "maxResponseBytes",
                    524288,
                    "transport",
                    "stdio-jsonrpc",
                    "methods",
                    List.of(
                            "session.open",
                            "session.close",
                            "sessions.list",
                            "input.send",
                            "request.get",
                            "input.get",
                            "events.list",
                            "questions.list",
                            "question.answer",
                            "approvals.list",
                            "approval.decide",
                            "session.cancel",
                            "session.result",
                            "session.report",
                            "session.artifact",
                            "content.search",
                            "shutdown"));
        }
        if (!initialized) throw error("INITIALIZE_REQUIRED");
        switch (method) {
            case "shutdown" -> {
                keys(p);
                closing = true;
                return Map.of("closing", true);
            }
            case "session.open" -> {
                optionalKeys(p, Set.of(), Set.of("profile", "sessionId"));
                if (handles.size() >= 32) throw error("HANDLE_LIMIT");
                String session = optional(p, "sessionId", ""),
                        profile = optional(p, "profile", "default");
                Handle handle;
                if (!session.isEmpty()) {
                    var actual = http("/v1/tasks/" + identifier(p, "sessionId"), null);
                    if (p.containsKey("profile") && !profile.equals(actual.get("profile")))
                        throw error("SESSION_SCOPE_MISMATCH");
                    handle = new Handle((String) actual.get("profile"));
                    handle.session = session;
                } else {
                    var info = http("/v1/info", null);
                    if (!((List<?>) info.get("profiles")).contains(profile))
                        throw error("UNKNOWN_PROFILE");
                    handle = new Handle(profile);
                }
                String key = UUID.randomUUID().toString();
                handles.put(key, handle);
                return Map.of(
                        "handle",
                        key,
                        "sessionId",
                        handle.session,
                        "profile",
                        handle.profile,
                        "state",
                        session.isEmpty() ? "DRAFT" : "OPEN",
                        "persisted",
                        !session.isEmpty());
            }
            case "session.close" -> {
                keys(p, "handle");
                String key = identifier(p, "handle");
                if (handles.remove(key) == null) throw error("HANDLE_NOT_FOUND");
                return Map.of("released", true);
            }
            case "sessions.list" -> {
                optionalKeys(p, Set.of(), Set.of("profile", "view", "after"));
                return http(
                        "/v1/tasks?view="
                                + optionalEnum(
                                        p, "view", "all", Set.of("all", "active", "archived"))
                                + "&limit=32"
                                + query(p, "profile")
                                + query(p, "after"),
                        null);
            }
            case "input.send" -> {
                optionalKeys(p, Set.of("handle", "text", "requestId"), Set.of("modelId", "kind"));
                var handle = handles.get(identifier(p, "handle"));
                if (handle == null) throw error("HANDLE_NOT_FOUND");
                String request = identifier(p, "requestId"), text = field(p, "text", 16384);
                String kind = optionalEnum(p, "kind", "message", Set.of("message", "supplement"));
                var body = new LinkedHashMap<String, Object>();
                body.put("requestId", request);
                if (p.containsKey("modelId")) body.put("modelId", identifier(p, "modelId"));
                if (handle.session.isEmpty() || handle.firstRequest.equals(request)) {
                    if (!kind.equals("message")) throw error("INITIAL_MESSAGE_REQUIRED");
                    body.put("goal", text);
                    body.put("profile", handle.profile);
                    var task = http("/v1/tasks", body);
                    handle.session = (String) task.get("taskId");
                    handle.firstRequest = request;
                    return Map.of(
                            "task",
                            task,
                            "sessionId",
                            handle.session,
                            "kind",
                            "initial",
                            "requestId",
                            request);
                }
                body.put("text", text);
                body.put("kind", kind);
                var result =
                        new LinkedHashMap<>(http("/v1/tasks/" + handle.session + "/turns", body));
                result.put("sessionId", handle.session);
                result.put("kind", "continuation");
                return result;
            }
            case "request.get" -> {
                keys(p, "requestId");
                return http("/v1/submissions/" + identifier(p, "requestId"), null);
            }
            case "input.get" -> {
                keys(p, "sessionId", "requestId");
                return http(task(p) + "/turns?request=" + identifier(p, "requestId"), null);
            }
            case "events.list" -> {
                optionalKeys(p, Set.of("sessionId"), Set.of("after", "limit"));
                return http(
                        task(p)
                                + "/events?limit="
                                + number(p, "limit", 32, 1, 100)
                                + query(p, "after"),
                        null);
            }
            case "questions.list" -> {
                optionalKeys(p, Set.of("sessionId"), Set.of("offset"));
                return http(task(p) + "/questions?offset=" + number(p, "offset", 0, 0, 64), null);
            }
            case "question.answer" -> {
                keys(
                        p,
                        "sessionId",
                        "questionId",
                        "turnId",
                        "callId",
                        "requestId",
                        "selected",
                        "text",
                        "skip");
                for (String key : List.of("questionId", "turnId", "callId", "requestId"))
                    identifier(p, key);
                if (!(p.get("skip") instanceof Boolean)
                        || !(p.get("text") instanceof String text)
                        || text.length() > 16384
                        || !(p.get("selected") instanceof List<?> selected)
                        || selected.size() > 16
                        || selected.stream().anyMatch(v -> !(v instanceof String)))
                    throw error("INVALID_PARAMS");
                var body = new LinkedHashMap<>(p);
                return http(
                        task(p) + "/questions/" + identifier(p, "questionId") + "/answer", body);
            }
            case "approvals.list" -> {
                keys(p, "sessionId");
                return http(task(p) + "/approvals", null);
            }
            case "approval.decide" -> {
                keys(p, "sessionId", "approvalId", "callId", "approved", "reason");
                if (!(p.get("approved") instanceof Boolean)) throw error("INVALID_PARAMS");
                return http(
                        task(p) + "/approvals/" + identifier(p, "approvalId"),
                        Map.of(
                                "callId",
                                field(p, "callId", 256),
                                "approved",
                                p.get("approved"),
                                "reason",
                                field(p, "reason", 1024)));
            }
            case "session.cancel" -> {
                keys(p, "sessionId");
                return http(task(p) + "/cancel", Map.of());
            }
            case "session.result" -> {
                keys(p, "sessionId");
                return http(task(p), null);
            }
            case "session.report" -> {
                keys(p, "sessionId");
                return http(task(p) + "/report", null);
            }
            case "session.artifact" -> {
                optionalKeys(p, Set.of("sessionId", "artifactId"), Set.of("offset", "version"));
                String artifact = field(p, "artifactId", 101);
                if (!artifact.matches("[a-f0-9-]{36}-[a-f0-9]{64}")) throw error("INVALID_PARAMS");
                return http(
                        task(p)
                                + "/artifacts/"
                                + artifact
                                + "?offset="
                                + number(p, "offset", 0, 0, 4194304)
                                + query(p, "version"),
                        null);
            }
            case "content.search" -> {
                keys(
                        p,
                        "text",
                        "mode",
                        "profile",
                        "view",
                        "sessionId",
                        "cursor",
                        "limit",
                        "rebuild");
                field(p, "text", 256);
                optionalEnum(p, "mode", "tokens", Set.of("tokens", "phrase"));
                optionalEnum(p, "view", "active", Set.of("active", "archived", "all"));
                field(p, "profile", 64);
                field(p, "sessionId", 128);
                field(p, "cursor", 1024);
                number(p, "limit", 20, 1, 32);
                if (!(p.get("rebuild") instanceof Boolean)) throw error("INVALID_PARAMS");
                return http("/v1/search", p);
            }
            default -> throw error("METHOD_NOT_FOUND");
        }
    }

    private Map<String, Object> http(String path, Map<String, Object> body)
            throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(address.resolve(path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + credential);
        if (body == null) request.GET();
        else
            request.header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    Json.text(body), StandardCharsets.UTF_8));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            byte[] bytes = input.readNBytes(524289);
            if (bytes.length > 524288) throw error("RPC_RESPONSE_LIMIT");
            Map<String, Object> value;
            try {
                value = Values.object(Json.parse(decode(bytes)));
            } catch (HarnessException ex) {
                if (response.statusCode() >= 400) throw error("SDK_HTTP_ERROR");
                throw ex;
            }
            if (response.statusCode() >= 400)
                throw error(
                        value.get("error") instanceof String code && code.matches("[A-Z0-9_]{1,64}")
                                ? code
                                : "SDK_OPERATION_FAILED");
            return value;
        }
    }

    private static String frame(InputStream input) throws IOException {
        var buffer = new ByteArrayOutputStream();
        for (int b; (b = input.read()) != -1; ) {
            if (b == '\n') return decode(buffer.toByteArray());
            if (buffer.size() >= 65536) throw error("RPC_REQUEST_LIMIT");
            buffer.write(b);
        }
        if (buffer.size() != 0) throw error("TRUNCATED_FRAME");
        return null;
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw error("INVALID_UTF8");
        }
    }

    private static Map<String, Object> failure(String id, String code) {
        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put(
                "error",
                Map.of(
                        "code",
                        code.equals("METHOD_NOT_FOUND")
                                ? -32601
                                : code.equals("INVALID_REQUEST") ? -32600 : -32000,
                        "message",
                        "SDK operation rejected",
                        "data",
                        Map.of("code", code, "protocolVersion", VERSION)));
        return response;
    }

    private static void keys(Map<String, Object> p, String... keys) {
        if (!p.keySet().equals(Set.of(keys))) throw error("INVALID_PARAMS");
    }

    private static void optionalKeys(
            Map<String, Object> p, Set<String> required, Set<String> optional) {
        var allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!p.keySet().containsAll(required) || !allowed.containsAll(p.keySet()))
            throw error("INVALID_PARAMS");
    }

    private static String field(Map<String, Object> p, String key, int max) {
        if (!(p.get(key) instanceof String value) || value.length() > max)
            throw error("INVALID_PARAMS");
        return value;
    }

    private static String identifier(Map<String, Object> p, String key) {
        int max = key.equals("sessionId") ? 128 : 64;
        String value = field(p, key, max);
        if (value.isEmpty() || !value.matches("[A-Za-z0-9_-]+")) throw error("INVALID_PARAMS");
        return value;
    }

    private static String optional(Map<String, Object> p, String key, String fallback) {
        return p.containsKey(key) ? identifier(p, key) : fallback;
    }

    private static String optionalEnum(
            Map<String, Object> p, String key, String fallback, Set<String> values) {
        String value = p.containsKey(key) ? field(p, key, 32) : fallback;
        if (!values.contains(value)) throw error("INVALID_PARAMS");
        return value;
    }

    private static String task(Map<String, Object> p) {
        return "/v1/tasks/" + identifier(p, "sessionId");
    }

    private static String query(Map<String, Object> p, String key) {
        return p.containsKey(key) && !field(p, key, 64).isEmpty()
                ? "&" + key + "=" + identifier(p, key)
                : "";
    }

    private static int number(Map<String, Object> p, String key, int fallback, int min, int max) {
        if (!p.containsKey(key)) return fallback;
        if (!(p.get(key) instanceof Integer value) || value < min || value > max)
            throw error("INVALID_PARAMS");
        return value;
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "SDK operation rejected");
    }
}
