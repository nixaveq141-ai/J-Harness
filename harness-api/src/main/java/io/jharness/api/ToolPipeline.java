package io.jharness.api;

@FunctionalInterface
public interface ToolPipeline {
    ToolResult execute(ToolInvocation invocation, ToolPipelineContext context);

    /** Legacy implementations remain serial. barrier must finish earlier reads before ASK/write. */
    default java.util.Optional<PreparedToolCall> prepare(
            ToolInvocation invocation, ToolPipelineContext context, Runnable barrier) {
        return java.util.Optional.empty();
    }
}
