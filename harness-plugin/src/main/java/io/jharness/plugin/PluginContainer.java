package io.jharness.plugin;

import io.jharness.api.*;
import io.jharness.events.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Static, one-shot container. Dependency validation precedes all plugin side effects. */
public final class PluginContainer implements AutoCloseable {
    private final PluginScope scope;
    private final ServiceRegistry registry;
    private final DefaultEventBus bus;
    private final Map<String, Descriptor> plugins = new LinkedHashMap<>();
    private final Set<String> enabled;
    private final Map<String, ConfigView> configs;
    private final List<ServiceRequirement> requirements;
    private final List<Running> running = new ArrayList<>();
    private final List<CleanupFailure> cleanupFailures = new ArrayList<>();
    private State state = State.NEW;
    private final AtomicBoolean closed = new AtomicBoolean();

    public enum State {
        NEW,
        STARTING,
        RUNNING,
        FAILED,
        STOPPED,
        STOP_FAILED
    }

    public PluginContainer(
            PluginScope scope,
            ServiceRegistry registry,
            List<HarnessPlugin> plugins,
            Set<String> enabled,
            Map<String, ConfigView> configs,
            List<ServiceRequirement> requirements) {
        this(scope, registry, plugins, enabled, configs, requirements, null);
    }

    public PluginContainer(
            PluginScope scope,
            ServiceRegistry registry,
            List<HarnessPlugin> plugins,
            Set<String> enabled,
            Map<String, ConfigView> configs,
            List<ServiceRequirement> requirements,
            Consumer<EventFailure> diagnostics) {
        this.scope = Objects.requireNonNull(scope);
        this.registry = Objects.requireNonNull(registry);
        this.bus = diagnostics == null ? new DefaultEventBus() : new DefaultEventBus(diagnostics);
        this.enabled = Set.copyOf(enabled);
        this.configs = Map.copyOf(configs);
        this.requirements = List.copyOf(requirements);
        for (HarnessPlugin plugin : plugins) {
            Descriptor descriptor =
                    new Descriptor(
                            Values.required(plugin.id(), "plugin id"),
                            Values.required(plugin.version(), "plugin version"),
                            Values.required(plugin.apiVersion(), "apiVersion"),
                            List.copyOf(plugin.dependencies()),
                            plugin);
            if (this.plugins.putIfAbsent(descriptor.id, descriptor) != null)
                throw new HarnessException(
                        "DUPLICATE_PLUGIN", "Duplicate plugin id: " + descriptor.id);
        }
    }

    public DefaultEventBus events() {
        return bus;
    }

    public synchronized State state() {
        return state;
    }

    /** Failed cleanup is evidence of unconfirmed resources, never permission to retry stop. */
    public synchronized List<CleanupFailure> cleanupFailures() {
        return List.copyOf(cleanupFailures);
    }

    public record CleanupFailure(String pluginId, String stage, String causeType) {}

    public synchronized void start() {
        if (state != State.NEW)
            throw new HarnessException("CONTAINER_STATE", "Container cannot start from " + state);
        state = State.STARTING;
        String activating = "container";
        try {
            List<Descriptor> ordered = order();
            for (Descriptor descriptor : ordered) {
                activating = descriptor.id;
                Context context =
                        new Context(configs.getOrDefault(descriptor.id, ConfigView.empty()));
                Running item = new Running(descriptor, context);
                running.add(item); // Include partially started plugin in rollback.
                lifecycle("plugin.loaded", descriptor.id);
                descriptor.plugin.start(context);
                lifecycle("plugin.started", descriptor.id);
            }
            activating = "container";
            for (ServiceRequirement requirement : requirements) {
                if (requirement.key() == null) registry.requireUnique(requirement.type(), scope);
                else registry.require(requirement.type(), requirement.key(), scope);
            }
            state = State.RUNNING;
        } catch (RuntimeException | Error ex) {
            state = State.FAILED;
            try {
                lifecycle("plugin.failed", activating);
            } catch (RuntimeException | Error notification) {
                ex.addSuppressed(notification);
            }
            RuntimeException cleanup = cleanup();
            if (cleanup != null) ex.addSuppressed(cleanup);
            throw ex;
        }
    }

