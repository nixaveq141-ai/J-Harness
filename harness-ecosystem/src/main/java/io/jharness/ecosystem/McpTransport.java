package io.jharness.ecosystem;

import io.jharness.api.CancellationToken;
import java.util.Map;

/** A trusted host-selected MCP connection; implementations must bound IO and never retry calls. */
public interface McpTransport extends AutoCloseable {
    String kind();

    Map<String, Object> exchange(Map<String, Object> message, CancellationToken cancellation);

    @Override
    void close();
}
