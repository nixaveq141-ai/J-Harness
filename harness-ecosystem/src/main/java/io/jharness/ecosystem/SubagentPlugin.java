package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.session.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** Bounded read-only delegation. The parent remains the sole workspace writer. */
public final class SubagentPlugin implements HarnessPlugin {
    private static final Set<String> READ_TOOLS =
            Set.of("list_directory", "read_file", "search_files", "grep", "read_artifact");

    public record Profile(
            String prompt,
            Set<String> tools,
            Budget budget,
            Supplier<LlmClient> models,
            ApiCostGuard costs,
            CompletionEvaluator completion) {
        public Profile(
                String prompt,
                Set<String> tools,
                Budget budget,
                Supplier<LlmClient> models,
                ApiCostGuard costs) {
            this(
                    prompt,
                    tools,
                    budget,
                    models,
                    costs,
                    (response, projection) ->
                            new CompletionEvaluator.CompletionDecision(
                                    false, "delegated completion criteria required"));
        }

        public Profile {
            Objects.requireNonNull(prompt);
            tools = Set.copyOf(tools);
            Objects.requireNonNull(budget);
            Objects.requireNonNull(models);
            Objects.requireNonNull(costs);
            Objects.requireNonNull(completion);
            if (prompt.length() > 8192
                    || !READ_TOOLS.containsAll(tools)
                    || tools.isEmpty()
                    || budget.maxSteps() > 64
                    || budget.maxDuration().compareTo(Duration.ofMinutes(5)) > 0)
                throw new IllegalArgumentException("Invalid bounded read-only subagent profile");
        }
    }

    private final Map<String, Profile> profiles;
    private final int maximumTasks;
    private final Semaphore slots;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean active;
    private PluginScope scope;
    private SdkHost host;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final Set<LlmClient> models = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Observer observer = new Observer();

    public SubagentPlugin(Map<String, Profile> profiles, int maxConcurrent, int maximumTasks) {
        this.profiles = Map.copyOf(profiles);
        this.maximumTasks = maximumTasks;
        if (profiles.isEmpty()
                || profiles.size() > 16
                || profiles.keySet().stream().anyMatch(id -> !id.matches("[a-zA-Z0-9_-]{1,32}"))
                || maxConcurrent < 1
                || maxConcurrent > 4
                || maximumTasks < maxConcurrent
                || maximumTasks > 32) throw new IllegalArgumentException("Invalid subagent limits");
        slots = new Semaphore(maxConcurrent);
    }

    @Override
    public String id() {
        return "subagent";
    }

    @Override
    public String version() {
        return "1.7";
    }

    @Override
    public void start(PluginContext context) {
        if (!started.compareAndSet(false, true)) throw BoundedJson.error("PLUGIN_ALREADY_USED");
        scope = context.scope();
        host = context.services().requireUnique(SdkHost.class, scope);
        for (var profile : profiles.values()) {
            if (!profile.budget().currency().equals(host.budget().currency()))
                throw BoundedJson.error("SUBAGENT_CURRENCY_MISMATCH");
            if (profile.budget().maxSteps() > host.budget().maxSteps()
                    || profile.budget().maxDuration().compareTo(host.budget().maxDuration()) > 0)
                throw BoundedJson.error("SUBAGENT_BUDGET_EXCEEDS_PARENT");
            if (host.budget().maxApiCost().isPresent()
                    && (profile.budget().maxApiCost().isEmpty()
                            || !profile.budget().currency().equals(host.budget().currency())
                            || profile.budget()
                                            .maxApiCost()
                                            .get()
                                            .compareTo(host.budget().maxApiCost().get())
                                    > 0)) throw BoundedJson.error("SUBAGENT_FEE_BUDGET_REQUIRED");
        }
        context.own(this::shutdown);
        context.register(SubagentService.class, "default", observer);
        var registry = context.services().requireUnique(ToolRegistry.class, scope);
        for (String name : List.of("delegate_task", "subagent_status", "subagent_cancel"))
            context.own(registry.register(new DelegationTool(name), scope));
        active = true;
    }

    @Override
    public void stop() {
        active = false;
    }

    private void event(String type, Map<String, Object> value) {
        host.writer().append(SessionEvent.session(scope.sessionId(), type, value));
        host.writer().flush();
    }

