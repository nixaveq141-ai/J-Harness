package io.jharness.ecosystem;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.plugin.*;
import io.jharness.policy.PolicyPlugin;
import io.jharness.session.*;
import io.jharness.tools.ToolSchedulingPlugin;
import io.jharness.tools.ToolsPlugin;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Embedded, single-session owner. Hosts supply trusted configuration and evidence evaluator. */
public final class HarnessSdk implements AutoCloseable {
    public static final String PROTOCOL_VERSION = "1.8";
    private final PluginScope scope;
    private final SessionWriter writer;
    private final SessionReader reader;
    private final ExecutionEnvironment environment;
    private final LlmClient model;
    private final PolicyEngine policy;
    private final Budget budget;
    private final PluginContainer container;
    private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
    private final ToolRegistry tools;
    private final SharedFeeBudget fees;
    private final boolean rootBudget;
    private final Map<String, Object> parameters;
    private final ToolSchedulingOptions scheduling;
    private final AtomicBoolean cancelled = new AtomicBoolean(), closed = new AtomicBoolean();

    /**
     * Takes ownership of environment and model once construction succeeds. Storage stays
     * host-owned.
     */
    public HarnessSdk(
            String sessionId,
            String workspaceBinding,
            SessionRepository repository,
            ExecutionEnvironment environment,
            LlmClient model,
            ArtifactStore artifacts,
            DataSanitizer sanitizer,
            PolicyEngine policy,
            ApprovalService approvals,
            Budget budget,
            CompletionEvaluator completion,
            ApiCostGuard costs,
            List<HarnessPlugin> extensions) {
        this(
                sessionId,
                workspaceBinding,
                repository,
                environment,
                model,
                artifacts,
                sanitizer,
                policy,
                approvals,
                budget,
                completion,
                costs,
                extensions,
                SdkOptions.root());
    }

    public HarnessSdk(
            String sessionId,
            String workspaceBinding,
            SessionRepository repository,
            ExecutionEnvironment environment,
            LlmClient model,
            ArtifactStore artifacts,
            DataSanitizer sanitizer,
            PolicyEngine policy,
            ApprovalService approvals,
            Budget budget,
            CompletionEvaluator completion,
            ApiCostGuard costs,
            List<HarnessPlugin> extensions,
            SdkConfiguration configuration) {
        this(
                sessionId,
                workspaceBinding,
                repository,
                environment,
                model,
                artifacts,
                sanitizer,
                policy,
                approvals,
                budget,
                completion,
                costs,
                extensions,
                new SdkOptions(
                        "default",
                        configuration.prompt(),
                        configuration.tools(),
                        Map.of(),
                        null,
                        configuration.parameters(),
                        configuration.scheduling()));
    }

