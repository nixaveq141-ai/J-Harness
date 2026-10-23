package io.jharness.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R1PluginRollbackTest {
    static final PluginScope SCOPE = new PluginScope("s", "a");

    static HarnessPlugin plugin(java.util.function.Consumer<PluginContext> start, Runnable stop) {
        return new HarnessPlugin() {
            public String id() {
                return "fixture";
            }

            public String version() {
                return "1";
            }

            public void start(PluginContext context) {
                start.accept(context);
            }

            public void stop() {
                stop.run();
            }
        };
    }

    static PluginContainer container(DefaultServiceRegistry registry, HarnessPlugin plugin) {
        return new PluginContainer(
                SCOPE, registry, List.of(plugin), Set.of("fixture"), Map.of(), List.of());
    }

    @Test
    void startErrorRollsBackRegistrationAndOwnedResourcesPreservingOriginal() {
        var registry = new DefaultServiceRegistry();
        var closed = new AtomicInteger();
        var original = new AssertionError("safe fixture");
        try (var container =
                container(
                        registry,
                        plugin(
                                c -> {
                                    c.register(String.class, "fixture", "value");
                                    c.own(closed::incrementAndGet);
                                    throw original;
                                },
                                () -> {}))) {
            assertSame(original, assertThrows(AssertionError.class, container::start));
            assertEquals(1, closed.get());
            assertTrue(registry.find(String.class, "fixture", SCOPE).isEmpty());
            assertEquals(PluginContainer.State.FAILED, container.state());
        }
    }

    @Test
    void failedStopAndResourceCloseRetainBothFailuresAndDoNotRetry() {
        var registry = new DefaultServiceRegistry();
        var stops = new AtomicInteger();
        var closes = new AtomicInteger();
        var container =
                container(
                        registry,
                        plugin(
                                c -> {
                                    c.register(String.class, "fixture", "value");
                                    c.own(
                                            () -> {
                                                closes.incrementAndGet();
                                                throw new IllegalArgumentException("safe close");
                                            });
                                },
                                () -> {
                                    stops.incrementAndGet();
                                    throw new IllegalStateException("safe stop");
                                }));
        container.start();
        var error = assertThrows(HarnessException.class, container::stop);
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(1, error.getSuppressed().length);
        assertEquals(PluginContainer.State.STOP_FAILED, container.state());
        assertEquals(
                List.of("stop", "owned.close"),
                container.cleanupFailures().stream()
                        .map(PluginContainer.CleanupFailure::stage)
                        .toList());
        assertTrue(registry.find(String.class, "fixture", SCOPE).isEmpty());
        container.stop();
        container.close();
        assertEquals(1, stops.get());
        assertEquals(1, closes.get());
    }

    @Test
    void brokenLifecycleObservationCannotSkipRollbackOrReplaceStartupFailure() {
        var registry = new DefaultServiceRegistry();
        var holder = new AtomicReference<PluginContainer>();
        var closed = new ArrayList<String>();
        var original = new AssertionError("safe startup");
        var container =
                container(
                        registry,
                        plugin(
                                context -> {
                                    context.register(String.class, "fixture", "value");
                                    context.own(() -> closed.add("first"));
                                    context.own(
                                            () -> {
                                                closed.add("second");
                                                throw new IllegalArgumentException("safe close");
                                            });
                                    holder.get().events().close();
                                    throw original;
                                },
                                () -> {
                                    throw new IllegalStateException("safe stop");
                                }));
        holder.set(container);
        assertSame(original, assertThrows(AssertionError.class, container::start));
        assertEquals(List.of("second", "first"), closed);
        assertTrue(registry.find(String.class, "fixture", SCOPE).isEmpty());
        assertEquals(PluginContainer.State.FAILED, container.state());
        assertEquals(2, original.getSuppressed().length);
        assertEquals("BUS_CLOSED", ((HarnessException) original.getSuppressed()[0]).code());
        assertEquals(
                List.of("stop", "owned.close"),
                container.cleanupFailures().stream()
                        .map(PluginContainer.CleanupFailure::stage)
                        .toList());
        var cleanup = (HarnessException) original.getSuppressed()[1];
        assertEquals("CLEANUP_FAILED", cleanup.code());
        assertInstanceOf(IllegalStateException.class, cleanup.getCause());
        assertEquals(2, cleanup.getSuppressed().length);
        assertEquals("BUS_CLOSED", ((HarnessException) cleanup.getSuppressed()[1]).code());
        assertEquals("BUS_CLOSED", assertThrows(HarnessException.class, container::close).code());
        assertEquals(List.of("second", "first"), closed);
    }

    @Test
    void closedContextImmediatelyClosesLateOwnedResourceAndRejectsWrongScope() {
        var registry = new DefaultServiceRegistry();
        var context = new AtomicReference<PluginContext>();
        registry.register(ToolRegistry.class, "default", new ScopedToolRegistry(), SCOPE);
        try (var container = container(registry, plugin(context::set, () -> {}))) {
            container.start();
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            context.get()
                                                    .events()
                                                    .publish(
                                                            SessionEvent.session(
                                                                    "other", "fixture", Map.of())))
                            .code());
            var tools = context.get().services().requireUnique(ToolRegistry.class, SCOPE);
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> tools.visibleTools(new PluginScope("other", "a")))
                            .code());
            container.stop();
            var closes = new AtomicInteger();
            assertEquals(
                    "CONTEXT_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> context.get().own(closes::incrementAndGet))
                            .code());
            assertEquals(1, closes.get());
            assertTrue(registry.find(String.class, "fixture", SCOPE).isEmpty());
        }
    }

    @Test
    void resourceCloseRunsOutsideContextMonitor() {
        var registry = new DefaultServiceRegistry();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
                var container =
                        container(
                                registry,
                                plugin(
                                        context -> {
                                            context.own(
                                                    () -> {
                                                        var query =
                                                                workers.submit(
                                                                        () ->
                                                                                assertThrows(
                                                                                                HarnessException
                                                                                                        .class,
                                                                                                context
                                                                                                        ::config)
                                                                                        .code());
                                                        assertEquals(
                                                                "CONTEXT_CLOSED",
                                                                query.get(2, TimeUnit.SECONDS));
                                                    });
                                        },
                                        () -> {}))) {
            container.start();
            container.stop();
            assertEquals(PluginContainer.State.STOPPED, container.state());
        }
    }

    @Test
    void toolMetadataCallbackDoesNotHoldRegistryMonitor() {
        var registry = new ScopedToolRegistry();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var registration =
                    registry.register(
                            new AgentTool() {
                                public String name() {
                                    try {
                                        assertTrue(
                                                workers.submit(
                                                                () ->
                                                                        registry.resolve(
                                                                                        "missing",
                                                                                        SCOPE)
                                                                                .isEmpty())
                                                        .get(2, TimeUnit.SECONDS));
                                    } catch (InterruptedException error) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException(error);
                                    } catch (ExecutionException | TimeoutException error) {
                                        throw new AssertionError(error);
                                    }
                                    return "fixture";
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
                                    return SideEffectLevel.READ_ONLY;
                                }

                                public ToolResult execute(
                                        ToolInvocation call, ToolContext context) {
                                    throw new AssertionError("metadata only");
                                }
                            },
                            SCOPE);
            assertTrue(registry.resolve("fixture", SCOPE).isPresent());
            registration.close();
        }
    }
}