    private final class Task {
        final String id = UUID.randomUUID().toString(), child = "child-" + UUID.randomUUID();
        final String callId, profile, goal;
        final long deadline;
        final AtomicBoolean cancelled = new AtomicBoolean(), finished = new AtomicBoolean();
        final CompletableFuture<Map<String, Object>> outcome = new CompletableFuture<>();
        volatile String state = "RUNNING";
        volatile Thread worker;
        volatile HarnessSdk sdk;
        volatile LlmClient model;
        final AtomicBoolean modelCloseStarted = new AtomicBoolean();
        final CompletableFuture<Void> modelClosed = new CompletableFuture<>();
        String originEvent;

        Task(ToolInvocation call) {
            callId = call.callId();
            profile = (String) call.arguments().get("profile");
            goal = (String) call.arguments().get("goal");
            deadline = System.nanoTime() + profiles.get(profile).budget().maxDuration().toNanos();
        }

        Map<String, Object> metadata() {
            return Map.of(
                    "taskId",
                    id,
                    "childSessionId",
                    child,
                    "parentSessionId",
                    scope.sessionId(),
                    "parentAgentId",
                    scope.agentId(),
                    "parentCallId",
                    callId,
                    "profile",
                    profile,
                    "state",
                    state,
                    "childAgentId",
                    "agent-" + id,
                    "budget",
                    Map.of(
                            "maxSteps",
                            profiles.get(profile).budget().maxSteps(),
                            "maxDurationMs",
                            profiles.get(profile).budget().maxDuration().toMillis(),
                            "maxApiCost",
                            profiles.get(profile)
                                    .budget()
                                    .maxApiCost()
                                    .map(java.math.BigDecimal::toPlainString)
                                    .orElse("none"),
                            "currency",
                            profiles.get(profile).budget().currency()),
                    "cancelRequested",
                    cancelled.get());
        }
    }

    private ToolResult delegate(ToolInvocation call, ToolContext context) {
        Task task;
        synchronized (tasks) {
            if (tasks.size() >= maximumTasks)
                return ToolResults.failure(call.callId(), ToolStatus.FAILED, "SUBAGENT_TASK_LIMIT");
            if (!slots.tryAcquire())
                return ToolResults.failure(
                        call.callId(), ToolStatus.FAILED, "SUBAGENT_CONCURRENCY_LIMIT");
            task = new Task(call);
            tasks.put(task.id, task);
        }
        try {
            var origin =
                    SessionEvent.session(scope.sessionId(), "subagent.started", task.metadata());
            host.writer().append(origin);
            host.writer().flush();
            task.originEvent = origin.eventId();
        } catch (RuntimeException ex) {
            slots.release();
            task.state = "UNKNOWN";
            task.finished.set(true);
            throw ex;
        }
        task.worker = Thread.ofVirtual().unstarted(() -> runChild(task, context.cancellation()));
        task.worker.start();
        while (true) {
            try {
                if (!active
                        || context.cancellation().isCancelled()
                        || task.cancelled.get()
                        || System.nanoTime() >= task.deadline) {
                    cancel(task);
                    try {
                        return result(call.callId(), task.outcome.get(2, TimeUnit.SECONDS));
                    } catch (TimeoutException ex) {
                        abort(task);
                        return result(
                                call.callId(), finish(task, "UNKNOWN", "CHILD_DID_NOT_STOP", null));
                    }
                }
                return result(call.callId(), task.outcome.get(20, TimeUnit.MILLISECONDS));
            } catch (TimeoutException ignored) {
                /* parent cancellation includes its monotonic deadline */
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                cancel(task);
                return result(call.callId(), finish(task, "UNKNOWN", "PARENT_INTERRUPTED", null));
            } catch (ExecutionException ex) {
                cancel(task);
                throw BoundedJson.error("SUBAGENT_RESULT_UNCERTAIN");
            }
        }
    }

