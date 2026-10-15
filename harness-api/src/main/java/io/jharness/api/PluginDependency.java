package io.jharness.api;

/** Exact version constraint in M0; semantic version ranges are not silently accepted. */
public record PluginDependency(String id, String version) {
    public PluginDependency {
        id = Values.required(id, "id");
        version = Values.required(version, "version");
    }
}
