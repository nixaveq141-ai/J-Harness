package io.jharness.testkit;

import io.jharness.agent.AgentPlugin;
import io.jharness.api.*;
import io.jharness.cli.CliApplication;
import java.nio.charset.StandardCharsets;

/** Real process crash between confirmed file commit and tool.completed persistence. */
public final class M4CliCrashProbe {
    private M4CliCrashProbe() {}

    public static void main(String[] args) {
        var app =
                new CliApplication(
                        System.getenv(),
                        services ->
                                new HarnessPlugin() {
                                    private final AgentPlugin delegate = new AgentPlugin(services);

                                    @Override
                                    public String id() {
                                        return delegate.id();
                                    }

                                    @Override
                                    public String version() {
                                        return "crash-test";
                                    }

                                    @Override
                                    public void start(PluginContext context) {
                                        delegate.start(context);
                                        context.own(
                                                context.events()
                                                        .intercept(
                                                                StandardExtensionPoints.AFTER_TOOL,
                                                                (result, next) -> {
                                                                    if (result.callId()
                                                                                    .equals("crash")
                                                                            && result.status()
                                                                                    == ToolStatus
                                                                                            .SUCCESS)
                                                                        Runtime.getRuntime()
                                                                                .halt(23);
                                                                    return next.proceed(result);
                                                                },
                                                                0));
                                    }

                                    @Override
                                    public void stop() {
                                        delegate.stop();
                                    }
                                });
        System.exit(
                app.execute(
                        args,
                        System.in,
                        new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8),
                        System.err));
    }
}
