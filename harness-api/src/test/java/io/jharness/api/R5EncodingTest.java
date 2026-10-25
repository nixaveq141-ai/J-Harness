package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class R5EncodingTest {
    @Test
    void exactBytesTokensDepthAndStreamingHaveTheSameBoundary() throws Exception {
        var value = Map.of("x", List.of("中文😀\n", true));
        byte[] encoded = JsonEncoding.encode(value, 100, 8, 2, "LIMIT");
        assertEquals("{\"x\":[\"中文😀\\n\",true]}", new String(encoded, StandardCharsets.UTF_8));
        assertArrayEquals(encoded, JsonEncoding.encode(value, encoded.length, 8, 2, "LIMIT"));
        assertThrows(
                HarnessException.class,
                () -> JsonEncoding.encode(value, encoded.length - 1, 8, 2, "LIMIT"));
        assertThrows(HarnessException.class, () -> JsonEncoding.encode(value, 100, 6, 2, "LIMIT"));
        assertThrows(HarnessException.class, () -> JsonEncoding.encode(value, 100, 8, 1, "LIMIT"));
        var out = new ByteArrayOutputStream();
        var big = List.of("x".repeat(20000));
        JsonEncoding.writeTo(big, 21000, 3, 1, "LIMIT", out);
        assertArrayEquals(JsonEncoding.encode(big, 21000, 3, 1, "LIMIT"), out.toByteArray());
        var limited = new ByteArrayOutputStream();
        assertThrows(
                HarnessException.class,
                () -> JsonEncoding.writeTo(big, 9000, 3, 1, "LIMIT", limited));
        assertTrue(limited.size() <= 9000);
    }

    @Test
    void hostileValuesNeverCallArbitraryToStringAndInvalidNumbersAreRejected() {
        Object hostile =
                new Object() {
                    public String toString() {
                        throw new AssertionError("must not stringify");
                    }
                };
        assertEquals(
                "INVALID_JSON",
                assertThrows(
                                HarnessException.class,
                                () -> JsonEncoding.encode(List.of(hostile), 100, 10, 2, "LIMIT"))
                        .code());
        for (Object value : List.of(Double.NaN, Double.POSITIVE_INFINITY, Map.of(1, "not-string")))
            assertThrows(
                    HarnessException.class, () -> JsonEncoding.encode(value, 100, 10, 2, "LIMIT"));
        assertEquals("\"\\ud800\"", JsonEncoding.text("\ud800", 20, "LIMIT"));
    }

    @Test
    void utf8AndDisplayMetricsHandleExpansionWithoutMaterializingDisplayCopies() {
        assertEquals(10, JsonEncoding.utf8Length("中文😀", 10, "LIMIT"));
        assertThrows(HarnessException.class, () -> JsonEncoding.utf8("中文😀", 9, "LIMIT"));
        var data =
                Map.of("tools", List.of(Map.of("description", "x".repeat(10000))), "flag", false);
        assertEquals(data.toString().length(), JsonEncoding.displayCharacters(data));
        assertEquals("a", JsonEncoding.prefix("a😀b", 2));
    }

    @Test
    void lowercaseExpansionOffsetsAndSnippetEdgesStayInOriginalUnicodeCoordinates() {
        String body = "İ".repeat(200) + "😀needle " + "z".repeat(600);
        int folded = body.toLowerCase(Locale.ROOT).indexOf("needle");
        String snippet = UnicodeSearch.snippet(body, folded, 3, 20);
        assertTrue(snippet.contains("😀needle"));
        assertFalse(Character.isLowSurrogate(snippet.charAt(0)));
        assertFalse(Character.isHighSurrogate(snippet.charAt(snippet.length() - 1)));
    }
}
