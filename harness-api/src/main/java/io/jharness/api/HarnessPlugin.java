package io.jharness.api;

import java.util.List;

public interface HarnessPlugin {
    String id();

    String version();

    default String apiVersion() {
        return ApiVersion.CURRENT;
    }

    default List<PluginDependency> dependencies() {
        return List.of();
    }

    void start(PluginContext context);

    void stop();
}
