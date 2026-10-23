package io.jharness.api;

import java.util.Set;

/** Select one adapter for a new task; callers discover that adapter's capabilities. */
public interface ModelRouter extends AutoCloseable {
    Set<String> routes();

    /** Borrowed router-owned client; unknown/null routes fail and must never fall back silently. */
    LlmClient select(String route);

    @Override
    void close();
}
