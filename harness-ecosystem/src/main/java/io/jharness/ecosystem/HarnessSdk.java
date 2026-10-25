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
    public static final String PROTOCOL_VERSION = "1.14";
    private final PluginScope scope;
    private final SessionWriter writer;
    private final SessionReader reader;
    private final ExecutionEnvironment environment;
    private final LlmClient model;
    private final ModelDisplayBuffer display;
    private final PolicyEngine policy;
    private final Budget budget;
    private final PluginContainer container;
    private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
    private final ToolRegistry tools;
    private final SharedFeeBudget fees;
    private final boolean rootBudget;
    private final Map<String, Object> parameters;
    private final ToolSchedulingOptions scheduling;
    private final ModelResilienceOptions resilience;
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
                        configuration.scheduling(),
                        configuration.resilience()));
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
        resilience = options.resilience();
        tools =
                options.tools() == null
                        ? new ScopedToolRegistry()
                        : new FilteredToolRegistry(new ScopedToolRegistry(), options.tools());
        rootBudget = options.fees() == null;
        fees = rootBudget ? new SharedFeeBudget(budget) : options.fees();
        this.environment = Objects.requireNonNull(environment);
        Objects.requireNonNull(model);
        this.policy = Objects.requireNonNull(policy);
        this.budget = Objects.requireNonNull(budget);
        Objects.requireNonNull(completion);
        Values.required(workspaceBinding, "workspace binding");
        var binding = stableBinding(environment.descriptor());
        binding.put("workspaceBinding", workspaceBinding);
        binding.put("agentId", scope.agentId());
        String bindingFingerprint = bindingFingerprint(binding);
        reader = repository.openReader(sessionId);
        display = new ModelDisplayBuffer(scope, reader, sanitizer);
        var displayModel = model.withDisplayUpdates(display::accept);
        writer =
                new SanitizingSessionWriter(
                        repository.openWriter(sessionId, WriteOwnership.EXCLUSIVE), sanitizer);
        this.model =
                new io.jharness.llm.ObservedLlmClient(
                        displayModel, writer, reader, sanitizer, sessionId);
        PluginContainer assembly = null;
        try {
            var events = reader.events();
            if (SessionLifecycle.archived(events)) throw BoundedJson.error("SESSION_ARCHIVED");
            var created = events.stream().filter(e -> e.type().equals("sdk.created")).findFirst();
            boolean initializing =
                    events.size() == 1
                            && events.getFirst().type().equals("session.created")
                            && "PENDING"
                                    .equals(events.getFirst().payload().get("sdkInitialization"))
                            && bindingFingerprint.equals(
                                    events.getFirst().payload().get("bindingFingerprint"));
            if (!events.isEmpty() && created.isEmpty() && !initializing)
                throw BoundedJson.error("SDK_SESSION_BINDING_REQUIRED");
            if (!initializing && !SessionRecovery.inspect(events).ready())
                throw BoundedJson.error("UNKNOWN_SIDE_EFFECT");
            if (created.isPresent()) {
                Object protocol = created.get().payload().get("protocolVersion");
                if (!(protocol instanceof String version)
                        || !version.matches("1\\.(?:[0-9]|1[0-4])"))
                    throw BoundedJson.error("UNSUPPORTED_SDK_PROTOCOL");
                var previous =
                        new LinkedHashMap<>(Values.object(created.get().payload().get("binding")));
                previous.putIfAbsent("agentId", "default");
                Object fingerprint = created.get().payload().get("bindingFingerprint");
                if (fingerprint == null
                        ? !stableBinding(previous).equals(binding)
                        : !bindingFingerprint.equals(fingerprint))
                    throw BoundedJson.error("ENVIRONMENT_MISMATCH");
            }
            if (created.isEmpty()) {
                if (!initializing)
                    writer.append(
                            SessionEvent.session(
                                    sessionId,
                                    "session.created",
                                    Map.of(
                                            "apiVersion",
                                            ApiVersion.CURRENT,
                                            "sdkInitialization",
                                            "PENDING",
                                            "bindingFingerprint",
                                            bindingFingerprint)));
                writer.append(
                        SessionEvent.session(
                                sessionId,
                                "sdk.created",
                                Map.of(
                                        "protocolVersion",
                                        PROTOCOL_VERSION,
                                        "binding",
                                        binding,
                                        "bindingFingerprint",
                                        bindingFingerprint)));
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
                            context.register(DataSanitizer.class, "default", sanitizer);
                            context.register(SessionWriter.class, "default", writer);
                            context.register(SessionReader.class, "default", reader);
                            context.register(ModelDisplay.class, "default", display);
                            context.events()
                                    .intercept(
                                            StandardExtensionPoints.BEFORE_MODEL,
                                            (request, next) -> {
                                                display.reset();
                                                return next.proceed(request);
                                            },
                                            -1000);
                            if (environment.files() instanceof WorkspaceFiles view)
                                context.register(
                                        ContentInspection.class,
                                        "default",
                                        new WorkspaceContentInspection(
                                                view,
                                                artifacts,
                                                sanitizer,
                                                (call, toolContext) ->
                                                        context.events()
                                                                .invoke(
                                                                        PolicyExtensionPoints
                                                                                .EVALUATE,
                                                                        policy,
                                                                        value -> value)
                                                                .evaluate(
                                                                        call,
                                                                        new ToolContext(
                                                                                scope,
                                                                                toolContext
                                                                                        .cancellation())),
                                                () -> {},
                                                scope.sessionId()));
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
                                    new UserQuestionsPlugin(sanitizer),
                                    new ToolSchedulingPlugin(options.scheduling()),
                                    new ModelResiliencePlugin(options.resilience()),
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
            var inventory =
                    tools.visibleTools(scope).stream()
                            .map(
                                    tool ->
                                            Map.<String, Object>of(
                                                    "name",
                                                    tool.name(),
                                                    "description",
                                                    tool.description()
                                                            .substring(
                                                                    0,
                                                                    Math.min(
                                                                            512,
                                                                            tool.description()
                                                                                    .length())),
                                                    "sideEffect",
                                                    tool.sideEffectLevel().name()))
                            .toList();
            if (inventory.size() > 256 || BoundedJson.encode(inventory).length > 256 * 1024)
                throw BoundedJson.error("TOOL_INVENTORY_LIMIT");
            writer.append(
                    SessionEvent.session(
                            sessionId, "extensions.ready", Map.of("tools", inventory)));
            writer.flush();
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

    private static String bindingFingerprint(Map<String, Object> binding) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(BoundedJson.encode(canonical(binding))));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put((String) key, canonical(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(HarnessSdk::canonical).toList();
        return value;
    }

    private static LinkedHashMap<String, Object> stableBinding(Map<String, Object> descriptor) {
        var result = new LinkedHashMap<>(descriptor);
        result.remove("id");
        if (result.get("parent") instanceof Map<?, ?> parent)
            result.put("parent", stableBinding(Values.object(parent)));
        return result;
    }

    /** A run is serialized; cancellation is callable concurrently and is never reset implicitly. */
    public synchronized Snapshot run(String goal, CancellationToken cancellation) {
        return run(goal, cancellation, Map.of());
    }

    /** Optional host submission association; applied only at a new complete-turn boundary. */
    public synchronized Snapshot run(
            String goal, CancellationToken cancellation, Map<String, String> input) {
        if (closed.get()) throw BoundedJson.error("SDK_CLOSED");
        if (goal == null || goal.isBlank() || goal.length() > 65536)
            throw BoundedJson.error("INVALID_GOAL");
        if (input == null
                || !input.isEmpty()
                        && (!input.keySet().equals(Set.of("requestHash", "kind"))
                                || input.get("requestHash") == null
                                || !input.get("requestHash").matches("[a-f0-9]{64}")
                                || input.get("kind") == null
                                || !Set.of("message", "supplement").contains(input.get("kind"))))
            throw BoundedJson.error("INVALID_SUBMISSION");
        Objects.requireNonNull(cancellation, "cancellation");
        var token = (CancellationToken) () -> cancelled.get() || cancellation.isCancelled();
        token.throwIfCancelled();
        if (!SessionRecovery.inspect(reader.events()).ready())
            throw BoundedJson.error("UNKNOWN_SIDE_EFFECT");
        if (!input.isEmpty()) {
            var applied =
                    reader.events().stream()
                            .filter(
                                    e ->
                                            e.type().equals("sdk.input.applied")
                                                    && input.get("requestHash")
                                                            .equals(e.payload().get("requestHash")))
                            .findFirst();
            if (applied.isPresent()) {
                if (!input.get("kind").equals(applied.get().payload().get("kind"))
                        || !bindingFingerprint(Map.of("content", goal))
                                .equals(applied.get().payload().get("inputContentHash")))
                    throw BoundedJson.error("SUBMISSION_CONFLICT");
                var history = reader.events();
                int boundary = history.indexOf(applied.get());
                boolean ended = false;
                for (int i = boundary + 1; i < history.size(); i++) {
                    var event = history.get(i);
                    if (event.type().equals("sdk.input.applied")) break;
                    if (event.type().equals("turn.ended")) ended = true;
                }
                if (!ended) throw BoundedJson.error("SUBMISSION_NOT_CONFIRMED");
                return snapshot();
            }
        }
        if (rootBudget) fees.beginTurn();
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
                                scheduling.configuration(),
                                "modelResilience",
                                resilience.configuration())));
        var userMessage =
                SessionEvent.session(
                        scope.sessionId(), "user.message", Map.of("role", "user", "content", goal));
        if (input.isEmpty()) writer.append(userMessage);
        else
            writer.append(
                    SessionEvent.session(
                            scope.sessionId(),
                            "sdk.input.applied",
                            Map.of(
                                    "requestHash",
                                    input.get("requestHash"),
                                    "kind",
                                    input.get("kind"),
                                    "userMessage",
                                    userMessage.payload(),
                                    "inputContentHash",
                                    bindingFingerprint(Map.of("content", goal)),
                                    "boundary",
                                    "next-turn")));
        writer.flush();
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

    public ContentInspection content() {
        return registry.requireUnique(ContentInspection.class, scope);
    }

    public ModelDisplay display() {
        return display;
    }

    public UserQuestionService questions() {
        return registry.requireUnique(UserQuestionService.class, scope);
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
        var existing = repository.openReader(sessionId).events();
        if (SessionLifecycle.archived(existing)) throw BoundedJson.error("SESSION_ARCHIVED");
        try (var writer =
                new SanitizingSessionWriter(
                        repository.openWriter(sessionId, WriteOwnership.EXCLUSIVE), sanitizer)) {
            var events = repository.openReader(sessionId).events();
            if (SessionLifecycle.archived(events)) throw BoundedJson.error("SESSION_ARCHIVED");
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
            Throwable primary = null;
            String cleanup = "SUCCESS";
            try {
                container.close();
            } catch (RuntimeException | Error ex) {
                primary = ex;
                cleanup = "UNKNOWN";
            }
            try {
                writer.append(
                        SessionEvent.session(
                                scope.sessionId(),
                                "environment.closed",
                                Map.of("environmentId", environment.id(), "status", cleanup)));
                writer.append(
                        SessionEvent.session(
                                scope.sessionId(),
                                "terminal.snapshot",
                                Map.of("tasks", environment.terminal().states())));
                writer.flush();
            } catch (RuntimeException | Error ex) {
                if (primary == null) primary = ex;
                else primary.addSuppressed(ex);
            }
            try {
                model.close();
            } catch (RuntimeException | Error ex) {
                if (primary == null) primary = ex;
                else primary.addSuppressed(ex);
            }
            try {
                writer.close();
            } catch (RuntimeException | Error ex) {
                if (primary == null) primary = ex;
                else primary.addSuppressed(ex);
            }
            if (primary instanceof Error fatal) throw fatal;
            if (primary instanceof RuntimeException failure) throw failure;
        }
    }
}
