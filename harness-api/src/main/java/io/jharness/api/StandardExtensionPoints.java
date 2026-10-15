package io.jharness.api;

/** Stable seams for the M1 pipeline; adding behavior does not require patching its loop. */
public final class StandardExtensionPoints {
    public static final ExtensionPoint<ChatRequest> BEFORE_MODEL =
            new ExtensionPoint<>("model.before-request", ChatRequest.class);
    public static final ExtensionPoint<ChatResponse> AFTER_MODEL =
            new ExtensionPoint<>("model.after-response", ChatResponse.class);
    public static final ExtensionPoint<ToolInvocation> BEFORE_TOOL =
            new ExtensionPoint<>("tool.before-call", ToolInvocation.class);
    public static final ExtensionPoint<ToolExecution> TOOL_EXECUTION =
            new ExtensionPoint<>("tool.execution", ToolExecution.class);
    public static final ExtensionPoint<ToolResult> AFTER_TOOL =
            new ExtensionPoint<>("tool.after-result", ToolResult.class);

    private StandardExtensionPoints() {}
}
