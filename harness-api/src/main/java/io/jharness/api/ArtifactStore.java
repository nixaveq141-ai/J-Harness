package io.jharness.api;

import java.io.IOException;

public interface ArtifactStore {
    Artifact put(String sessionId, String content) throws IOException;

    String read(String sessionId, String id, int offset, int limit) throws IOException;

    /** Complete verified text for bounded host inspection, not an unbounded storage read. */
    default String readText(String sessionId, String id) throws IOException {
        var text = new StringBuilder();
        for (int offset = 0; ; ) {
            String part = read(sessionId, id, offset, 262144);
            text.append(part);
            offset += part.length();
            if (text.length() > 4194304)
                throw new HarnessException("ARTIFACT_LIMIT", "Artifact exceeds bound");
            if (part.length() < 262144) return text.toString();
        }
    }

    record Artifact(String id, long bytes, String sha256) {}
}
