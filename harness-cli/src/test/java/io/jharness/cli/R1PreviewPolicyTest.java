package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.events.DefaultEventBus;
import io.jharness.plugin.ScopedToolRegistry;
import io.jharness.session.InMemorySessionRepository;
import io.jharness.session.MemoryCheckpointPolicy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class R1PreviewPolicyTest {
    @Test
    void nullablePolicyCannotEnterPreviewEvenWhenProviderIsInstalled() {
        var previews = new AtomicInteger();
        var scope = new PluginScope("s", "a");
        var tools = new ScopedToolRegistry();
        AgentTool tool =
                new AgentTool() {
                    public String name() {
                        return "write_file";
                    }

                    public String description() {
                        return "fixture";
                    }

                    public Map<String, Object> inputSchema() {
                        return Map.of("type", "object");
                    }

                    public Map<String, Object> outputSchema() {
                        return Map.of("type", "object");
                    }

                    public SideEffectLevel sideEffectLevel() {
                        return SideEffectLevel.WORKSPACE_WRITE;
                    }

                    public ToolResult execute(ToolInvocation call, ToolContext context) {
                        throw new AssertionError("must not execute");
                    }
                };
        tools.register(tool, scope);
        var repo = new InMemorySessionRepository();
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
                var events = new DefaultEventBus()) {
            var pipeline =
                    new DryRunPipeline(
                            (call, context) -> {
                                throw new AssertionError("must not delegate");
                            },
                            (call, cancel) -> {
                                previews.incrementAndGet();
                                return Map.of("executed", false);
                            },
                            new SecretRedactor());
            var context =
                    new ToolPipelineContext(
                            scope,
                            "turn",
                            "step",
                            tools,
                            writer,
                            events,
                            (call, ctx) -> null,
                            ApprovalService.rejectAll(),
                            new MemoryCheckpointPolicy(),
                            CancellationToken.NONE);
            var result =
                    pipeline.execute(
                            new ToolInvocation(
                                    "c", "write_file", Map.of(), SideEffectLevel.WORKSPACE_WRITE),
                            context);
            assertEquals(0, previews.get());
            assertEquals(ToolStatus.DENIED, result.status());
            assertEquals("PERMISSION_DENIED", result.value().get("reason"));
        }
    }
}