    private void runChild(Task task, CancellationToken parentCancellation) {
        var profile = profiles.get(task.profile);
        HarnessSdk.Snapshot snapshot = null;
        String state = "FAILED", reason = "SUBAGENT_START_FAILED";
        boolean modelOwned = false;
        try {
            CancellationToken cancellation =
                    () ->
                            !active
                                    || task.cancelled.get()
                                    || parentCancellation.isCancelled()
                                    || System.nanoTime() >= task.deadline;
            cancellation.throwIfCancelled();
            LlmClient model = Objects.requireNonNull(profile.models().get());
            synchronized (models) {
                if (model == host.model() || !models.add(model))
                    throw BoundedJson.error("SUBAGENT_MODEL_REUSED");
                task.model = model;
                modelOwned = true;
            }
            cancellation.throwIfCancelled();
            var environment =
                    new ReadOnlyChildEnvironment(
                            host.environment(), "view-" + task.id, cancellation::isCancelled);
            PolicyEngine policy =
                    (call, childContext) -> {
                        if (call.sideEffectLevel() != SideEffectLevel.READ_ONLY
                                || !profile.tools().contains(call.tool()))
                            return PolicyEngine.Decision.DENY;
                        return host.policy()
                                .evaluate(
                                        call, new ToolContext(scope, childContext.cancellation()));
                    };
            var options =
                    new SdkOptions(
                            "agent-" + task.id,
                            profile.prompt(),
                            profile.tools(),
                            Map.of(
                                    "taskId",
                                    task.id,
                                    "parentSessionId",
                                    scope.sessionId(),
                                    "parentAgentId",
                                    scope.agentId(),
                                    "parentCallId",
                                    task.callId,
                                    "parentEventId",
                                    task.originEvent),
                            host.fees());
            task.sdk =
                    new HarnessSdk(
                            task.child,
                            "borrowed:" + scope.sessionId(),
                            host.repository(),
                            environment,
                            ownedModel(task, model),
                            host.artifacts(),
                            host.sanitizer(),
                            policy,
                            (call, context) -> host.approvals().decide(call, context),
                            profile.budget(),
                            profile.completion(),
                            profile.costs(),
                            List.of(),
                            options);
            snapshot = task.sdk.run(task.goal, cancellation);
            state = snapshot.status().name();
            reason = "CHILD_TURN_ENDED";
        } catch (Exception ex) {
            reason = ex instanceof HarnessException failure ? failure.code() : "SUBAGENT_FAILED";
            if ("CANCELLED".equals(reason)) state = "CANCELLED";
        } finally {
            try {
                if (task.sdk != null) task.sdk.close();
                else if (modelOwned) closeModel(task);
            } catch (RuntimeException ex) {
                state = "UNKNOWN";
                reason = "CHILD_CLOSE_UNKNOWN";
            }
            try {
                if (!SessionRecovery.inspect(childEvents(task)).ready()) {
                    state = "UNKNOWN";
                    reason = "CHILD_RECOVERY_REQUIRED";
                }
                finish(task, state, reason, snapshot);
            } catch (RuntimeException ex) {
                task.outcome.completeExceptionally(ex);
            }
            slots.release();
        }
    }

    private Map<String, Object> finish(
            Task task, String state, String reason, HarnessSdk.Snapshot snapshot) {
        if (!task.finished.compareAndSet(false, true)) {
            var pending = new LinkedHashMap<String, Object>(task.metadata());
            pending.put("state", "UNKNOWN");
            pending.put("reason", "RESULT_PENDING");
            return task.outcome.getNow(Values.snapshot(pending));
        }
        var summary = new LinkedHashMap<String, Object>(task.metadata());
        summary.put("state", state);
        summary.put("reason", reason);
        summary.put("readOnly", true);
        summary.put("currency", host.budget().currency());
        summary.put("sharedReservedApiCost", host.fees().reserved().toPlainString());
        var childEvents = childEvents(task);
        summary.put(
                "lastChildEventId", childEvents.isEmpty() ? "" : childEvents.getLast().eventId());
        summary.put(
                "evidenceEventIds",
                childEvents.stream()
                        .filter(
                                event ->
                                        event.type().equals("tool.completed")
                                                && "SUCCESS".equals(event.payload().get("status")))
                        .limit(16)
                        .map(SessionEvent::eventId)
                        .toList());
        String text =
                snapshot == null
                        ? ""
                        : snapshot.events().stream()
                                .filter(event -> event.type().equals("assistant.message"))
                                .reduce((first, second) -> second)
                                .map(
                                        event ->
                                                String.valueOf(
                                                        event.payload()
                                                                .getOrDefault("content", "")))
                                .orElse("");
        summary.put("untrustedSummary", text.substring(0, Math.min(8192, text.length())));
        try {
            var artifact =
                    host.artifacts()
                            .put(
                                    scope.sessionId(),
                                    BoundedJson.text(host.sanitizer().sanitize(summary)));
            summary.put(
                    "artifact",
                    Map.of(
                            "id",
                            artifact.id(),
                            "bytes",
                            artifact.bytes(),
                            "sha256",
                            artifact.sha256()));
            event("subagent.completed", summary);
            task.state = state;
            var immutable = Values.snapshot(summary);
            task.outcome.complete(immutable);
            return immutable;
        } catch (Exception ex) {
            task.state = "UNKNOWN";
            task.outcome.completeExceptionally(ex);
            throw BoundedJson.error("SUBAGENT_RESULT_UNCERTAIN");
        }
    }

