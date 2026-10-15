package io.jharness.llm;

import io.jharness.api.*;

public final class LlmPlugin implements HarnessPlugin {
    private final LlmClient client;

    public LlmPlugin(LlmClient client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    @Override
    public String id() {
        return "llm-provider";
    }

    @Override
    public String version() {
        return "1.1";
    }

    @Override
    public void start(PluginContext context) {
        context.own(client);
        context.register(LlmClient.class, "default", client);
        if (client instanceof ModelRouter router)
            context.register(ModelRouter.class, "default", router);
    }

    @Override
    public void stop() {}
}
