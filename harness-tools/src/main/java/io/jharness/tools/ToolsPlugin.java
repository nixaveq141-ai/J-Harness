package io.jharness.tools;

import io.jharness.api.*;

public final class ToolsPlugin implements HarnessPlugin {
    private final DataSanitizer sanitizer;

    public ToolsPlugin(DataSanitizer sanitizer) {
        this.sanitizer = java.util.Objects.requireNonNull(sanitizer);
    }

    @Override
    public String id() {
        return "tools-pipeline";
    }

    @Override
    public String version() {
        return "1.1";
    }

    @Override
    public void start(PluginContext context) {
        context.register(
                ToolPipeline.class,
                "default",
                new DefaultToolPipeline(new JsonSchemaValidator(), sanitizer));
    }

    @Override
    public void stop() {}
}
