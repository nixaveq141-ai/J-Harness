package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.Values;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class StdioSdkServerTest {
    private String run(byte[] bytes, int code) throws Exception {
        var output = new ByteArrayOutputStream();
        int actual =
                new StdioSdkServer(URI.create("http://127.0.0.1:1"), "synthetic-transport-only")
                        .run(
                                new ByteArrayInputStream(bytes),
                                new PrintStream(output, true, StandardCharsets.UTF_8));
        assertEquals(code, actual);
        String result = output.toString(StandardCharsets.UTF_8);
        assertFalse(result.contains("synthetic-transport-only"));
        assertFalse(result.contains("\r"));
        return result;
    }

    private static String request(String id, String method, Map<String, Object> p) {
        return Json.text(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", p)) + "\n";
    }

    private static String error(String line) {
        var envelope = Values.object(Json.parse(line));
        assertTrue(envelope.get("error") instanceof Map<?, ?>, "Expected JSON-RPC error object");
        var error = Values.object(envelope.get("error"));
        assertTrue(error.get("data") instanceof Map<?, ?>, "Expected error data object");
        return assertInstanceOf(
                String.class,
                Values.object(error.get("data")).get("code"),
                "Expected fixed error code");
    }

    @Test
    void exactVersionInitializationUnknownFieldsAndMethodsAreExecutionBarriers() throws Exception {
        String input =
                request("before", "input.send", Map.of())
                        + request("future", "initialize", Map.of("protocolVersion", "99"))
                        + request("init", "initialize", Map.of("protocolVersion", "1.0"))
                        + request("fields", "session.open", Map.of("path", "outside"))
                        + request("method", "unknown", Map.of())
                        + request("end", "shutdown", Map.of());
        String[] rows = run(input.getBytes(StandardCharsets.UTF_8), 0).split("\n");
        assertEquals(6, rows.length);
        assertEquals("INITIALIZE_REQUIRED", error(rows[0]));
        assertEquals("VERSION_UNSUPPORTED", error(rows[1]));
        assertEquals(
                "1.0",
                Values.object(Values.object(Json.parse(rows[2])).get("result"))
                        .get("protocolVersion"));
        assertEquals("INVALID_PARAMS", error(rows[3]));
        assertEquals("METHOD_NOT_FOUND", error(rows[4]));
    }

    @Test
    void truncatedOversizedAndInvalidUtf8FramesCloseWithoutInterpretingBytes() throws Exception {
        assertEquals(
                "TRUNCATED_FRAME",
                error(run("{unfinished".getBytes(StandardCharsets.UTF_8), 2).trim()));
        assertEquals(
                "RPC_REQUEST_LIMIT",
                error(run("x".repeat(65537).getBytes(StandardCharsets.UTF_8), 2).trim()));
        assertEquals(
                "INVALID_UTF8", error(run(new byte[] {(byte) 0xc3, (byte) 0x28, 10}, 2).trim()));
    }

    @Test
    void duplicateFieldsRejectRatherThanSelectingLastValue() throws Exception {
        String bad =
                "{\"jsonrpc\":\"2.0\",\"id\":\"one\",\"id\":\"two\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1.0\"}}\n";
        String[] rows =
                run(
                                (bad
                                                + request(
                                                        "ok",
                                                        "initialize",
                                                        Map.of("protocolVersion", "1.0")))
                                        .getBytes(StandardCharsets.UTF_8),
                                0)
                        .split("\n");
        assertEquals("INVALID_JSON", error(rows[0]));
        assertEquals("ok", Values.object(Json.parse(rows[1])).get("id"));
    }
}
