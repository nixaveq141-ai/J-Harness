package io.jharness.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PluginContainerTest {
    @Test
    void closingAnUnusedContainerIsIdempotentAndItCannotRestart() {
        PluginContainer container =
                container(new DefaultServiceRegistry(), List.of(), Set.of(), List.of());
        container.close();
        container.close();
        assertEquals(PluginContainer.State.STOPPED, container.state());
        assertEquals(
                "CONTAINER_STATE", assertThrows(HarnessException.class, container::start).code());
    }

    @Test
    void startupFailureNamesTheFailedPluginAndKeepsCleanupFailures() {
        List<SessionEvent> facts = new java.util.concurrent.CopyOnWriteArrayList<>();
        HarnessPlugin bad =
                plugin(
                        "bad",
                        List.of(),
                        c -> {
                            c.own(
                                    () -> {
                                        throw new IllegalStateException("resource failure");
                                    });
                            throw new HarnessException("INJECTED", "start failed");
                        },
                        () -> {
                            throw new IllegalStateException("stop failed");
                        });
        try (PluginContainer container =
                container(new DefaultServiceRegistry(), List.of(bad), Set.of("bad"), List.of())) {
            container.events().notify("*", facts::add);
            HarnessException failure = assertThrows(HarnessException.class, container::start);
            assertEquals("INJECTED", failure.code());
            assertEquals(1, failure.getSuppressed().length);
            container.events().awaitNotifications(Duration.ofSeconds(2));
            assertEquals(
                    "bad",
                    facts.stream()
                            .filter(e -> e.type().equals("plugin.failed"))
                            .findFirst()
                            .orElseThrow()
                            .payload()
                            .get("pluginId"));
            assertEquals(
                    List.of("plugin.loaded", "plugin.failed", "plugin.stopped"),
                    facts.stream().map(SessionEvent::type).toList());
        }
    }

    private static final PluginScope SCOPE = new PluginScope("s", "agent");

    @Test
    void dependenciesStartBeforeDependentsAndStopInReverse() {
        List<String> calls = new ArrayList<>();
        HarnessPlugin dependent =
                plugin(
                        "dependent",
                        List.of(new PluginDependency("base", "1")),
                        c -> {
                            assertEquals(
                                    "base", c.services().require(String.class, "service", SCOPE));
                            calls.add("start-dependent");
                        },
                        () -> calls.add("stop-dependent"));
        HarnessPlugin base =
                plugin(
                        "base",
                        List.of(),
                        c -> {
                            c.register(String.class, "service", "base");
                            calls.add("start-base");
                        },
                        () -> calls.add("stop-base"));
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        try (PluginContainer container =
                container(
                        registry,
                        List.of(dependent, base),
                        Set.of("dependent", "base"),
                        List.of())) {
            container.start();
            assertEquals(PluginContainer.State.RUNNING, container.state());
            container.stop();
            assertEquals(
                    List.of("start-base", "start-dependent", "stop-dependent", "stop-base"), calls);
            assertTrue(registry.find(String.class, "service", SCOPE).isEmpty());
            container.stop();
        }
    }

    @Test
    void missingDisabledCircularAndIncompatibleDependenciesFailBeforeAnyStart() {
        AtomicInteger starts = new AtomicInteger();
        HarnessPlugin a =
                plugin(
                        "a",
                        List.of(new PluginDependency("b", "1")),
                        c -> starts.incrementAndGet(),
                        () -> {});
        HarnessPlugin b =
                plugin(
                        "b",
                        List.of(new PluginDependency("a", "1")),
                        c -> starts.incrementAndGet(),
                        () -> {});
        assertStartupCode("MISSING_DEPENDENCY", List.of(a, b), Set.of("a"));
        assertStartupCode("DEPENDENCY_CYCLE", List.of(a, b), Set.of("a", "b"));
        HarnessPlugin incompatible =
                plugin(
                        "a",
                        List.of(new PluginDependency("b", "2")),
                        c -> starts.incrementAndGet(),
                        () -> {});
        HarnessPlugin base = plugin("b", List.of(), c -> starts.incrementAndGet(), () -> {});
        assertStartupCode("DEPENDENCY_VERSION", List.of(incompatible, base), Set.of("a", "b"));
        assertStartupCode("MISSING_PLUGIN", List.of(base), Set.of("missing"));
        HarnessPlugin wrongApi =
                new HarnessPlugin() {
                    @Override
                    public String id() {
                        return "api";
                    }

                    @Override
                    public String version() {
                        return "1";
                    }

                    @Override
                    public String apiVersion() {
                        return "2.0";
                    }

                    @Override
                    public void start(PluginContext c) {
                        starts.incrementAndGet();
                    }

                    @Override
                    public void stop() {}
                };
        assertStartupCode("API_VERSION", List.of(wrongApi), Set.of("api"));
        assertEquals(0, starts.get());
    }

    @Test
    void partialStartupFailureRollsBackAllServicesSubscriptionsAndResources() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AtomicReference<PluginContext> saved = new AtomicReference<>();
        HarnessPlugin good =
                plugin("good", List.of(), c -> c.register(String.class, "good", "value"), () -> {});
        HarnessPlugin bad =
                plugin(
                        "bad",
                        List.of(),
                        c -> {
                            saved.set(c);
                            c.register(String.class, "bad", "value");
                            c.events().notify("test", e -> callbacks.incrementAndGet());
                            c.own(() -> closed.incrementAndGet());
                            throw new HarnessException("INJECTED", "Startup failure");
                        },
                        () -> {});
        try (PluginContainer container =
                container(registry, List.of(good, bad), Set.of("good", "bad"), List.of())) {
            assertEquals("INJECTED", assertThrows(HarnessException.class, container::start).code());
            assertEquals(PluginContainer.State.FAILED, container.state());
            assertTrue(registry.find(String.class, "good", SCOPE).isEmpty());
            assertTrue(registry.find(String.class, "bad", SCOPE).isEmpty());
            container.events().publish(SessionEvent.session("s", "test", Map.of()));
            container.events().awaitNotifications(Duration.ofSeconds(2));
            assertEquals(0, callbacks.get());
            assertEquals(1, closed.get());
            assertEquals(
                    "CONTEXT_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> saved.get().register(String.class, "late", "bad"))
                            .code());
        }
    }

    @Test
    void missingAndAmbiguousRequiredServicesRollbackStartup() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        HarnessPlugin disabled =
                plugin(
                        "model",
                        List.of(),
                        c -> c.register(LlmClient.class, "model", null),
                        () -> fail("Disabled plugin stopped"));
        try (PluginContainer container =
                container(
                        registry,
                        List.of(disabled),
                        Set.of(),
                        List.of(ServiceRequirement.unique(LlmClient.class)))) {
            assertEquals(
                    "MISSING_SERVICE",
                    assertThrows(HarnessException.class, container::start).code());
        }
        HarnessPlugin ambiguous =
                plugin(
                        "many",
                        List.of(),
                        c -> {
                            c.register(String.class, "one", "1");
                            c.register(String.class, "two", "2");
                        },
                        () -> {});
        try (PluginContainer container =
                container(
                        registry,
                        List.of(ambiguous),
                        Set.of("many"),
                        List.of(ServiceRequirement.unique(String.class)))) {
            assertEquals(
                    "AMBIGUOUS_SERVICE",
                    assertThrows(HarnessException.class, container::start).code());
            assertTrue(registry.find(String.class, "one", SCOPE).isEmpty());
            assertTrue(registry.find(String.class, "two", SCOPE).isEmpty());
        }
    }

    @Test
    void duplicateRegistrationIsDiagnosticAndRevocationIsIdempotent() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        Registration old = registry.register(String.class, "key", "old", SCOPE);
        assertEquals(
                "DUPLICATE_SERVICE",
                assertThrows(
                                HarnessException.class,
                                () -> registry.register(String.class, "key", "new", SCOPE))
                        .code());
        old.close();
        Registration replacement = registry.register(String.class, "key", "replacement", SCOPE);
        old.close();
        assertEquals("replacement", registry.require(String.class, "key", SCOPE));
        replacement.close();
    }

    @Test
    void exactScopesIsolateServicesEventsAndInterceptors() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        PluginScope child = new PluginScope("s", "child");
        AtomicReference<PluginContext> parentContext = new AtomicReference<>();
        AtomicInteger parentHits = new AtomicInteger();
        ExtensionPoint<String> point = new ExtensionPoint<>("prompt", String.class);
        HarnessPlugin parent =
                plugin(
                        "p",
                        List.of(),
                        c -> {
                            parentContext.set(c);
                            c.register(String.class, "key", "parent");
                            c.events().notify("test", e -> parentHits.incrementAndGet());
                            c.events()
                                    .intercept(point, (v, next) -> "parent:" + next.proceed(v), 0);
                        },
                        () -> {});
        HarnessPlugin childPlugin =
                plugin("p", List.of(), c -> c.register(String.class, "key", "child"), () -> {});
        try (PluginContainer one = container(registry, List.of(parent), Set.of("p"), List.of());
                PluginContainer two =
                        new PluginContainer(
                                child,
                                registry,
                                List.of(childPlugin),
                                Set.of("p"),
                                Map.of(),
                                List.of())) {
            one.start();
            two.start();
            assertEquals("parent", registry.require(String.class, "key", SCOPE));
            assertEquals("child", registry.require(String.class, "key", child));
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            parentContext
                                                    .get()
                                                    .services()
                                                    .find(String.class, "key", child))
                            .code());
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            parentContext
                                                    .get()
                                                    .events()
                                                    .publish(
                                                            SessionEvent.session(
                                                                    "other", "test", Map.of())))
                            .code());
            two.events().publish(SessionEvent.session("s", "test", Map.of()));
            two.events().awaitNotifications(Duration.ofSeconds(2));
            assertEquals(0, parentHits.get());
            assertEquals("plain", two.events().invoke(point, "plain", v -> v));
            assertEquals("parent:plain", one.events().invoke(point, "plain", v -> v));
            one.stop();
            assertEquals("plain", one.events().invoke(point, "plain", v -> v));
            assertEquals("child", registry.require(String.class, "key", child));
        }
    }

    @Test
    void configIsPluginSpecificAndImmutableAndLifecycleCanBeObserved() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        var source = new LinkedHashMap<String, Object>();
        source.put("model", "fake");
        var snapshot = new ConfigView(source);
        source.put("model", "mutated");
        source.put("extra", "late");
        List<String> facts = new java.util.concurrent.CopyOnWriteArrayList<>();
        HarnessPlugin p =
                plugin(
                        "p",
                        List.of(),
                        c -> {
                            assertEquals(Optional.of("fake"), c.config().get("model"));
                            assertTrue(c.config().get("extra").isEmpty());
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> c.config().values().clear());
                        },
                        () -> {});
        try (PluginContainer container =
                new PluginContainer(
                        SCOPE,
                        registry,
                        List.of(p),
                        Set.of("p"),
                        Map.of("p", snapshot),
                        List.of())) {
            container.events().notify("*", e -> facts.add(e.type()));
            container.start();
            container.stop();
            container.events().awaitNotifications(Duration.ofSeconds(2));
            assertEquals(List.of("plugin.loaded", "plugin.started", "plugin.stopped"), facts);
        }
    }

    @Test
    void stopFailureStillReleasesEveryContributionAndResource() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        AtomicInteger releases = new AtomicInteger();
        HarnessPlugin p =
                plugin(
                        "p",
                        List.of(),
                        c -> {
                            c.register(String.class, "key", "value");
                            c.own(() -> releases.incrementAndGet());
                            c.own(
                                    () -> {
                                        throw new IllegalStateException("close failed");
                                    });
                        },
                        () -> {
                            throw new IllegalStateException("stop failed");
                        });
        try (PluginContainer container = container(registry, List.of(p), Set.of("p"), List.of())) {
            container.start();
            assertEquals(
                    "CLEANUP_FAILED", assertThrows(HarnessException.class, container::stop).code());
            assertEquals(1, releases.get());
            assertTrue(registry.find(String.class, "key", SCOPE).isEmpty());
        }
    }

    private static void assertStartupCode(
            String code, List<HarnessPlugin> plugins, Set<String> enabled) {
        try (PluginContainer container =
                container(new DefaultServiceRegistry(), plugins, enabled, List.of())) {
            assertEquals(code, assertThrows(HarnessException.class, container::start).code());
        }
    }

    private static PluginContainer container(
            ServiceRegistry registry,
            List<HarnessPlugin> plugins,
            Set<String> enabled,
            List<ServiceRequirement> requirements) {
        return new PluginContainer(SCOPE, registry, plugins, enabled, Map.of(), requirements);
    }

    private static HarnessPlugin plugin(
            String id,
            List<PluginDependency> dependencies,
            Consumer<PluginContext> start,
            Runnable stop) {
        return new HarnessPlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String version() {
                return "1";
            }

            @Override
            public List<PluginDependency> dependencies() {
                return dependencies;
            }

            @Override
            public void start(PluginContext context) {
                start.accept(context);
            }

            @Override
            public void stop() {
                stop.run();
            }
        };
    }
}
