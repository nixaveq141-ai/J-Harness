package io.jharness.api;

/** The name AND type identify an extension point. */
public record ExtensionPoint<T>(String name, Class<T> type) {
    public ExtensionPoint {
        name = Values.required(name, "name");
        java.util.Objects.requireNonNull(type);
    }
}
