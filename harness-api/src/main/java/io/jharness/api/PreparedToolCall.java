package io.jharness.api;

/** Owner-thread preparation/settlement; only run may execute on a bounded worker. */
public interface PreparedToolCall {
    boolean parallelAllowed();

    ToolResult run();

    ToolResult finish(ToolResult result);
}
