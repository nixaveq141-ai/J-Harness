package io.jharness.api;

public record ToolContext(PluginScope scope, CancellationToken cancellation) {
    public ToolContext {
        java.util.Objects.requireNonNull(scope, "scope");
        java.util.Objects.requireNonNull(cancellation, "cancellation");
    }
}
