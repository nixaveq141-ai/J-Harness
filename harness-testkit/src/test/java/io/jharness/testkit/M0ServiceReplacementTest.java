package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * M0 metadata/service-replacement contract: both fake loops compose a projection and call a model,
 * but deliberately never dispatch a tool. Body execution is covered by M1 pipeline tests.
 */
class M0ServiceReplacementTest {
    private static final PluginScope SCOPE = new PluginScope("session", "agent");

    @Test
    void baselinePluginPassesHarnessContract() {
        verifyContract(false);
    }

    @Test
    void replacementLoopAndToolRegistryPassTheSameHarnessContract() {
        verifyContract(true);
    }

    private static void verifyContract(boolean replacement) {
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        var otherScope = new PluginScope("other-session", "agent");
        var otherModel = new FakeLlm(List.of());
        services.register(LlmClient.class, "default", otherModel, otherScope);
        FakeLlm llm = new FakeLlm(List.of(new ChatResponse("done", List.of(), Map.of())));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger toolExecutions = new AtomicInteger();
        ToolRegistry tools = replacement ? new AlternateRegistry() : new ScopedToolRegistry();
        AgentTool tool = tool(toolExecutions);
        AgentLoopFactory factory =
                replacement
                        ? dependencies ->
                                cancellation -> {
                                    cancellation.throwIfCancelled();
                                    calls.addAndGet(10);
                                    dependencies
                                            .llm()
                                            .complete(
                                                    dependencies
                                                            .promptComposer()
                                                            .compose(dependencies.projection()),
                                                    cancellation);
                                }
                        : dependencies ->
                                cancellation -> {
                                    cancellation.throwIfCancelled();
                                    calls.incrementAndGet();
                                    dependencies
                                            .llm()
                                            .complete(
                                                    dependencies
                                                            .promptComposer()
                                                            .compose(dependencies.projection()),
                                                    cancellation);
                                };
        HarnessPlugin testPlugin =
                new HarnessPlugin() {
                    @Override
                    public String id() {
                        return "test-services";
                    }

                    @Override
                    public String version() {
                        return "1";
                    }

                    @Override
                    public void start(PluginContext context) {
                        context.register(AgentLoopFactory.class, "default", factory);
                        context.register(ToolRegistry.class, "default", tools);
                        context.register(LlmClient.class, "default", llm);
                        context.register(
                                SessionRepository.class,
                                "default",
                                new InMemorySessionRepository());
                        context.own(tools.register(tool, SCOPE));
                    }

                    @Override
                    public void stop() {}
                };
        try (PluginContainer container =
                        new PluginContainer(
                                SCOPE,
                                services,
                                List.of(testPlugin),
                                Set.of(testPlugin.id()),
                                Map.of(),
                                List.of(
                                        ServiceRequirement.unique(AgentLoopFactory.class),
                                        ServiceRequirement.unique(ToolRegistry.class),
                                        ServiceRequirement.unique(LlmClient.class),
                                        ServiceRequirement.unique(SessionRepository.class)));
                EventRecorder events = new EventRecorder(container.events())) {
            container.start();
            runHarness(services, container.events());
            assertEquals(replacement ? 10 : 1, calls.get());
            assertEquals(1, llm.requests().size());
            assertSame(otherModel, services.require(LlmClient.class, "default", otherScope));
            assertEquals("hello", llm.requests().getFirst().messages().getFirst().get("content"));
            assertEquals(1, tools.visibleTools(SCOPE).size());
            assertEquals(
                    0, toolExecutions.get()); // M0 contract does not execute real side effects.
            container.events().awaitNotifications(Duration.ofSeconds(2));
            assertEquals(List.of("plugin.loaded", "plugin.started"), events.types());
            container.stop();
            assertTrue(tools.resolve("echo", SCOPE).isEmpty());
            assertEquals(
                    "UNKNOWN_TOOL",
                    assertThrows(HarnessException.class, () -> tools.require("echo", SCOPE))
                            .code());
            assertTrue(services.find(AgentLoopFactory.class, "default", SCOPE).isEmpty());
            assertTrue(services.find(ToolRegistry.class, "default", SCOPE).isEmpty());
            assertTrue(services.find(LlmClient.class, "default", SCOPE).isEmpty());
            assertTrue(services.find(SessionRepository.class, "default", SCOPE).isEmpty());
            assertSame(otherModel, services.require(LlmClient.class, "default", otherScope));
        }
    }

    /** Consumer depends exclusively on public contracts for either provider combination. */
    private static void runHarness(ServiceRegistry services, EventBus events) {
        SessionRepository sessions = services.requireUnique(SessionRepository.class, SCOPE);
        try (SessionWriter writer =
                sessions.openWriter(SCOPE.sessionId(), WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session(SCOPE.sessionId(), "session.created", Map.of()));
            writer.append(
                    SessionEvent.session(
                            SCOPE.sessionId(),
                            "user.message",
                            Map.of("content", "hello", "role", "user")));
            SessionProjection projection =
                    new EventSessionProjection(sessions.openReader(SCOPE.sessionId()).events());
            AgentLoopDependencies dependencies =
                    new AgentLoopDependencies(
                            services.requireUnique(LlmClient.class, SCOPE),
                            services.requireUnique(ToolRegistry.class, SCOPE),
                            writer,
                            projection,
                            observed -> new ChatRequest(observed.messages(), List.of(), Map.of()),
                            events,
                            (call, context) -> PolicyEngine.Decision.DENY,
                            CancellationToken.NONE,
                            new Budget(1, Duration.ofSeconds(5)));
            services.requireUnique(AgentLoopFactory.class, SCOPE)
                    .create(dependencies)
                    .run(CancellationToken.NONE);
        }
    }

    private static AgentTool tool(AtomicInteger executions) {
        return new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "test-only echo";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object");
            }

            @Override
            public Map<String, Object> outputSchema() {
                return Map.of("type", "object");
            }

            @Override
            public SideEffectLevel sideEffectLevel() {
                return SideEffectLevel.READ_ONLY;
            }

            @Override
            public ToolResult execute(ToolInvocation args, ToolContext context) {
                executions.incrementAndGet();
                return new ToolResult(
                        args.callId(),
                        ToolStatus.SUCCESS,
                        Map.of(),
                        "echo",
                        Map.of(),
                        Optional.empty());
            }
        };
    }

    private static final class AlternateRegistry implements ToolRegistry {
        private final Map<PluginScope, Map<String, AgentTool>> storage = new HashMap<>();

        @Override
        public Registration register(AgentTool tool, PluginScope scope) {
            Map<String, AgentTool> scoped =
                    storage.computeIfAbsent(scope, ignored -> new LinkedHashMap<>());
            if (scoped.putIfAbsent(tool.name(), tool) != null)
                throw new HarnessException("DUPLICATE_TOOL", "Duplicate test tool");
            return () -> scoped.remove(tool.name(), tool);
        }

        @Override
        public Optional<AgentTool> resolve(String name, PluginScope scope) {
            return Optional.ofNullable(storage.getOrDefault(scope, Map.of()).get(name));
        }

        @Override
        public List<AgentTool> visibleTools(PluginScope scope) {
            return List.copyOf(storage.getOrDefault(scope, Map.of()).values());
        }
    }
}
