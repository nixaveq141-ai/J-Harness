package io.jharness.session;

import io.jharness.api.*;

public final class JsonlSessionPlugin implements HarnessPlugin {
    private final SessionRepository repository;
    private final ArtifactStore artifacts;

    public JsonlSessionPlugin(SessionRepository repository, ArtifactStore artifacts) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.artifacts = java.util.Objects.requireNonNull(artifacts);
    }

    @Override
    public String id() {
        return "session-jsonl";
    }

    @Override
    public String version() {
        return "1.2";
    }

    @Override
    public void start(PluginContext context) {
        context.register(SessionRepository.class, "default", repository);
        context.register(ArtifactStore.class, "default", artifacts);
        context.register(CheckpointPolicy.class, "default", new DefaultCheckpointPolicy());
    }

    @Override
    public void stop() {}
}
