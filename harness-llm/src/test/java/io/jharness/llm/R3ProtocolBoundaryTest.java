package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3ProtocolBoundaryTest {
    @Test
    void terminalCannotIntroduceUnstreamedFunctionCall() {
        var stream = new ProtocolStream(ModelProtocol.RESPONSES, new ObjectMapper());
        stream.frame(Map.of("type", "response.created", "response", Map.of("id", "r")));
        assertEquals(
                "MODEL_STREAM_INCOMPLETE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        stream.frame(
                                                Map.of(
                                                        "type",
                                                        "response.completed",
                                                        "response",
                                                        Map.of(
                                                                "id",
                                                                "r",
                                                                "output",
                                                                List.of(
                                                                        Map.of(
                                                                                "type",
                                                                                "function_call"))))))
                        .code());
    }

    @Test
    void sequenceFreeLegacyFramesCannotMixWithSequencedFrames() {
        var stream = new ProtocolStream(ModelProtocol.RESPONSES, new ObjectMapper());
        stream.frame(
                Map.of(
                        "type",
                        "response.created",
                        "sequence_number",
                        0,
                        "response",
                        Map.of("id", "r")));
        assertEquals(
                "MODEL_BAD_RESPONSE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        stream.frame(
                                                Map.of(
                                                        "type",
                                                        "response.in_progress",
                                                        "response",
                                                        Map.of("id", "r"))))
                        .code());
    }

    @Test
    void configAcceptsCaseInsensitiveUrisAndRejectsUnsafeHeaderEarly() {
        assertNotNull(config("HTTPS://EXAMPLE.COM", "synthetic-valid"));
        assertNotNull(config("HTTP://LOCALHOST:1234", "synthetic-valid"));
        for (String key : List.of("a\tb", "a\u0000b", "中文", "a".repeat(8193)))
            assertThrows(IllegalArgumentException.class, () -> config("https://example.com", key));
    }

    private OpenAiCompatibleConfig config(String uri, String key) {
        return new OpenAiCompatibleConfig(
                URI.create(uri),
                "fixture",
                key,
                Duration.ofSeconds(1),
                4096,
                0,
                new ModelCapabilities(true, true, false, false, 4096));
    }
}
