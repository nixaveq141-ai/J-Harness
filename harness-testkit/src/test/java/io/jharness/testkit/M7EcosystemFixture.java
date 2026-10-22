package io.jharness.testkit;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.events.DefaultEventBus;
import io.jharness.plugin.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.tools.*;
import java.nio.file.*;
import java.util.*;

final class M7EcosystemFixture implements AutoCloseable {
    static final PluginScope SCOPE = new PluginScope("s", "default");
    final Path root;
    final JsonlSessionRepository repository;
    final SessionWriter writer;
    final ToolRegistry tools;
    final DefaultEventBus events = new DefaultEventBus();
    final PluginContainer container;
    final SecretRedactor clean = new SecretRedactor(List.of("mcp-secret-token"));
    final DefaultToolPipeline pipeline = new DefaultToolPipeline(new JsonSchemaValidator(), clean);

    M7EcosystemFixture(Path base, HarnessPlugin extension) throws Exception {
        this(base, extension, new ScopedToolRegistry());
    }

    M7EcosystemFixture(Path base, HarnessPlugin extension, ToolRegistry tools) throws Exception {
        this.tools = tools;
        root = base.resolve("workspace");
        Files.createDirectories(root);
        repository = new JsonlSessionRepository(base.resolve("sessions"));
        writer =
                new SanitizingSessionWriter(
                        repository.openWriter("s", WriteOwnership.EXCLUSIVE), clean);
        var registry =
                new HarnessPlugin() {
                    @Override
                    public String id() {
                        return "fixture-registry";
                    }

                    @Override
                    public String version() {
                        return "1.7";
                    }

                    @Override
                    public void start(PluginContext context) {
                        context.register(ToolRegistry.class, "default", tools);
                        context.register(DataSanitizer.class, "default", clean);
                    }

                    @Override
                    public void stop() {}
                };
        var plugins =
                List.of(
                        registry,
                        new EnvironmentPlugin(
                                new LocalExecutionEnvironment("local", root, List.of()),
                                new LocalArtifactStore(base.resolve("artifacts"), clean)),
                        extension);
        container =
                new PluginContainer(
                        SCOPE,
                        new DefaultServiceRegistry(),
                        plugins,
                        plugins.stream()
                                .map(HarnessPlugin::id)
                                .collect(java.util.stream.Collectors.toSet()),
                        Map.of(),
                        List.of());
    }

    void start() {
        container.start();
    }

    ToolResult execute(String tool, Map<String, Object> arguments, ApprovalService approvals) {
        return execute(tool, arguments, approvals, CancellationToken.NONE, SCOPE);
    }

    ToolResult execute(
            String tool,
            Map<String, Object> arguments,
            ApprovalService approvals,
            CancellationToken cancellation,
            PluginScope scope) {
        return pipeline.execute(
                new ToolInvocation(
                        UUID.randomUUID().toString(), tool, arguments, SideEffectLevel.READ_ONLY),
                new ToolPipelineContext(
                        scope,
                        "turn",
                        "step",
                        tools,
                        writer,
                        events,
                        new DefaultPolicyEngine(),
                        approvals,
                        new DefaultCheckpointPolicy(),
                        cancellation));
    }

    static ApprovalService approve() {
        return (call, context) ->
                new ApprovalService.ApprovalDecision(true, "fixture approval", "operator");
    }

    List<SessionEvent> history() {
        return repository.openReader("s").events();
    }

    @Override
    public void close() {
        try {
            container.close();
        } finally {
            writer.close();
            events.close();
        }
    }
}
