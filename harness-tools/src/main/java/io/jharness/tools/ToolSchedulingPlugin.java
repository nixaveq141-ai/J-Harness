package io.jharness.tools;

import io.jharness.api.*;
import java.util.Objects;

/** Scoped service plus batch extension; hosts can supply a replacement scheduler. */
public final class ToolSchedulingPlugin implements HarnessPlugin {
    private final ToolScheduler scheduler;

    public ToolSchedulingPlugin(ToolSchedulingOptions options) {
        this(new BoundedReadOnlyToolScheduler(options));
    }

    public ToolSchedulingPlugin(ToolScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    @Override
    public String id() {
        return "tool-scheduling";
    }

    @Override
    public String version() {
        return "1.8";
    }

    @Override
    public void start(PluginContext context) {
        context.own(scheduler);
        context.register(ToolScheduler.class, "default", scheduler);
        context.events()
                .intercept(
                        StandardExtensionPoints.TOOL_BATCH,
                        (batch, next) ->
                                next.proceed(
                                        (calls, pipelineContext, pipeline) -> {
                                            if (!pipelineContext.scope().equals(context.scope()))
                                                throw new HarnessException(
                                                        "SCOPE_DENIED",
                                                        "Scheduler belongs to another scope");
                                            return context.services()
                                                    .requireUnique(
                                                            ToolScheduler.class, context.scope())
                                                    .execute(calls, pipelineContext, pipeline);
                                        }),
                        0);
    }

    @Override
    public void stop() {}
}
