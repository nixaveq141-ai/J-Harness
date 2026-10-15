package io.jharness.api;

import java.util.Set;

/** Select one adapter for a new task; callers discover that adapter's capabilities. */
public interface ModelRouter extends AutoCloseable {
    Set<String> routes();

    LlmClient select(String route);

    @Override
    void close();
}