    HarnessSdk(
            String sessionId,
            String workspaceBinding,
            SessionRepository repository,
            ExecutionEnvironment environment,
            LlmClient model,
            ArtifactStore artifacts,
            DataSanitizer sanitizer,
            PolicyEngine policy,
            ApprovalService approvals,
            Budget budget,
            CompletionEvaluator completion,
            ApiCostGuard costs,
            List<HarnessPlugin> extensions,
            SdkOptions options) {
        scope = new PluginScope(sessionId, options.agentId());
        parameters = options.parameters();
        scheduling = options.scheduling();
        tools =
                options.tools() == null
                        ? new ScopedToolRegistry()
                        : new FilteredToolRegistry(new ScopedToolRegistry(), options.tools());
        rootBudget = options.fees() == null;
        fees = rootBudget ? new SharedFeeBudget(budget) : options.fees();
        this.environment = Objects.requireNonNull(environment);
        this.model = Objects.requireNonNull(model);
        this.policy = Objects.requireNonNull(policy);
        this.budget = Objects.requireNonNull(budget);
        Objects.requireNonNull(completion);
        Values.required(workspaceBinding, "workspace binding");
        var binding = new LinkedHashMap<>(environment.descriptor());
        binding.remove("id");
        binding.put("workspaceBinding", workspaceBinding);
        binding.put("agentId", scope.agentId());
        reader = repository.openReader(sessionId);
        writer =
                new SanitizingSessionWriter(
                        repository.openWriter(sessionId, WriteOwnership.EXCLUSIVE), sanitizer);
        PluginContainer assembly = null;
        try {
            var events = reader.events();
            if (events.stream().anyMatch(e -> e.type().equals("session.archived")))
                throw BoundedJson.error("SESSION_ARCHIVED");
            if (!SessionRecovery.inspect(events).ready())
                throw BoundedJson.error("UNKNOWN_SIDE_EFFECT");
            var created = events.stream().filter(e -> e.type().equals("sdk.created")).findFirst();
            if (!events.isEmpty() && created.isEmpty())
                throw BoundedJson.error("SDK_SESSION_BINDING_REQUIRED");
            if (created.isPresent()) {
                var previous =
                        new LinkedHashMap<>(Values.object(created.get().payload().get("binding")));
                previous.putIfAbsent("agentId", "default");
                if (!previous.equals(binding)) throw BoundedJson.error("ENVIRONMENT_MISMATCH");
            }
            if (created.isEmpty()) {
                writer.append(
                        SessionEvent.session(
                                sessionId,
                                "session.created",
                                Map.of("apiVersion", ApiVersion.CURRENT)));
                writer.append(
                        SessionEvent.session(
                                sessionId,
                                "sdk.created",
                                Map.of("protocolVersion", PROTOCOL_VERSION, "binding", binding)));
                writer.flush();
            }
            if (!options.origin().isEmpty()) {
                writer.append(SessionEvent.session(sessionId, "subagent.origin", options.origin()));
                writer.flush();
            }
            if (!options.prompt().isBlank()) {
                writer.append(
                        SessionEvent.session(
                                sessionId,
                                "system.message",
                                Map.of("role", "system", "content", options.prompt())));
                writer.flush();
            }
            var services =
                    new AgentRuntimeServices(
                            scope,
                            reader,
                            EventSessionProjection::new,
                            new DefaultCheckpointPolicy(),
                            approvals,
                            new ToolPipeline() {
                                public ToolResult execute(
                                        ToolInvocation call, ToolPipelineContext context) {
                                    return registry.requireUnique(ToolPipeline.class, scope)
                                            .execute(call, context);
                                }

                                public Optional<PreparedToolCall> prepare(
                                        ToolInvocation call,
                                        ToolPipelineContext context,
                                        Runnable barrier) {
                                    return registry.requireUnique(ToolPipeline.class, scope)
                                            .prepare(call, context, barrier);
                                }
                            },
                            new EventRequestJournal(),
                            sanitizer,
                            completion,
                            fees.guard(costs));
            HarnessPlugin registryPlugin =
                    new HarnessPlugin() {
                        @Override
                        public String id() {
                            return "sdk-registry";
                        }

                        @Override
                        public String version() {
                            return PROTOCOL_VERSION;
                        }

                        @Override
                        public void start(PluginContext context) {
                            context.register(ToolRegistry.class, "default", tools);
                            context.register(SessionWriter.class, "default", writer);
                            context.register(SessionReader.class, "default", reader);
                            context.register(
                                    SdkHost.class,
                                    "default",
                                    new SdkHost(
                                            scope,
                                            repository,
                                            reader,
                                            writer,
                                            environment,
                                            model,
                                            artifacts,
                                            sanitizer,
                                            (call, toolContext) ->
                                                    context.events()
                                                            .invoke(
                                                                    PolicyExtensionPoints.EVALUATE,
                                                                    policy,
                                                                    value -> value)
                                                            .evaluate(call, toolContext),
                                            approvals,
                                            budget,
                                            fees));
                        }

                        @Override
                        public void stop() {}
                    };
            var plugins =
                    new ArrayList<HarnessPlugin>(
                            List.of(
                                    registryPlugin,
                                    new JsonlSessionPlugin(repository, artifacts),
                                    new EnvironmentPlugin(environment, artifacts),
                                    new PolicyPlugin(policy, approvals),
                                    new ToolsPlugin(sanitizer),
                                    new ToolSchedulingPlugin(options.scheduling()),
                                    new AgentPlugin(services)));
            plugins.addAll(extensions);
            assembly =
                    new PluginContainer(
                            scope,
                            registry,
                            plugins,
                            plugins.stream()
                                    .map(HarnessPlugin::id)
                                    .collect(java.util.stream.Collectors.toSet()),
                            Map.of(),
                            List.of());
            assembly.start();
            if (tools instanceof FilteredToolRegistry filtered) filtered.validate(scope);
            container = assembly;
        } catch (RuntimeException ex) {
            try {
                if (assembly != null) assembly.close();
            } catch (RuntimeException cleanup) {
                ex.addSuppressed(cleanup);
            } finally {
                writer.close();
            }
            throw ex;
        }
    }

