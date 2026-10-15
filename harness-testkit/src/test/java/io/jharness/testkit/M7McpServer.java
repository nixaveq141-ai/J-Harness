package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Actual newline-delimited stdio peer for protocol and disconnect fixtures. */
public final class M7McpServer {
    private static final ObjectMapper JSON = new ObjectMapper();

    private M7McpServer() {}

    public static void main(String[] args) throws Exception {
        Path trace = Path.of(args[0]), marker = Path.of(args[1]);
        String mode = args[2];
        Files.writeString(
                trace.resolveSibling("pid.txt"), Long.toString(ProcessHandle.current().pid()));
        System.err.println("warning: mcp-secret-token (must never become an event)");
        try (var reader =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                                System.in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var request = JSON.readValue(line, Map.class);
                Files.writeString(
                        trace, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (!(request.get("method") instanceof String method)) continue;
                Object id = request.get("id");
                if (method.equals("notifications/initialized")) continue;
                if (method.equals("initialize")) {
                    send(
                            Map.of(
                                    "jsonrpc",
                                    "2.0",
                                    "id",
                                    id,
                                    "result",
                                    Map.of(
                                            "protocolVersion",
                                            "2025-11-25",
                                            "serverInfo",
                                            Map.of("name", "fixture", "version", "1"),
                                            "capabilities",
                                            Map.of("tools", Map.of()))));
                } else if (method.equals("tools/list")) {
                    send(Map.of("jsonrpc", "2.0", "id", "server-ping", "method", "ping"));
                    var input =
                            mode.equals("bad-schema")
                                    ? Map.of("$ref", "https://untrusted.invalid/schema")
                                    : schema();
                    send(
                            Map.of(
                                    "jsonrpc",
                                    "2.0",
                                    "id",
                                    id,
                                    "result",
                                    Map.of(
                                            "tools",
                                            List.of(
                                                    Map.of(
                                                            "name",
                                                            "echo",
                                                            "description",
                                                            "untrusted hint",
                                                            "annotations",
                                                            Map.of("readOnlyHint", true),
                                                            "inputSchema",
                                                            input,
                                                            "outputSchema",
                                                            output())))));
                } else if (method.equals("tools/call")) {
                    Files.writeString(marker, "called");
                    if (mode.equals("drop")) return;
                    if (mode.equals("many-nodes")) {
                        send(
                                Map.of(
                                        "jsonrpc",
                                        "2.0",
                                        "id",
                                        id,
                                        "result",
                                        Map.of(
                                                "content",
                                                Collections.nCopies(
                                                        100001,
                                                        Map.of("type", "text", "text", "x")))));
                        continue;
                    }
                    if (mode.equals("hang")) {
                        Thread.sleep(10000);
                        return;
                    }
                    if (mode.equals("changed")) {
                        send(
                                Map.of(
                                        "jsonrpc",
                                        "2.0",
                                        "method",
                                        "notifications/tools/list_changed"));
                        return;
                    }
                    var parameters = (Map<?, ?>) request.get("params");
                    var arguments = (Map<?, ?>) parameters.get("arguments");
                    Object value = mode.equals("bad-output") ? 7 : arguments.get("text");
                    send(
                            Map.of(
                                    "jsonrpc",
                                    "2.0",
                                    "id",
                                    mode.equals("wrong-id") ? "wrong" : id,
                                    "result",
                                    Map.of(
                                            "content",
                                            List.of(
                                                    Map.of(
                                                            "type",
                                                            "text",
                                                            "text",
                                                            value.toString())),
                                            "structuredContent",
                                            Map.of("answer", value))));
                }
            }
        }
    }

    public static Map<String, Object> schema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of("text", Map.of("type", "string")),
                "required",
                List.of("text"),
                "additionalProperties",
                false);
    }

    public static Map<String, Object> output() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of("answer", Map.of("type", "string")),
                "required",
                List.of("answer"),
                "additionalProperties",
                false);
    }

    private static void send(Map<String, Object> value) throws Exception {
        System.out.println(JSON.writeValueAsString(value));
        System.out.flush();
    }
}
