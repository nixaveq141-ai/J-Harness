package io.jharness.api;

import java.util.List;
import java.util.Map;

/**
 * Persistent terminal/background-task service. Defaults explicitly decline unsupported operations.
 */
public interface TerminalSession extends AutoCloseable {
    default Map<String, Object> start(
            CommandRequest request, boolean pty, CancellationToken cancellation) {
        throw unsupported();
    }

    default Map<String, Object> status(String id) {
        throw unsupported();
    }

    default Map<String, Object> log(String id, long offset, int limit) {
        throw unsupported();
    }

    default Map<String, Object> write(String id, String text, CancellationToken cancellation) {
        throw unsupported();
    }

    default Map<String, Object> resize(String id, int columns, int rows) {
        throw unsupported();
    }

    default Map<String, Object> stop(String id) {
        throw unsupported();
    }

    private static HarnessException unsupported() {
        return new HarnessException(
                "UNSUPPORTED_TERMINAL", "Terminal provider does not implement this capability");
    }

    default List<Map<String, Object>> states() {
        return List.of();
    }

    default void stopAll() {
        close();
    }

    @Override
    void close();
}
