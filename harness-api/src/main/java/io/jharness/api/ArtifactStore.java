package io.jharness.api;

import java.io.IOException;

public interface ArtifactStore {
    Artifact put(String sessionId, String content) throws IOException;

    String read(String sessionId, String id, int offset, int limit) throws IOException;

    record Artifact(String id, long bytes, String sha256) {}
}
