package io.jharness.agent;

import io.jharness.api.*;
import java.util.*;

/**
 * Revocable scoped service, looked up on every invocation including retained extension references.
 */
public final class ModelResiliencePlugin implements HarnessPlugin {
    private ModelExecution execution;
    private ModelResilienceOptions options;

    public ModelResiliencePlugin(ModelResilienceOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    public ModelResiliencePlugin(ModelExecution execution) {
        this.execution = Objects.requireNonNull(execution);
    }

    public String id() {
        return "model-resilience";
    }

    public String version() {
        return "1.10";
    }

    public void start(PluginContext context) {
        if (options != null)
            execution =
                    new BoundedModelExecution(
                            options,
                            (type, payload) -> {
                                SessionWriter writer =
                                        context.services()
                                                .requireUnique(
                                                        SessionWriter.class, context.scope());
                                EventSupport.append(
                                        writer,
                                        context.events(),
                                        context.scope(),
                                        type,
                                        EventLevel.SESSION,
                                        null,
                                        null,
                                        null,
                                        payload);
                            });
        if (execution instanceof AutoCloseable resource) context.own(resource);
        context.register(ModelExecution.class, "default", execution);
        context.events()
                .intercept(
                        StandardExtensionPoints.MODEL_EXECUTION,
                        (original, next) ->
                                next.proceed(
                                        (attempt, cancellation) ->
                                                context.services()
                                                        .requireUnique(
                                                                ModelExecution.class,
                                                                context.scope())
                                                        .execute(attempt, cancellation)),
                        0);
    }

    public void stop() {}
}
