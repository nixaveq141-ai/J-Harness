package io.jharness.api;

import java.io.IOException;

public interface ArtifactStore {
    Artifact put(String sessionId, String content) throws IOException;

    /**
     * UTF-16 character offset/limit; an empty block denotes EOF, short blocks need not. Providers
     * validate scope, identity and integrity before returning text.
     */
    String read(String sessionId, String id, int offset, int limit) throws IOException;

    /**
     * Complete text for host inspection, bounded to 4,194,304 UTF-16 units. Artifact.bytes is the
     * stored UTF-8 byte count; integrity is the provider responsibility.
     */
    default String readText(String sessionId, String id) throws IOException {
        var text = new StringBuilder();
        for (int offset = 0; ; ) {
            String part = read(sessionId, id, offset, 262144);
            text.append(part);
            offset += part.length();
            if (text.length() > 4194304)
                throw new HarnessException("ARTIFACT_LIMIT", "Artifact exceeds bound");
            if (part.isEmpty()) return text.toString();
        }
    }

    record Artifact(String id, long bytes, String sha256) {}
}
