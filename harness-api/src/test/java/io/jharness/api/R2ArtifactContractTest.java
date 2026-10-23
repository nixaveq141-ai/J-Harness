package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class R2ArtifactContractTest {
    @Test
    void defaultTextReaderContinuesShortUtf16BlocksUntilEmptyEof() throws Exception {
        String text = "中😀文second";
        ArtifactStore store =
                new ArtifactStore() {
                    public Artifact put(String session, String content) {
                        throw new UnsupportedOperationException();
                    }

                    public String read(String session, String id, int offset, int limit) {
                        assertTrue(offset >= 0);
                        return offset >= text.length()
                                ? ""
                                : text.substring(offset, Math.min(text.length(), offset + 2));
                    }
                };
        assertEquals(text, store.readText("s", "artifact"));
        assertEquals("", store.read("s", "artifact", text.length() + 1, 1));
    }
}
