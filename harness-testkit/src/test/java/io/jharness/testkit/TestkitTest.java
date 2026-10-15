package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class TestkitTest {
    @Test
    void fakeLlmCapturesRequestsHonorsCancellationAndFailsWhenExhausted() {
        ChatResponse response = new ChatResponse("ok", List.of(), Map.of("tokens", 1));
        FakeLlm llm = new FakeLlm(List.of(response));
        ChatRequest request =
                new ChatRequest(
                        List.of(Map.of("role", "user", "content", "hi")), List.of(), Map.of());
        assertFalse(llm.capabilities().streaming());
        assertTrue(llm.capabilities().tools());
        assertEquals(request, llm.prepare(request).request());
        assertEquals(
                "CANCELLED",
                assertThrows(HarnessException.class, () -> llm.complete(request, () -> true))
                        .code());
        assertTrue(llm.requests().isEmpty());
        assertEquals(response, llm.complete(request, CancellationToken.NONE));
        assertEquals(List.of(request), llm.requests());
        assertEquals(
                "FAKE_EXHAUSTED",
                assertThrows(
                                HarnessException.class,
                                () -> llm.complete(request, CancellationToken.NONE))
                        .code());
        assertEquals(
                "UNSUPPORTED",
                assertThrows(
                                HarnessException.class,
                                () -> llm.stream(request, CancellationToken.NONE))
                        .code());
    }

    @Test
    @SuppressWarnings(
            "try") // Deliberately verify early subscription revocation inside its lifetime.
    void eventRecorderCapturesOrderAndPayloadAndCanBeRemoved() {
        try (DefaultEventBus bus = new DefaultEventBus();
                EventRecorder recorder = new EventRecorder(bus)) {
            SessionEvent one = SessionEvent.session("s", "one", Map.of("value", 1));
            SessionEvent two = SessionEvent.session("s", "two", Map.of("value", 2));
            bus.publish(one);
            bus.publish(two);
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals(List.of(one, two), recorder.events());
            assertEquals(List.of("one", "two"), recorder.types());
            recorder.close();
            bus.publish(SessionEvent.session("s", "three", Map.of()));
            bus.awaitNotifications(Duration.ofSeconds(2));
            assertEquals(2, recorder.events().size());
        }
    }

    @Test
    void checkpointRecorderAndFailureInjectorExposeBoundaries() {
        CheckpointRecorder recorder = new CheckpointRecorder(new DefaultCheckpointPolicy());
        SessionState state = new SessionState("s", TaskStatus.RUNNING);
        assertEquals(CheckpointDecision.DURABLE_FLUSH, recorder.beforeEachModelRequest(state));
        assertEquals(
                CheckpointDecision.DURABLE_FLUSH,
                recorder.beforeTopLevelTool(
                        new ToolInvocation(
                                "c", "write", Map.of(), SideEffectLevel.WORKSPACE_WRITE)));
        assertEquals(CheckpointDecision.DURABLE_FLUSH, recorder.atStepBoundary(state));
        assertEquals(List.of("model:s", "tool:c", "step:s"), recorder.boundaries());
        try (SessionWriter writer = new FailingSessionWriter()) {
            assertEquals(
                    "CHECKPOINT_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            writer.append(
                                                    SessionEvent.session(
                                                            "s", "session.created", Map.of())))
                            .code());
            assertEquals(
                    "CHECKPOINT_FAILED",
                    assertThrows(HarnessException.class, writer::flush).code());
        }
    }

    @Test
    void temporaryProjectHasMavenFilesAndCleansOnlyItsOwnedDirectory() throws Exception {
        Path root;
        try (TemporaryProject project = new TemporaryProject()) {
            root = project.root();
            assertTrue(root.getFileName().toString().contains("测试 project"));
            assertTrue(Files.readString(root.resolve("pom.xml")).contains("calculator"));
            assertTrue(
                    Files.readString(root.resolve("src/main/java/fixture/Calculator.java"))
                            .contains("return a + b"));
        }
        assertFalse(Files.exists(root));
    }

    @Test
    void toolRegistryIsolationDuplicatesAndOldHandleCannotRemoveNewRegistration() {
        ScopedToolRegistry registry = new ScopedToolRegistry();
        PluginScope parent = new PluginScope("s", "parent");
        PluginScope child = new PluginScope("s", "child");
        AgentTool tool =
                new AgentTool() {
                    @Override
                    public String name() {
                        return "read";
                    }

                    @Override
                    public String description() {
                        return "test";
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
                        throw new UnsupportedOperationException();
                    }
                };
        Registration old = registry.register(tool, parent);
        assertTrue(registry.resolve("read", child).isEmpty());
        assertTrue(registry.resolve("read", new PluginScope("other", "parent")).isEmpty());
        assertEquals(
                "DUPLICATE_TOOL",
                assertThrows(HarnessException.class, () -> registry.register(tool, parent)).code());
        old.close();
        Registration replacement = registry.register(tool, parent);
        old.close();
        assertEquals(tool, registry.require("read", parent));
        replacement.close();
        assertTrue(registry.visibleTools(parent).isEmpty());
    }
}