    private static ToolResult result(String callId, Map<String, Object> summary) {
        String state = (String) summary.get("state");
        ToolStatus status =
                switch (state) {
                    case "COMPLETED" -> ToolStatus.SUCCESS;
                    case "CANCELLED" -> ToolStatus.CANCELLED;
                    case "UNKNOWN" -> ToolStatus.UNKNOWN;
                    default -> ToolStatus.FAILED;
                };
        var value = new LinkedHashMap<>(summary);
        value.put("sideEffectUncertain", status == ToolStatus.UNKNOWN);
        return new ToolResult(
                callId,
                status,
                value,
                "Untrusted subagent report; verify referenced child evidence:\n"
                        + BoundedJson.text(summary),
                summary,
                Optional.empty());
    }

    private void cancel(Task task) {
        if (task.outcome.isDone()) return;
        if (task.cancelled.compareAndSet(false, true))
            event("subagent.cancel_requested", task.metadata());
        if (task.sdk != null) task.sdk.cancel();
    }

    private LlmClient ownedModel(Task task, LlmClient model) {
        return new LlmClient() {
            @Override
            public ModelCapabilities capabilities() {
                return model.capabilities();
            }

            @Override
            public PreparedRequest prepare(ChatRequest request) {
                return model.prepare(request);
            }

            @Override
            public ChatResponse complete(ChatRequest request, CancellationToken token) {
                return model.complete(request, token);
            }

            @Override
            public StreamHandle stream(ChatRequest request, CancellationToken token) {
                return model.stream(request, token);
            }

            @Override
            public void close() {
                closeModel(task);
            }
        };
    }

    private void closeModel(Task task) {
        if (task.modelCloseStarted.compareAndSet(false, true)) {
            try {
                task.model.close();
                task.modelClosed.complete(null);
            } catch (RuntimeException ex) {
                task.modelClosed.completeExceptionally(ex);
                throw ex;
            }
        } else {
            if (!task.modelClosed.isDone()) throw BoundedJson.error("CHILD_CLOSE_PENDING");
            task.modelClosed.join();
        }
    }

