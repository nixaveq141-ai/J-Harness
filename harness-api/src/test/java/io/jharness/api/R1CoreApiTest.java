package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class R1CoreApiTest {
    @Test
    void sanitizerCannotInjectAnErrorIntoSuccess() {
        var result =
                new ToolResult("id", ToolStatus.SUCCESS, Map.of(), "", Map.of(), Optional.empty());
        assertEquals(
                "INVALID_SANITIZER_OUTPUT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ToolResults.sanitized(
                                                result,
                                                input -> {
                                                    var changed = new LinkedHashMap<>(input);
                                                    changed.put(
                                                            "error",
                                                            Map.of("code", "X", "message", "X"));
                                                    return changed;
                                                }))
                        .code());
    }

    @Test
    void checkpointRejectsNullBeforeFlush() {
        var writes = new int[1];
        SessionWriter writer =
                new SessionWriter() {
                    public String append(SessionEvent e) {
                        return e.eventId();
                    }

                    public void flush() {
                        writes[0]++;
                    }

                    public void close() {}
                };
        assertThrows(NullPointerException.class, () -> EventSupport.checkpoint(writer, null));
        assertEquals(0, writes[0]);
    }

    @Test
    void errorInfoAndSuccessHaveConsistentSchema() {
        assertThrows(
                IllegalArgumentException.class, () -> new ToolResult.ErrorInfo(" ", "failure"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ToolResults.failure("id", ToolStatus.FAILED, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ToolResult(
                                "id",
                                ToolStatus.SUCCESS,
                                Map.of(),
                                "",
                                Map.of(),
                                Optional.of(new ToolResult.ErrorInfo("ERROR", "failure"))));
    }

    @Test
    void sanitizerAliasesAndKeyCollisionsAreFailClosed() {
        var redactor = new SecretRedactor(List.of("synthetic-secret"));
        var source = new LinkedHashMap<String, Object>();
        source.put("accessToken", "unregistered token");
        source.put("nested", Map.of("Client-Secret", "unregistered secret", "tokenCount", 23));
        var clean = redactor.sanitize(source);
        assertEquals("[REDACTED]", clean.get("accessToken"));
        assertEquals(23, Values.object(clean.get("nested")).get("tokenCount"));
        assertEquals("[REDACTED]", Values.object(clean.get("nested")).get("Client-Secret"));
        assertEquals("unregistered token", source.get("accessToken"));
        assertThrows(
                HarnessException.class,
                () -> redactor.sanitize(Map.of("synthetic-secret", 1, "[REDACTED]", 2)));
    }

    @Test
    void streamingNeverSplitsReplacementAndRetainsOnlyPotentialSecretSuffix() {
        var redactor = new SecretRedactor(List.of("secret"));
        assertEquals("hello [REDACTED]", redactor.streamingText("hello secret"));
        assertEquals("hello ", redactor.streamingText("hello secr"));
        assertEquals("safe text", redactor.streamingText("safe text"));
    }

    @Test
    void metadataToStringOmitsRawInputs() {
        assertFalse(
                new ConfigView(Map.of("arbitrary", "sentinel-private"))
                        .toString()
                        .contains("sentinel-private"));
        assertFalse(
                new ToolInvocation(
                                "id",
                                "tool",
                                Map.of("arbitrary", "sentinel-private"),
                                SideEffectLevel.READ_ONLY)
                        .toString()
                        .contains("sentinel-private"));
        assertFalse(
                new CommandRequest(
                                "sentinel-private",
                                CommandRequest.Mode.ARGV,
                                List.of("fixture", "sentinel-private"),
                                new ExecPath("sentinel-private"),
                                java.time.Duration.ofSeconds(1))
                        .toString()
                        .contains("sentinel-private"));
    }

    @Test
    void sanitizationShapeFailureHasStableCode() {
        var result =
                new ToolResult(
                        "id", ToolStatus.SUCCESS, Map.of("x", 1), "ok", Map.of(), Optional.empty());
        var error =
                assertThrows(
                        HarnessException.class,
                        () ->
                                ToolResults.sanitized(
                                        result,
                                        value ->
                                                Map.of(
                                                        "value", "removed", "display", Map.of(),
                                                        "content", "ok")));
        assertEquals("INVALID_SANITIZER_OUTPUT", error.code());
    }

    @Test
    void rejectionMetadataIsBoundedAndHasNoRawProviderInput() {
        var rejection = new ModelRejectedException(OptionalLong.of(1000));
        assertEquals(429, rejection.statusCode());
        assertEquals(1000, rejection.retryAfterMillis().orElseThrow());
        assertEquals("MODEL_RATE_LIMIT", rejection.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelRejectedException(OptionalLong.of(-1)));
    }

    @Test
    void repeatedStreamingFramesRemainBoundedAndNeverExposeCrossBoundarySecret() {
        var redactor = new SecretRedactor(List.of("synthetic-secret"));
        String cumulative = "x".repeat(65530) + "synthetic-secret" + "y".repeat(100000);
        for (int i = 0; i < 20; i++) {
            String display = redactor.streamingText(cumulative);
            assertTrue(display.length() <= 65536);
            assertFalse(display.contains("synthe"));
            assertFalse(display.contains("synthetic-secret"));
        }
        var expanded = new SecretRedactor(List.of("a")).streamingUpdate("a".repeat(65536));
        assertTrue(expanded.truncated());
        assertTrue(expanded.text().length() <= 65536);
        assertEquals("[REDACTED]".repeat(expanded.text().length() / 10), expanded.text());
        var longInput = redactor.streamingUpdate("x".repeat(70000));
        assertTrue(longInput.truncated());
        assertEquals(65536, longInput.text().length());
    }
}