    /** A run is serialized; cancellation is callable concurrently and is never reset implicitly. */
    public synchronized Snapshot run(String goal, CancellationToken cancellation) {
        if (closed.get()) throw BoundedJson.error("SDK_CLOSED");
        var token = (CancellationToken) () -> cancelled.get() || cancellation.isCancelled();
        token.throwIfCancelled();
        if (!SessionRecovery.inspect(reader.events()).ready())
            throw BoundedJson.error("UNKNOWN_SIDE_EFFECT");
        if (rootBudget) fees.beginTurn();
        if (goal == null || goal.isBlank() || goal.length() > 65536)
            throw BoundedJson.error("INVALID_GOAL");
        writer.append(
                SessionEvent.session(
                        scope.sessionId(),
                        "sdk.turn.configured",
                        Map.of(
                                "agentId",
                                scope.agentId(),
                                "budget",
                                Map.of(
                                        "maxSteps",
                                        budget.maxSteps(),
                                        "maxDurationMs",
                                        budget.maxDuration().toMillis(),
                                        "maxApiCost",
                                        budget.maxApiCost()
                                                .map(java.math.BigDecimal::toPlainString)
                                                .orElse("none"),
                                        "currency",
                                        budget.currency()),
                                "feeScope",
                                rootBudget ? "parent-turn" : "inherited-parent-turn",
                                "toolScheduling",
                                scheduling.configuration())));
        writer.append(
                SessionEvent.session(
                        scope.sessionId(),
                        "user.message",
                        Map.of("role", "user", "content", goal)));
        var loop =
                registry.requireUnique(AgentLoopFactory.class, scope)
                        .create(
                                new AgentLoopDependencies(
                                        model,
                                        tools,
                                        writer,
                                        new EventSessionProjection(reader.events()),
                                        new DefaultPromptComposer(tools, scope, parameters),
                                        container.events(),
                                        policy,
                                        token,
                                        budget));
        loop.run(token);
        writer.flush();
        return snapshot();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public Snapshot snapshot() {
        var events = reader.events();
        return new Snapshot(
                PROTOCOL_VERSION,
                scope.sessionId(),
                new EventSessionProjection(events).taskStatus(),
                events);
    }

    public record Snapshot(
            String protocolVersion,
            String sessionId,
            TaskStatus status,
            List<SessionEvent> events) {
        public Snapshot {
            events = List.copyOf(events);
        }
    }

    /**
     * Records an operator's external cleanup inspection; never removes a container or reruns a
     * task.
     */
    public static void verifyEnvironment(
            SessionRepository repository,
            String sessionId,
            String environmentId,
            String reason,
            DataSanitizer sanitizer) {
        if (reason == null || reason.isBlank() || reason.length() > 4096)
            throw BoundedJson.error("VERIFICATION_REASON_REQUIRED");
        try (var writer =
                new SanitizingSessionWriter(
                        repository.openWriter(sessionId, WriteOwnership.EXCLUSIVE), sanitizer)) {
            var events = repository.openReader(sessionId).events();
            if (!SessionRecovery.inspect(events)
                    .uncertainCalls()
                    .contains("environment:" + environmentId))
                throw BoundedJson.error("ENVIRONMENT_NOT_UNCERTAIN");
            writer.append(
                    SessionEvent.session(
                            sessionId,
                            "environment.verified",
                            Map.of(
                                    "environmentId",
                                    environmentId,
                                    "reason",
                                    reason,
                                    "actor",
                                    "operator")));
            writer.flush();
        }
    }

    @Override
    public void close() {
        cancel();
        synchronized (this) {
            if (!closed.compareAndSet(false, true)) return;
            try {
                try {
                    container.close();
                    writer.append(
                            SessionEvent.session(
                                    scope.sessionId(),
                                    "environment.closed",
                                    Map.of(
                                            "environmentId",
                                            environment.id(),
                                            "status",
                                            "SUCCESS")));
                } catch (RuntimeException ex) {
                    writer.append(
                            SessionEvent.session(
                                    scope.sessionId(),
                                    "environment.closed",
                                    Map.of(
                                            "environmentId",
                                            environment.id(),
                                            "status",
                                            "UNKNOWN")));
                    writer.flush();
                    throw ex;
                }
                writer.append(
                        SessionEvent.session(
                                scope.sessionId(),
                                "terminal.snapshot",
                                Map.of("tasks", environment.terminal().states())));
                writer.flush();
            } finally {
                try {
                    model.close();
                } finally {
                    writer.close();
                }
            }
        }
    }
}
