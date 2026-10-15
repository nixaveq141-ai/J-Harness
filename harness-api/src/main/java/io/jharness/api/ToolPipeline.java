package io.jharness.api;

@FunctionalInterface
public interface ToolPipeline {
    ToolResult execute(ToolInvocation invocation, ToolPipelineContext context);
}