    private List<Descriptor> order() {
        for (String id : enabled)
            if (!plugins.containsKey(id))
                throw new HarnessException("MISSING_PLUGIN", "Enabled plugin unavailable: " + id);
        List<Descriptor> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String id : plugins.keySet())
            if (enabled.contains(id)) visit(id, visited, visiting, result);
        return result;
    }

    private void visit(
            String id, Set<String> visited, Set<String> visiting, List<Descriptor> result) {
        if (visited.contains(id)) return;
        if (!visiting.add(id))
            throw new HarnessException(
                    "DEPENDENCY_CYCLE", "Dependency cycle: " + visiting + " -> " + id);
        Descriptor descriptor = plugins.get(id);
        if (!ApiVersion.CURRENT.equals(descriptor.apiVersion))
            throw new HarnessException("API_VERSION", "Incompatible plugin API: " + id);
        Set<String> declared = new HashSet<>();
        for (PluginDependency dependency : descriptor.dependencies) {
            if (!declared.add(dependency.id()))
                throw new HarnessException(
                        "DUPLICATE_DEPENDENCY", "Duplicate dependency: " + dependency.id());
            Descriptor target = plugins.get(dependency.id());
            if (target == null || !enabled.contains(dependency.id()))
                throw new HarnessException(
                        "MISSING_DEPENDENCY", id + " requires enabled " + dependency.id());
            if (!target.version.equals(dependency.version()))
                throw new HarnessException(
                        "DEPENDENCY_VERSION",
                        id + " requires " + dependency.id() + "@" + dependency.version());
            visit(dependency.id(), visited, visiting, result);
        }
        visiting.remove(id);
        visited.add(id);
        result.add(descriptor);
    }

    private void lifecycle(String type, String pluginId) {
        bus.publish(
                SessionEvent.session(
                        scope.sessionId(),
                        type,
                        Map.of("pluginId", pluginId, "agentId", scope.agentId())));
    }

    private RuntimeException cleanup() {
        RuntimeException failure = null;
        for (int i = running.size() - 1; i >= 0; i--) {
            Running item = running.get(i);
            try {
                item.descriptor.plugin.stop();
            } catch (RuntimeException | Error ex) {
                cleanupFailures.add(
                        new CleanupFailure(item.descriptor.id, "stop", ex.getClass().getName()));
                failure = accumulate(failure, ex);
            }
            try {
                item.context.close();
            } catch (RuntimeException | Error ex) {
                cleanupFailures.add(
                        new CleanupFailure(
                                item.descriptor.id, "owned.close", ex.getClass().getName()));
                failure = accumulate(failure, ex);
            }
            try {
                lifecycle("plugin.stopped", item.descriptor.id);
            } catch (RuntimeException | Error notification) {
                failure = accumulate(failure, notification);
            }
        }
        running.clear();
        return failure;
    }

    private static RuntimeException accumulate(RuntimeException previous, Throwable next) {
        if (previous == null)
            return new HarnessException("CLEANUP_FAILED", "Plugin cleanup failed", next);
        previous.addSuppressed(next);
        return previous;
    }

    /** Stop contributions but leave observers available to inspect lifecycle facts. */
    public synchronized void stop() {
        if (state == State.STOPPED || state == State.STOP_FAILED) return;
        RuntimeException failure = cleanup();
        state = failure == null ? State.STOPPED : State.STOP_FAILED;
        if (failure != null) throw failure;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        try {
            stop();
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            bus.awaitNotifications(Duration.ofSeconds(2));
        } catch (RuntimeException observation) {
            if (failure == null) failure = observation;
            else failure.addSuppressed(observation);
        }
        try {
            bus.close();
        } catch (RuntimeException observation) {
            if (failure == null) failure = observation;
            else failure.addSuppressed(observation);
        }
        if (failure != null) throw failure;
    }

    private record Descriptor(
            String id,
            String version,
            String apiVersion,
            List<PluginDependency> dependencies,
            HarnessPlugin plugin) {}

    private record Running(Descriptor descriptor, Context context) {}

    private final class Context implements PluginContext, AutoCloseable {
        private final ConfigView config;
        private final List<Registration> owned = new ArrayList<>();
        private boolean closed;
        private final EventBus scopedBus = new OwnedBus();
        private final ServiceRegistry scopedServices =
                new ServiceRegistry() {
                    private void verify(PluginScope requested) {
                        if (!scope.equals(requested))
                            throw new HarnessException(
                                    "SCOPE_DENIED", "Another scope is not visible");
                        ensureOpen();
                    }

                    @Override
                    public <T> Registration register(
                            Class<T> type, String key, T implementation, PluginScope requested) {
                        verify(requested);
                        return Context.this.register(type, key, implementation);
                    }

                    @Override
                    public <T> Optional<T> find(Class<T> type, String key, PluginScope requested) {
                        verify(requested);
                        return registry.find(type, key, requested)
                                .map(value -> scoped(type, value));
                    }

                    @Override
                    public <T> T require(Class<T> type, String key, PluginScope requested) {
                        verify(requested);
                        return scoped(type, registry.require(type, key, requested));
                    }

                    @Override
                    public <T> T requireUnique(Class<T> type, PluginScope requested) {
                        verify(requested);
                        return scoped(type, registry.requireUnique(type, requested));
                    }
                };

        Context(ConfigView config) {
            this.config = config;
        }

        private <T> T scoped(Class<T> type, T service) {
            if (type != ToolRegistry.class) return service;
            ToolRegistry tools = (ToolRegistry) service;
            return type.cast(
                    new ToolRegistry() {
                        private void verify(PluginScope requested) {
                            ensureOpen();
                            if (!scope.equals(requested))
                                throw new HarnessException(
                                        "SCOPE_DENIED", "Another tool scope is not visible");
                        }

                        public Registration register(AgentTool tool, PluginScope requested) {
                            verify(requested);
                            return Context.this.own(tools.register(tool, requested));
                        }

                        public Optional<AgentTool> resolve(String name, PluginScope requested) {
                            verify(requested);
                            return tools.resolve(name, requested);
                        }

                        public List<AgentTool> visibleTools(PluginScope requested) {
                            verify(requested);
                            return tools.visibleTools(requested);
                        }
                    });
        }

        private synchronized void ensureOpen() {
            if (closed) throw new HarnessException("CONTEXT_CLOSED", "Plugin context is closed");
        }

        @Override
        public synchronized <T> Registration register(Class<T> type, String key, T implementation) {
            ensureOpen();
            return own(registry.register(type, key, implementation, scope));
        }

        @Override
        public Registration own(AutoCloseable resource) {
            Objects.requireNonNull(resource);
            AtomicBoolean revoked = new AtomicBoolean();
            Registration handle =
                    () -> {
                        if (revoked.compareAndSet(false, true)) {
                            try {
                                resource.close();
                            } catch (Exception ex) {
                                throw new HarnessException(
                                        "RESOURCE_CLOSE", "Resource close failed", ex);
                            }
                        }
                    };
            synchronized (this) {
                if (!closed) {
                    owned.add(handle);
                    return handle;
                }
            }
            handle.close();
            throw new HarnessException("CONTEXT_CLOSED", "Plugin context is closed");
        }

        @Override
        public ServiceRegistry services() {
            ensureOpen();
            return scopedServices;
        }

        @Override
        public EventBus events() {
            ensureOpen();
            return scopedBus;
        }

        @Override
        public ConfigView config() {
            ensureOpen();
            return config;
        }

        @Override
        public PluginScope scope() {
            return scope;
        }

        @Override
        public void close() {
            List<Registration> snapshot;
            synchronized (this) {
                if (closed) return;
                closed = true;
                snapshot = new ArrayList<>(owned);
                owned.clear();
            }
            RuntimeException failure = null;
            for (int i = snapshot.size() - 1; i >= 0; i--) {
                try {
                    snapshot.get(i).close();
                } catch (RuntimeException | Error error) {
                    failure = accumulate(failure, error);
                }
            }
            if (failure != null) throw failure;
        }

        private final class OwnedBus implements EventBus {
            private void verify(SessionEvent event) {
                ensureOpen();
                if (!scope.sessionId().equals(event.sessionId()))
                    throw new HarnessException("SCOPE_DENIED", "Event belongs to another session");
            }

            @Override
            public void publish(SessionEvent event) {
                verify(event);
                bus.publish(event);
            }

            @Override
            public void publishSerial(SessionEvent event) {
                verify(event);
                bus.publishSerial(event);
            }

            @Override
            public Registration notify(String type, NotificationListener listener) {
                synchronized (Context.this) {
                    ensureOpen();
                    return own(bus.notify(type, listener));
                }
            }

            @Override
            public Registration serial(
                    String type, SerialListener listener, int priority, Duration timeout) {
                synchronized (Context.this) {
                    ensureOpen();
                    return own(bus.serial(type, listener, priority, timeout));
                }
            }

            @Override
            public <T> Registration intercept(
                    ExtensionPoint<T> point, Interceptor<T> interceptor, int priority) {
                synchronized (Context.this) {
                    ensureOpen();
                    return own(bus.intercept(point, interceptor, priority));
                }
            }

            @Override
            public <T> T invoke(ExtensionPoint<T> point, T request, Interceptor.Next<T> terminal) {
                ensureOpen();
                return bus.invoke(point, request, terminal);
            }
        }
    }
}
