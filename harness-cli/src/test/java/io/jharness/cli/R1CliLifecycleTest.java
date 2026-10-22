package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R1CliLifecycleTest {
    @Test
    void policyPluginRevokesApprovalRegistrationBeforeClosingAndStillRevokesPolicyOnFailure() {
        var scope = new PluginScope("r1-policy", "test");
        var registry = new io.jharness.plugin.DefaultServiceRegistry();
        var closes = new AtomicInteger();
        class Approvals implements ApprovalService, AutoCloseable {
            public ApprovalDecision decide(ToolInvocation call, ToolContext context) {
                return new ApprovalDecision(false, "fixture", "fixture");
            }

            public void close() {
                closes.incrementAndGet();
                assertTrue(registry.find(ApprovalService.class, "default", scope).isEmpty());
                assertTrue(registry.find(PolicyEngine.class, "default", scope).isPresent());
                throw new IllegalStateException("synthetic close failure");
            }
        }
        var plugin =
                new io.jharness.policy.PolicyPlugin(
                        (call, context) -> PolicyEngine.Decision.DENY, new Approvals());
        var container =
                new io.jharness.plugin.PluginContainer(
                        scope, registry, List.of(plugin), Set.of("policy"), Map.of(), List.of());
        container.start();
        assertThrows(HarnessException.class, container::stop);
        container.close();
        assertEquals(1, closes.get());
        assertTrue(registry.find(PolicyEngine.class, "default", scope).isEmpty());
        assertTrue(registry.find(ApprovalService.class, "default", scope).isEmpty());
    }

    @Test
    void shutdownStopsTerminalEvenWhenCancellationOrTaskFails() {
        for (boolean cancelFails : List.of(false, true)) {
            for (boolean cleanupFails : List.of(false, true)) {
                var stopped = new AtomicInteger();
                var error = new IllegalStateException("synthetic failure");
                var cleanup = new IllegalArgumentException("synthetic terminal failure");
                TerminalSession terminal =
                        () -> {
                            stopped.incrementAndGet();
                            if (cleanupFails) throw cleanup;
                        };
                var task = CompletableFuture.failedFuture(error);
                var actual =
                        assertThrows(
                                Exception.class,
                                () ->
                                        CliApplication.stopForShutdown(
                                                () -> {
                                                    if (cancelFails) throw error;
                                                },
                                                task,
                                                terminal));
                assertEquals(1, stopped.get());
                if (cancelFails) assertSame(error, actual);
                else assertSame(error, actual.getCause());
                assertArrayEquals(
                        cleanupFails ? new Throwable[] {cleanup} : new Throwable[0],
                        actual.getSuppressed());
            }
        }
        var cleanup = new IllegalArgumentException("synthetic terminal failure");
        assertSame(
                cleanup,
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                CliApplication.stopForShutdown(
                                        () -> {},
                                        CompletableFuture.completedFuture(null),
                                        () -> {
                                            throw cleanup;
                                        })));
    }

    @Test
    void interactionFailureCancelsRunAndPreservesPrimaryAndCleanupFailures() {
        var primary = new IllegalStateException("interaction");
        var cleanup = new IllegalArgumentException("cancel");
        var cancelled = new AtomicInteger();
        var actual =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                CliApplication.awaitInteraction(
                                        new CompletableFuture<>(),
                                        () -> {
                                            throw primary;
                                        },
                                        () -> {
                                            cancelled.incrementAndGet();
                                            throw cleanup;
                                        }));
        assertSame(primary, actual);
        assertEquals(1, cancelled.get());
        assertArrayEquals(new Throwable[] {cleanup}, actual.getSuppressed());
    }

    @Test
    void parameterizedModelDelegatesSingleAttemptCapabilityAndStreamingWithoutRetry() {
        var calls = new ArrayList<String>();
        var seen = new ArrayList<ChatRequest>();
        LlmClient delegate =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return new ModelCapabilities(true, true, false, false, 1000);
                    }

                    public boolean supportsSingleAttempt() {
                        return false;
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        calls.add("prepare");
                        seen.add(request);
                        return new PreparedRequest(request);
                    }

                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        throw new AssertionError("no retry path");
                    }

                    public ChatResponse completeSingleAttempt(
                            ChatRequest request, CancellationToken token) {
                        calls.add("single");
                        seen.add(request);
                        return new ChatResponse("ok", List.of(), Map.of());
                    }

                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        calls.add("stream");
                        seen.add(request);
                        return null;
                    }

                    public void close() {
                        calls.add("close");
                    }
                };
        var model =
                new EcosystemConfig.ParameterizedModel(
                        delegate,
                        Map.of("stream", true, "stream_options", Map.of(), "temperature", 0));
        assertFalse(model.supportsSingleAttempt());
        var request = new ChatRequest(List.of(), List.of(), Map.of("ignored", true));
        model.prepare(request);
        model.completeSingleAttempt(request, CancellationToken.NONE);
        model.stream(request, CancellationToken.NONE);
        model.close();
        assertEquals(List.of("prepare", "single", "stream", "close"), calls);
        for (var sent : seen)
            assertEquals(Map.of("stream", false, "temperature", 0), sent.parameters());
    }
}
