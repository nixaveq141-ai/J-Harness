package io.jharness.api;

import java.io.IOException;
import java.util.Map;

/** Bound, read-only host inspection; never establishes a model write/version observation. */
public interface ContentInspection {
    Map<String, Object> list(String path, CancellationToken cancellation) throws IOException;

    Map<String, Object> file(
            String path, int offset, int line, String version, CancellationToken cancellation)
            throws IOException;

    Map<String, Object> artifact(
            String sessionId, String id, int offset, String version, CancellationToken cancellation)
            throws IOException;
}
