package io.jharness.api;

/** One owned execution world for files, processes and supported terminal capabilities. */
public interface ExecutionEnvironment extends AutoCloseable {
    String id();

    FileSystemView files();

    ProcessRunner processes();

    TerminalSession terminal();

    default java.util.Map<String, Object> descriptor() {
        return java.util.Map.of("id", id(), "isolation", "unspecified");
    }

    @Override
    default void close() {}
}
