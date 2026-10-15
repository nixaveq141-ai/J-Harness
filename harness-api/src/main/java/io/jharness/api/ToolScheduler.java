package io.jharness.api;

/** Scoped replaceable batch scheduler. Implementations must preserve pipeline and call identity. */
public interface ToolScheduler extends ToolBatchExecution, AutoCloseable {
    @Override
    default void close() {}
}
