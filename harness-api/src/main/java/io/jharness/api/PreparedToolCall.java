package io.jharness.api;

/** Owner-thread preparation/settlement; only run may execute on a bounded worker. */
public interface PreparedToolCall {
    boolean parallelAllowed();

    ToolResult run();

    ToolResult finish(ToolResult result);

    /** Owner-thread abandonment: settle accepted preparation without entering its body. */
    default ToolResult abandon(String callId, String reason) {
        return finish(ToolResults.failure(callId, ToolStatus.CANCELLED, reason));
    }
}
