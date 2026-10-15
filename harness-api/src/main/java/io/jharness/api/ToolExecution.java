package io.jharness.api;

/** Wrappable execution capability; policy and checkpoint checks belong to the M1 pipeline. */
@FunctionalInterface
public interface ToolExecution {
    ToolResult execute(ToolInvocation invocation, CancellationToken cancellation) throws Exception;
}
