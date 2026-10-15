package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M7SubagentTest {
    @TempDir Path base;
    private static final PluginScope PARENT = new PluginScope("parent", "default");
    private JsonlSessionRepository repository;
    private ArtifactStore artifacts;

    private void prepare() throws Exception {
        Files.createDirectories(base.resolve("workspace"));
        Files.writeString(base.resolve("workspace/input.txt"), "before");
        repository = new JsonlSessionRepository(base.resolve("sessions"));
        artifacts = new LocalArtifactStore(base.resolve("artifacts"), new SecretRedactor());
    }

    private static ChatResponse call(String name, Map<String, Object> arguments) {
        return new ChatResponse(
                "fixture",
                List.of(
                        new ToolInvocation(
                                UUID.randomUUID().toString(),
                                name,
                                arguments,
                                SideEffectLevel.READ_ONLY)),
                Map.of());
    }

    private static ChatResponse done() {
        return new ChatResponse("untrusted child summary", List.of(), Map.of());
    }

    private static FakeLlm readerModel() {
        return new FakeLlm(List.of(call("read_file", Map.of("path", "input.txt")), done()), 32768);
    }

    private static SubagentPlugin.Profile profile(Supplier<LlmClient> factory) {
        return new SubagentPlugin.Profile(
                "child-only prompt",
                Set.of("read_file"),
                new Budget(4, Duration.ofSeconds(10)),
                factory,
                ApiCostGuard.unavailable(),
                readEvidence());
    }

    private static CompletionEvaluator readEvidence() {
        return (response, projection) ->
                new CompletionEvaluator.CompletionDecision(
                        projection.toolHistory().stream()
                                .anyMatch(
                                        event ->
                                                event.type().equals("tool.completed")
                                                        && "read_file"
                                                                .equals(event.payload().get("tool"))
                                                        && "SUCCESS"
                                                                .equals(
                                                                        event.payload()
                                                                                .get("status"))
                                                        && "input.txt"
                                                                .equals(
                                                                        Values.object(
                                                                                        event.payload()
                                                                                                .get(
                                                                                                        "value"))
                                                                                .get("path"))),
                        "input.txt read evidence required");
    }

    private HarnessSdk sdk(
            LlmClient parent,
            SubagentPlugin plugin,
            ApprovalService approvals,
            Budget budget,
            ApiCostGuard costs,
            CompletionEvaluator completion,
            List<HarnessPlugin> extras)
            throws Exception {
        var extensions = new ArrayList<HarnessPlugin>();
        extensions.add(plugin);
        extensions.addAll(extras);
        return new HarnessSdk(
                "parent",
                base.resolve("workspace").toString(),
                repository,
                new LocalExecutionEnvironment("parent-env", base.resolve("workspace"), List.of()),
                parent,
                artifacts,
                new SecretRedactor(),
                new DefaultPolicyEngine(),
                approvals,
                budget,
                completion,
                costs,
                extensions);
    }

    private static Budget parentBudget() {
        return new Budget(8, Duration.ofSeconds(30));
    }

    private static CompletionEvaluator notComplete() {
        return (response, projection) ->
                new CompletionEvaluator.CompletionDecision(false, "fixture requires evidence");
    }

    private static void reconstruct(List<SessionEvent> events, FakeLlm model) {
        int requests = 0;
        for (int i = 0; i < events.size(); i++)
            if (events.get(i).type().equals("model.requested"))
                assertEquals(
                        model.requests().get(requests++),
                        new EventRequestJournal()
                                .reconstruct(events.subList(0, i + 1), events.get(i).eventId()));
        assertEquals(model.requests().size(), requests);
    }

    @Test
    void independentChildCannotWriteAndParentCanEditAfterEvidenceWithFullOwnershipAndReplay()
            throws Exception {
        prepare();
        var child =
                new FakeLlm(
                        List.of(
                                call(
                                        "write_file",
                                        Map.of("path", "forbidden.txt", "content", "bad")),
                                call("execute_command", Map.of("argv", List.of("bad-command"))),
                                call("read_file", Map.of("path", "input.txt")),
                                done()),
                        32768);
        var parent =
                new FakeLlm(
                        List.of(
                                call(
                                        "delegate_task",
                                        Map.of("profile", "inspect", "goal", "read input.txt")),
                                call("read_file", Map.of("path", "input.txt")),
                                call(
                                        "edit_file",
                                        Map.of(
                                                "path",
                                                "input.txt",
                                                "oldText",
                                                "before",
                                                "newText",
                                                "after")),
                                done()),
                        32768);
        AtomicInteger approvals = new AtomicInteger();
        var capture = new Capture();
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                profile(
                                        () -> {
                                            assertTrue(
                                                    repository
                                                            .openReader("parent")
                                                            .events()
                                                            .stream()
                                                            .anyMatch(
                                                                    event ->
                                                                            event.type()
                                                                                            .equals(
                                                                                                    "tool.intent")
                                                                                    && "delegate_task"
                                                                                            .equals(
                                                                                                    event.payload()
                                                                                                            .get(
                                                                                                                    "tool"))));
                                            return child;
                                        })),
                        2,
                        8);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        (call, context) -> {
                            approvals.incrementAndGet();
                            return new ApprovalService.ApprovalDecision(
                                    true, "fixed task approved", "operator");
                        },
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        (response, projection) -> {
                            try {
                                return new CompletionEvaluator.CompletionDecision(
                                        Files.readString(base.resolve("workspace/input.txt"))
                                                .equals("after"),
                                        "actual parent edit");
                            } catch (Exception ex) {
                                return new CompletionEvaluator.CompletionDecision(
                                        false, "missing file");
                            }
                        },
                        List.of(capture))) {
            var result =
                    sdk.run(
                            "parent-only-context: delegate reading then edit",
                            CancellationToken.NONE);
            assertEquals(TaskStatus.COMPLETED, result.status());
            assertEquals(2, approvals.get());
            assertFalse(Files.exists(base.resolve("workspace/forbidden.txt")));
            var assignment =
                    result.events().stream()
                            .filter(event -> event.type().equals("subagent.started"))
                            .findFirst()
                            .orElseThrow();
            String childId = (String) assignment.payload().get("childSessionId");
            var childEvents = repository.openReader(childId).events();
            var origin =
                    childEvents.stream()
                            .filter(event -> event.type().equals("subagent.origin"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(assignment.eventId(), origin.payload().get("parentEventId"));
            assertEquals("parent", origin.payload().get("parentSessionId"));
            assertTrue(
                    childEvents.stream()
                                    .filter(event -> event.type().equals("tool.completed"))
                                    .filter(
                                            event ->
                                                    event.payload().get("error")
                                                                    instanceof Map<?, ?> error
                                                            && "UNKNOWN_TOOL"
                                                                    .equals(error.get("code")))
                                    .count()
                            >= 2);
            assertTrue(
                    child.requests().stream()
                            .allMatch(
                                    request ->
                                            !request.messages()
                                                            .toString()
                                                            .contains("parent-only-context")
                                                    && !request.parameters()
                                                            .containsKey("parentHook")));
            assertTrue(
                    child.requests()
                            .getFirst()
                            .messages()
                            .toString()
                            .contains("child-only prompt"));
            assertEquals(1, child.requests().getFirst().tools().size());
            assertTrue(
                    parent.requests().stream()
                            .allMatch(
                                    request ->
                                            Boolean.TRUE.equals(
                                                    request.parameters().get("parentHook"))));
            var completion =
                    result.events().stream()
                            .filter(event -> event.type().equals("subagent.completed"))
                            .findFirst()
                            .orElseThrow();
            var reference = Values.object(completion.payload().get("artifact"));
            String report = artifacts.read("parent", (String) reference.get("id"), 0, 65536);
            assertEquals(
                    reference.get("sha256"),
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(report.getBytes(StandardCharsets.UTF_8))));
            assertTrue(report.contains(childId));
            assertTrue(report.contains("evidenceEventIds"));
            reconstruct(result.events(), parent);
            reconstruct(childEvents, child);
            assertTrue(SessionRecovery.inspect(result.events()).ready());
        }
        assertTrue(capture.tools.visibleTools(PARENT).isEmpty());
        assertThrows(HarnessException.class, () -> capture.service.tasks());
    }

    @Test
    void rejectingDelegationNeverCreatesAChildOrModel() throws Exception {
        prepare();
        AtomicInteger factories = new AtomicInteger();
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                profile(
                                        () -> {
                                            factories.incrementAndGet();
                                            return readerModel();
                                        })),
                        1,
                        2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        ApprovalService.rejectAll(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of())) {
            var result = sdk.run("delegate", CancellationToken.NONE);
            assertEquals(0, factories.get());
            assertEquals(List.of("parent"), repository.list());
            assertTrue(
                    result.events().stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("tool.completed")
                                                    && "DENIED"
                                                            .equals(
                                                                    event.payload()
                                                                            .get("status"))));
            assertFalse(
                    result.events().stream()
                            .anyMatch(event -> event.type().equals("subagent.started")));
        }
    }

    @Test
    void sharedFeeCapIncludesParentAndChildRequests() throws Exception {
        prepare();
        var child = readerModel();
        ApiCostGuard price = request -> new BigDecimal("0.30");
        var budget = new Budget(4, Duration.ofSeconds(20), Optional.of(BigDecimal.ONE), "USD");
        var childBudget = new Budget(2, Duration.ofSeconds(10), Optional.of(BigDecimal.ONE), "USD");
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                new SubagentPlugin.Profile(
                                        "child",
                                        Set.of("read_file"),
                                        childBudget,
                                        () -> child,
                                        price,
                                        readEvidence())),
                        1,
                        2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        budget,
                        price,
                        notComplete(),
                        List.of())) {
            var result = sdk.run("inspect", CancellationToken.NONE);
            assertEquals(TaskStatus.LIMIT_REACHED, result.status());
            assertEquals(1, parent.requests().size());
            assertEquals(2, child.requests().size());
            assertEquals(
                    "COST_LIMIT",
                    result.events().stream()
                            .filter(event -> event.type().equals("turn.ended"))
                            .reduce((a, b) -> b)
                            .orElseThrow()
                            .payload()
                            .get("reason"));
            var report =
                    result.events().stream()
                            .filter(event -> event.type().equals("subagent.completed"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    0,
                    new BigDecimal((String) report.payload().get("sharedReservedApiCost"))
                            .compareTo(new BigDecimal("0.90")));
        }
    }

    @Test
    void concurrencyQuotaAndTaskQuotaDoNotLaunchExtraModels() throws Exception {
        prepare();
        var child = new BlockingModel(false);
        AtomicInteger factories = new AtomicInteger();
        var capture = new Capture();
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                profile(
                                        () ->
                                                factories.incrementAndGet() == 1
                                                        ? child
                                                        : readerModel())),
                        1,
                        2);
        try (var sdk =
                sdk(
                        new FakeLlm(List.of(), 32768),
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of(capture))) {
            assertEquals(TaskStatus.CREATED, sdk.snapshot().status());
            var first =
                    CompletableFuture.supplyAsync(
                            () -> capture.delegate("read", CancellationToken.NONE));
            assertTrue(child.entered.await(3, TimeUnit.SECONDS));
            var overflow = capture.delegate("second", CancellationToken.NONE);
            assertEquals(ToolStatus.FAILED, overflow.status());
            assertEquals("SUBAGENT_CONCURRENCY_LIMIT", overflow.error().orElseThrow().code());
            assertEquals(1, factories.get());
            assertEquals(1, capture.service.tasks().size());
            assertEquals("RUNNING", capture.service.tasks().getFirst().get("state"));
            child.release.countDown();
            assertEquals(ToolStatus.SUCCESS, first.get(5, TimeUnit.SECONDS).status());
            assertEquals(
                    ToolStatus.SUCCESS, capture.delegate("third", CancellationToken.NONE).status());
            assertEquals(
                    "SUBAGENT_TASK_LIMIT",
                    capture.delegate("fourth", CancellationToken.NONE)
                            .error()
                            .orElseThrow()
                            .code());
            assertEquals(2, factories.get());
        } finally {
            child.release.countDown();
        }
    }

    @Test
    void parentCancellationAndUncooperativeChildStayUnknownUntilExplicitSettledAudit()
            throws Exception {
        prepare();
        var child = new BlockingModel(true);
        var childBudget = new Budget(2, Duration.ofMillis(800));
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                new SubagentPlugin.Profile(
                                        "child",
                                        Set.of("read_file"),
                                        childBudget,
                                        () -> child,
                                        ApiCostGuard.unavailable())),
                        1,
                        2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        String taskId, childId;
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        new Budget(4, Duration.ofSeconds(6)),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of())) {
            long start = System.nanoTime();
            var snapshot = sdk.run("delegate", CancellationToken.NONE);
            assertTrue(
                    Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5))
                            < 0);
            assertEquals(1, parent.requests().size());
            assertFalse(SessionRecovery.inspect(snapshot.events()).ready());
            var assignment =
                    snapshot.events().stream()
                            .filter(event -> event.type().equals("subagent.started"))
                            .findFirst()
                            .orElseThrow();
            taskId = (String) assignment.payload().get("taskId");
            childId = (String) assignment.payload().get("childSessionId");
            assertThrows(HarnessException.class, () -> sdk.run("again", CancellationToken.NONE));
        }
        try {
            assertThrows(
                    HarnessException.class,
                    () ->
                            SubagentPlugin.verify(
                                    repository,
                                    "parent",
                                    taskId,
                                    "external check",
                                    new SecretRedactor()));
        } finally {
            child.release.countDown();
        }
        awaitWriter(childId);
        assertFalse(SessionRecovery.inspect(repository.openReader(childId).events()).ready());
        assertThrows(
                HarnessException.class,
                () ->
                        SubagentPlugin.verify(
                                repository,
                                "parent",
                                taskId,
                                "child not settled",
                                new SecretRedactor()));
        try (var writer = repository.openWriter(childId, WriteOwnership.EXCLUSIVE)) {
            for (String step :
                    SessionRecovery.inspect(repository.openReader(childId).events())
                            .pendingRequests())
                writer.append(
                        SessionEvent.session(
                                childId,
                                "model.verified",
                                Map.of(
                                        "stepId",
                                        step,
                                        "reason",
                                        "fixture model stopped and no external IO occurred",
                                        "actor",
                                        "operator")));
            writer.flush();
        }
        SubagentPlugin.verify(
                repository,
                "parent",
                taskId,
                "child writer released and pending request audited",
                new SecretRedactor());
        assertTrue(SessionRecovery.inspect(repository.openReader("parent").events()).ready());
        assertFalse(
                repository.openReader("parent").events().stream()
                        .anyMatch(
                                event ->
                                        event.type().equals("tool.completed")
                                                && "SUCCESS"
                                                        .equals(event.payload().get("status"))));
    }

    @Test
    void profilesCannotGrantWriteToolsOrExceedParentBudget() throws Exception {
        prepare();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SubagentPlugin.Profile(
                                "bad",
                                Set.of("write_file"),
                                new Budget(2, Duration.ofSeconds(1)),
                                M7SubagentTest::readerModel,
                                ApiCostGuard.unavailable()));
        var plugin =
                new SubagentPlugin(Map.of("inspect", profile(M7SubagentTest::readerModel)), 1, 2);
        assertEquals(
                "SUBAGENT_BUDGET_EXCEEDS_PARENT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        sdk(
                                                new FakeLlm(List.of()),
                                                plugin,
                                                M7EcosystemFixture.approve(),
                                                new Budget(1, Duration.ofSeconds(1)),
                                                ApiCostGuard.unavailable(),
                                                notComplete(),
                                                List.of()))
                        .code());
    }

    @Test
    void separateReadObservationDoesNotAuthorizeParentOverwrite() throws Exception {
        prepare();
        var parent = new io.jharness.workspace.LocalWorkspace(base.resolve("workspace"));
        var child = parent.isolatedView();
        var path = new ExecPath("input.txt");
        assertEquals("before", child.readText(path));
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () -> parent.write(path, "bad", true, null, CancellationToken.NONE))
                        .code());
        assertEquals("before", parent.readText(path));
        parent.write(path, "after", true, null, CancellationToken.NONE);
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        child.write(
                                                path, "stale", true, null, CancellationToken.NONE))
                        .code());
        assertEquals("after", Files.readString(base.resolve("workspace/input.txt")));
    }

    private void awaitWriter(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try (var writer = repository.openWriter(id, WriteOwnership.EXCLUSIVE)) {
                writer.flush();
                return;
            } catch (HarnessException ex) {
                if (System.nanoTime() >= deadline) throw ex;
                Thread.sleep(10);
            }
        }
    }

    @Test
    void twoReadOnlyChildrenRunConcurrentlyWithDistinctContextsAndLogs() throws Exception {
        prepare();
        var firstModel = new BlockingModel(false);
        var secondModel = new BlockingModel(false);
        var capture = new Capture();
        AtomicInteger created = new AtomicInteger();
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                profile(
                                        () ->
                                                created.getAndIncrement() == 0
                                                        ? firstModel
                                                        : secondModel)),
                        2,
                        2);
        try (var sdk =
                sdk(
                        new FakeLlm(List.of(), 32768),
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of(capture))) {
            assertEquals(TaskStatus.CREATED, sdk.snapshot().status());
            var first =
                    CompletableFuture.supplyAsync(
                            () -> capture.delegate("goal-one", CancellationToken.NONE));
            assertTrue(firstModel.entered.await(3, TimeUnit.SECONDS));
            var second =
                    CompletableFuture.supplyAsync(
                            () -> capture.delegate("goal-two", CancellationToken.NONE));
            assertTrue(secondModel.entered.await(3, TimeUnit.SECONDS));
            assertEquals(2, capture.service.tasks().size());
            assertFalse(SessionRecovery.inspect(repository.openReader("parent").events()).ready());
            firstModel.release.countDown();
            secondModel.release.countDown();
            var firstResult = first.get(5, TimeUnit.SECONDS);
            var secondResult = second.get(5, TimeUnit.SECONDS);
            assertEquals(ToolStatus.SUCCESS, firstResult.status());
            assertEquals(ToolStatus.SUCCESS, secondResult.status());
            assertNotEquals(
                    firstResult.value().get("childSessionId"),
                    secondResult.value().get("childSessionId"));
            assertTrue(
                    firstModel
                            .delegate
                            .requests()
                            .getFirst()
                            .messages()
                            .toString()
                            .contains("goal-one"));
            assertFalse(
                    firstModel
                            .delegate
                            .requests()
                            .getFirst()
                            .messages()
                            .toString()
                            .contains("goal-two"));
            reconstruct(
                    repository
                            .openReader((String) firstResult.value().get("childSessionId"))
                            .events(),
                    firstModel.delegate);
            reconstruct(
                    repository
                            .openReader((String) secondResult.value().get("childSessionId"))
                            .events(),
                    secondModel.delegate);
            assertTrue(SessionRecovery.inspect(repository.openReader("parent").events()).ready());
            assertEquals("before", Files.readString(base.resolve("workspace/input.txt")));
        } finally {
            firstModel.release.countDown();
            secondModel.release.countDown();
        }
    }

    @Test
    void explicitParentCancellationReachesChildAndDoesNotClaimSuccess() throws Exception {
        prepare();
        var child = new BlockingModel(false);
        var capture = new Capture();
        var plugin = new SubagentPlugin(Map.of("inspect", profile(() -> child)), 1, 2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of(capture))) {
            var running =
                    CompletableFuture.supplyAsync(
                            () -> sdk.run("delegate", CancellationToken.NONE));
            assertTrue(child.entered.await(3, TimeUnit.SECONDS));
            sdk.cancel();
            var result = running.get(4, TimeUnit.SECONDS);
            assertNotEquals(TaskStatus.COMPLETED, result.status());
            assertEquals(1, parent.requests().size());
            assertTrue(
                    result.events().stream()
                            .anyMatch(event -> event.type().equals("subagent.cancel_requested")));
            assertFalse(SessionRecovery.inspect(result.events()).ready());
            assertEquals(1, child.closed.get());
        } finally {
            child.release.countDown();
        }
    }

    @Test
    void parentPolicyExtensionsStillRestrictChildReads() throws Exception {
        prepare();
        var child = readerModel();
        HarnessPlugin denyRead =
                new HarnessPlugin() {
                    @Override
                    public String id() {
                        return "deny-child-read";
                    }

                    @Override
                    public String version() {
                        return "1.7";
                    }

                    @Override
                    public void start(PluginContext context) {
                        context.events()
                                .intercept(
                                        PolicyExtensionPoints.EVALUATE,
                                        (policy, next) ->
                                                next.proceed(
                                                        (call, toolContext) ->
                                                                call.tool().equals("read_file")
                                                                        ? PolicyEngine.Decision.DENY
                                                                        : policy.evaluate(
                                                                                call, toolContext)),
                                        0);
                    }

                    @Override
                    public void stop() {}
                };
        var plugin = new SubagentPlugin(Map.of("inspect", profile(() -> child)), 1, 2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of(denyRead))) {
            var result = sdk.run("delegate", CancellationToken.NONE);
            var assignment =
                    result.events().stream()
                            .filter(event -> event.type().equals("subagent.started"))
                            .findFirst()
                            .orElseThrow();
            var events =
                    repository
                            .openReader((String) assignment.payload().get("childSessionId"))
                            .events();
            assertTrue(
                    events.stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("tool.completed")
                                                    && "DENIED"
                                                            .equals(
                                                                    event.payload()
                                                                            .get("status"))));
            assertTrue(
                    result.events().stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("subagent.completed")
                                                    && "FAILED"
                                                            .equals(event.payload().get("state"))));
        }
    }

    @Test
    void feeLimitedParentRequiresBoundedChildFees() throws Exception {
        prepare();
        var plugin =
                new SubagentPlugin(Map.of("inspect", profile(M7SubagentTest::readerModel)), 1, 2);
        assertEquals(
                "SUBAGENT_FEE_BUDGET_REQUIRED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        sdk(
                                                new FakeLlm(List.of()),
                                                plugin,
                                                M7EcosystemFixture.approve(),
                                                new Budget(
                                                        8,
                                                        Duration.ofSeconds(30),
                                                        Optional.of(BigDecimal.ONE),
                                                        "USD"),
                                                request -> BigDecimal.ZERO,
                                                notComplete(),
                                                List.of()))
                        .code());
    }

    @Test
    void failedAssignmentPersistenceNeverConstructsChildModel() throws Exception {
        prepare();
        AtomicInteger factories = new AtomicInteger();
        SessionRepository broken =
                new SessionRepository() {
                    @Override
                    public SessionWriter openWriter(String id, WriteOwnership ownership) {
                        var delegate = repository.openWriter(id, ownership);
                        return new SessionWriter() {
                            @Override
                            public boolean durable() {
                                return delegate.durable();
                            }

                            @Override
                            public String append(SessionEvent event) {
                                if (event.type().equals("subagent.started"))
                                    throw new HarnessException(
                                            "CHECKPOINT_FAILED", "assignment fault fixture");
                                return delegate.append(event);
                            }

                            @Override
                            public void flush() {
                                delegate.flush();
                            }

                            @Override
                            public void close() {
                                delegate.close();
                            }
                        };
                    }

                    @Override
                    public SessionReader openReader(String id) {
                        return repository.openReader(id);
                    }

                    @Override
                    public List<String> list() {
                        return repository.list();
                    }

                    @Override
                    public String fork(String id, String event) {
                        return repository.fork(id, event);
                    }
                };
        var plugin =
                new SubagentPlugin(
                        Map.of(
                                "inspect",
                                profile(
                                        () -> {
                                            factories.incrementAndGet();
                                            return readerModel();
                                        })),
                        1,
                        2);
        var parent =
                new FakeLlm(
                        List.of(
                                call(
                                        "delegate_task",
                                        Map.of("profile", "inspect", "goal", "read"))),
                        32768);
        try (var sdk =
                new HarnessSdk(
                        "parent",
                        base.toString(),
                        broken,
                        new LocalExecutionEnvironment(
                                "local", base.resolve("workspace"), List.of()),
                        parent,
                        artifacts,
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        notComplete(),
                        ApiCostGuard.unavailable(),
                        List.of(plugin))) {
            var snapshot = sdk.run("delegate", CancellationToken.NONE);
            assertEquals(0, factories.get());
            assertEquals(List.of("parent"), repository.list());
            assertFalse(
                    snapshot.events().stream()
                            .anyMatch(event -> event.type().equals("subagent.started")));
            assertFalse(SessionRecovery.inspect(snapshot.events()).ready());
        }
    }

    @Test
    void reusingParentModelIsRejectedWithoutClosingIt() throws Exception {
        prepare();
        AtomicInteger closes = new AtomicInteger();
        var script =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        LlmClient parent =
                new LlmClient() {
                    @Override
                    public ModelCapabilities capabilities() {
                        return script.capabilities();
                    }

                    @Override
                    public PreparedRequest prepare(ChatRequest request) {
                        return script.prepare(request);
                    }

                    @Override
                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        return script.complete(request, token);
                    }

                    @Override
                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        return script.stream(request, token);
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                };
        var plugin = new SubagentPlugin(Map.of("inspect", profile(() -> parent)), 1, 2);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of())) {
            var snapshot = sdk.run("delegate", CancellationToken.NONE);
            assertTrue(
                    snapshot.events().stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("subagent.completed")
                                                    && "SUBAGENT_MODEL_REUSED"
                                                            .equals(
                                                                    event.payload()
                                                                            .get("reason"))));
            assertEquals(0, closes.get());
            assertEquals(2, script.requests().size());
            assertEquals(List.of("parent"), repository.list());
        }
        assertEquals(1, closes.get());
    }

    @Test
    void missingChildCompletionCriteriaCannotPromoteModelClaimToSuccess() throws Exception {
        prepare();
        var child = readerModel();
        var profile =
                new SubagentPlugin.Profile(
                        "inspect",
                        Set.of("read_file"),
                        new Budget(2, Duration.ofSeconds(10)),
                        () -> child,
                        ApiCostGuard.unavailable());
        var plugin = new SubagentPlugin(Map.of("inspect", profile), 1, 2);
        var parent =
                new FakeLlm(
                        List.of(
                                call("delegate_task", Map.of("profile", "inspect", "goal", "read")),
                                done()),
                        32768);
        try (var sdk =
                sdk(
                        parent,
                        plugin,
                        M7EcosystemFixture.approve(),
                        parentBudget(),
                        ApiCostGuard.unavailable(),
                        notComplete(),
                        List.of())) {
            var snapshot = sdk.run("delegate", CancellationToken.NONE);
            assertTrue(
                    snapshot.events().stream()
                            .anyMatch(
                                    event ->
                                            event.type().equals("subagent.completed")
                                                    && "FAILED"
                                                            .equals(event.payload().get("state"))));
            assertEquals(2, child.requests().size());
        }
    }

    private static final class BlockingModel implements LlmClient {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final FakeLlm delegate = readerModel();
        final boolean ignoreCancellation;
        final AtomicInteger closed = new AtomicInteger();

        BlockingModel(boolean ignoreCancellation) {
            this.ignoreCancellation = ignoreCancellation;
        }

        @Override
        public ModelCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public PreparedRequest prepare(ChatRequest request) {
            return delegate.prepare(request);
        }

        @Override
        public ChatResponse complete(ChatRequest request, CancellationToken token) {
            entered.countDown();
            while (release.getCount() != 0) {
                if (!ignoreCancellation) token.throwIfCancelled();
                try {
                    release.await(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    /* deliberate noncooperative fixture if configured */
                }
            }
            return delegate.complete(request, ignoreCancellation ? CancellationToken.NONE : token);
        }

        @Override
        public StreamHandle stream(ChatRequest request, CancellationToken token) {
            return delegate.stream(request, token);
        }

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    private static final class Capture implements HarnessPlugin {
        ToolRegistry tools;
        SubagentService service;
        SessionWriter writer;
        EventBus events;
        ToolPipeline pipeline;

        @Override
        public String id() {
            return "parent-capture";
        }

        @Override
        public String version() {
            return "1.7";
        }

        @Override
        public void start(PluginContext context) {
            tools = context.services().requireUnique(ToolRegistry.class, context.scope());
            service = context.services().requireUnique(SubagentService.class, context.scope());
            writer = context.services().requireUnique(SessionWriter.class, context.scope());
            events = context.events();
            pipeline = context.services().requireUnique(ToolPipeline.class, context.scope());
            context.events()
                    .intercept(
                            StandardExtensionPoints.BEFORE_MODEL,
                            (request, next) -> {
                                var parameters = new LinkedHashMap<>(request.parameters());
                                parameters.put("parentHook", true);
                                return next.proceed(
                                        new ChatRequest(
                                                request.messages(), request.tools(), parameters));
                            },
                            0);
        }

        @Override
        public void stop() {}

        ToolResult delegate(String goal, CancellationToken token) {
            return pipeline.execute(
                    new ToolInvocation(
                            UUID.randomUUID().toString(),
                            "delegate_task",
                            Map.of("profile", "inspect", "goal", goal),
                            SideEffectLevel.READ_ONLY),
                    new ToolPipelineContext(
                            PARENT,
                            "fixture-turn",
                            "fixture-step",
                            tools,
                            writer,
                            events,
                            new DefaultPolicyEngine(),
                            M7EcosystemFixture.approve(),
                            new DefaultCheckpointPolicy(),
                            token));
        }
    }
}
