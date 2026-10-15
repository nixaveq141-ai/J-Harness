package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.process.ProcessToolsPlugin;
import io.jharness.process.TerminalToolsPlugin;
import io.jharness.workspace.WorkspaceToolsPlugin;
import java.util.*;

/** One environment contributes all paths/processes/terminals to the same scoped tool pipeline. */
public final class EnvironmentPlugin implements HarnessPlugin {
    private final ExecutionEnvironment environment;
    private final ArtifactStore artifacts;

    public EnvironmentPlugin(ExecutionEnvironment environment, ArtifactStore artifacts) {
        this.environment = Objects.requireNonNull(environment);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    @Override
    public String id() {
        return "execution-environment";
    }

    @Override
    public String version() {
        return "1.7";
    }

    @Override
    public void start(PluginContext context) {
        context.own(environment);
        new WorkspaceToolsPlugin(environment, artifacts).start(context);
        context.register(ProcessRunner.class, "default", environment.processes());
        var registry = context.services().requireUnique(ToolRegistry.class, context.scope());
        context.own(
                registry.register(
                        new ProcessToolsPlugin.CommandTool(
                                environment.processes(), context.scope(), artifacts),
                        context.scope()));
        if ("unsupported".equals(environment.descriptor().get("terminal")))
            context.register(TerminalSession.class, "default", environment.terminal());
        else new TerminalToolsPlugin(environment.terminal()).start(context);
    }

    @Override
    public void stop() {}
}
