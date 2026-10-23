package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3McpProtocolTest {
    @Test
    void nullIdIsARequestAndGetsStructuredRejection() {
        var request = new LinkedHashMap<String, Object>();
        request.put("jsonrpc", "2.0");
        request.put("id", null);
        request.put("method", "unsupported");
        assertTrue(McpIo.expectsResponse(request));
        var rejection = McpIo.rejection(request);
        assertTrue(rejection.containsKey("id"));
        assertNull(rejection.get("id"));
        assertEquals(-32601, Values.object(rejection.get("error")).get("code"));
        assertTrue(McpIo.response(rejection, null));
    }

    @Test
    void resultAndErrorMustBeExclusiveWithExplicitId() {
        for (var frame :
                List.of(
                        Map.<String, Object>of("jsonrpc", "2.0", "id", "x"),
                        Map.<String, Object>of(
                                "jsonrpc", "2.0", "id", "x", "result", Map.of(), "error", Map.of()),
                        Map.<String, Object>of("jsonrpc", "2.0", "result", Map.of())))
            assertEquals(
                    "MCP_PROTOCOL_ERROR",
                    assertThrows(HarnessException.class, () -> McpIo.response(frame, "x")).code());
        assertTrue(McpIo.response(Map.of("jsonrpc", "2.0", "id", "x", "result", Map.of()), "x"));
    }
}