    private void abort(Task task) {
        if (task.worker != null) task.worker.interrupt();
        if (task.model != null)
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try {
                                    closeModel(task);
                                } catch (RuntimeException ignored) {
                                    /* parent retains UNKNOWN */
                                }
                            });
    }

    private List<SessionEvent> childEvents(Task task) {
        try {
            return host.repository().openReader(task.child).events();
        } catch (HarnessException ex) {
            if ("SESSION_NOT_FOUND".equals(ex.code())) return List.of();
            throw ex;
        }
    }

    private Task task(String id) {
        synchronized (tasks) {
            var task = tasks.get(id);
            if (task == null) throw BoundedJson.error("UNKNOWN_SUBAGENT");
            return task;
        }
    }

    /** External audit only: both writers must be free and the child log must be settled. */
    public static void verify(
            SessionRepository repository,
            String parentSessionId,
            String taskId,
            String reason,
            DataSanitizer sanitizer) {
        if (reason == null || reason.isBlank() || reason.length() > 4096)
            throw BoundedJson.error("VERIFICATION_REASON_REQUIRED");
        try (var parent =
                new SanitizingSessionWriter(
                        repository.openWriter(parentSessionId, WriteOwnership.EXCLUSIVE),
                        sanitizer)) {
            var events = repository.openReader(parentSessionId).events();
            if (!SessionRecovery.inspect(events).uncertainCalls().contains("subagent:" + taskId))
                throw BoundedJson.error("SUBAGENT_NOT_UNCERTAIN");
            var assignment =
                    events.stream()
                            .filter(
                                    event ->
                                            event.type().equals("subagent.started")
                                                    && taskId.equals(event.payload().get("taskId")))
                            .findFirst()
                            .orElseThrow(() -> BoundedJson.error("UNKNOWN_SUBAGENT"));
            String child = (String) assignment.payload().get("childSessionId");
            boolean exists = repository.list().contains(child);
            try (var childWriter =
                    exists ? repository.openWriter(child, WriteOwnership.EXCLUSIVE) : null) {
                var childEvents =
                        exists ? repository.openReader(child).events() : List.<SessionEvent>of();
                if (!SessionRecovery.inspect(childEvents).ready())
                    throw BoundedJson.error("CHILD_RECOVERY_REQUIRED");
                if (childEvents.stream().anyMatch(event -> event.type().equals("model.requested"))
                        && childEvents.stream()
                                .noneMatch(
                                        event ->
                                                event.type().equals("subagent.origin")
                                                        && taskId.equals(
                                                                event.payload().get("taskId"))
                                                        && parentSessionId.equals(
                                                                event.payload()
                                                                        .get("parentSessionId"))
                                                        && assignment
                                                                .eventId()
                                                                .equals(
                                                                        event.payload()
                                                                                .get(
                                                                                        "parentEventId"))))
                    throw BoundedJson.error("CHILD_ORIGIN_MISMATCH");
                if (childWriter != null) childWriter.flush();
                parent.append(
                        SessionEvent.session(
                                parentSessionId,
                                "subagent.verified",
                                Map.of(
                                        "taskId",
                                        taskId,
                                        "childSessionId",
                                        child,
                                        "reason",
                                        reason,
                                        "actor",
                                        "operator")));
                String callId = (String) assignment.payload().get("parentCallId");
                if (new EventSessionProjection(events).uncertainCalls().contains(callId)) {
                    var source =
                            events.stream()
                                    .filter(
                                            event ->
                                                    callId.equals(event.callId())
                                                            && event.type().equals("tool.intent"))
                                    .findFirst()
                                    .orElseThrow();
                    parent.append(
                            new SessionEvent(
                                    UUID.randomUUID().toString(),
                                    parentSessionId,
                                    "tool.verified",
                                    java.time.Instant.now(),
                                    1,
                                    EventLevel.TOOL,
                                    source.turnId(),
                                    source.stepId(),
                                    callId,
                                    Map.of(
                                            "outcome",
                                            "PARTIAL",
                                            "reason",
                                            reason,
                                            "actor",
                                            "operator",
                                            "childSessionId",
                                            child)));
                }
                parent.flush();
            }
        }
    }

    private final class Observer implements SubagentService {
        private void check() {
            if (!active) throw BoundedJson.error("SCOPE_DENIED");
        }

        @Override
        public List<Map<String, Object>> tasks() {
            check();
            synchronized (tasks) {
                return tasks.values().stream().map(Task::metadata).toList();
            }
        }

        @Override
        public Map<String, Object> status(String taskId) {
            check();
            return task(taskId).metadata();
        }

        @Override
        public void cancel(String taskId) {
            check();
            SubagentPlugin.this.cancel(task(taskId));
        }
    }

    private void shutdown() {
        active = false;
        List<Task> pending;
        synchronized (tasks) {
            pending = tasks.values().stream().filter(task -> !task.finished.get()).toList();
        }
        pending.forEach(this::cancel);
        for (var task : pending)
            if (!task.finished.get()) finish(task, "UNKNOWN", "PARENT_CLOSED", null);
        synchronized (tasks) {
            tasks.values().stream()
                    .filter(
                            task ->
                                    "UNKNOWN".equals(task.state)
                                            && task.worker != null
                                            && task.worker.isAlive())
                    .forEach(this::abort);
        }
    }

    private final class DelegationTool implements AgentTool {
        private final String name;

        DelegationTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Bounded read-only subagent "
                    + name
                    + "; independent session, tools and prompt; parent is the only workspace writer.";
        }

        @Override
        public SideEffectLevel sideEffectLevel() {
            return name.equals("subagent_status")
                    ? SideEffectLevel.READ_ONLY
                    : SideEffectLevel.SYSTEM_IMPACT;
        }

        @Override
        public Map<String, Object> inputSchema() {
            if (!name.equals("delegate_task"))
                return Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("taskId", Map.of("type", "string")),
                        "required",
                        List.of("taskId"),
                        "additionalProperties",
                        false);
            return Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "profile",
                            Map.of("type", "string", "enum", List.copyOf(profiles.keySet())),
                            "goal",
                            Map.of("type", "string", "minLength", 1, "maxLength", 16384)),
                    "required",
                    List.of("profile", "goal"),
                    "additionalProperties",
                    false);
        }

        @Override
        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        @Override
        public ToolResult execute(ToolInvocation args, ToolContext context) {
            if (!active || !scope.equals(context.scope())) throw BoundedJson.error("SCOPE_DENIED");
            context.cancellation().throwIfCancelled();
            if (name.equals("delegate_task")) return delegate(args, context);
            String id = (String) args.arguments().get("taskId");
            if (name.equals("subagent_cancel")) observer.cancel(id);
            var value = observer.status(id);
            return new ToolResult(
                    args.callId(),
                    ToolStatus.SUCCESS,
                    value,
                    BoundedJson.text(value),
                    value,
                    Optional.empty());
        }
    }
}
