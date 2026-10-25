package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class R6UiManifestTest {
    @Test
    void completeManifestSupportsDottedChunksButRejectsOversizeDuplicateAndMalformedNames()
            throws Exception {
        assertEquals(
                Set.of("index.html", "ui-license.txt", "assets/chunk.hash.js"),
                UiResources.parseManifest(
                        "index.html\nui-license.txt\nassets/chunk.hash.js\n"
                                .getBytes(StandardCharsets.UTF_8)));
        for (String value :
                new String[] {
                    "index.html\n",
                    "index.html\nui-license.txt\nindex.html\n",
                    "index.html\nui-license.txt\n../private\n"
                })
            assertThrows(
                    IOException.class,
                    () -> UiResources.parseManifest(value.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> UiResources.parseManifest(new byte[16385]));
    }
}
