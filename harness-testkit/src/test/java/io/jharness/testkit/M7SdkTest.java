package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M7SdkTest {
    @TempDir Path root;

    private HarnessSdk open(
            String id, String binding, FakeLlm model, CompletionEvaluator completion)
            throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        return new HarnessSdk(
                id,
                binding,
                new JsonlSessionRepository(root.resolve("sessions")),
                new LocalExecutionEnvironment("test-local", root.resolve("workspace"), List.of()),
                model,
                new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor()),
                new SecretRedactor(),
                new DefaultPolicyEngine(),
                ApprovalService.rejectAll(),
                new Budget(5, Duration.ofSeconds(20)),
                completion,
                ApiCostGuard.unavailable(),
                List.of());
    }

    private static ChatResponse reply(ToolInvocation... calls) {
        return new ChatResponse("fixture", List.of(calls), Map.of());
    }

    private static CompletionEvaluator evidence() {
        return (response, projection) ->
                new CompletionEvaluator.CompletionDecision(
                        projection.toolHistory().stream()
                                .anyMatch(
                                        e ->
                                                e.type().equals("tool.completed")
                                                        && "SUCCESS"
                                                                .equals(e.payload().get("status"))),
                        "successful read required");
    }

    @Test
    void embeddedLoopUsesScopedToolsAndEveryRequestReconstructsFromDurableEvents()
            throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        Files.writeString(root.resolve("workspace/input.txt"), "evidence");
        var model =
                new FakeLlm(
                        List.of(
                                reply(
                                        new ToolInvocation(
                                                "read",
                                                "read_file",
                                                Map.of("path", "input.txt"),
                                                SideEffectLevel.READ_ONLY)),
                                reply()));
        List<SessionEvent> events;
        try (var sdk = open("s", "workspace-a", model, evidence())) {
            var result = sdk.run("read input", CancellationToken.NONE);
            assertEquals(TaskStatus.COMPLETED, result.status());
            events = result.events();
            assertEquals("1.7", result.protocolVersion());
            assertThrows(
                    HarnessException.class,
                    () ->
                            new JsonlSessionRepository(root.resolve("sessions"))
                                    .openWriter("s", WriteOwnership.EXCLUSIVE));
        }
        int count = 0;
        for (int i = 0; i < events.size(); i++)
            if (events.get(i).type().equals("model.requested"))
                assertEquals(
                        model.requests().get(count++),
                        new EventRequestJournal()
                                .reconstruct(events.subList(0, i + 1), events.get(i).eventId()));
        assertEquals(2, count);
        try (var sdk = open("s", "workspace-a", new FakeLlm(List.of()), evidence())) {
            assertEquals(TaskStatus.COMPLETED, sdk.snapshot().status());
        }
        assertEquals(
                "ENVIRONMENT_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () -> open("s", "workspace-b", new FakeLlm(List.of()), evidence()))
                        .code());
    }

    @Test
    void sdkCannotBypassApprovalOrExecuteAfterCancellation() throws Exception {
        var model =
                new FakeLlm(
                        List.of(
                                reply(
                                        new ToolInvocation(
                                                "write",
                                                "write_file",
                                                Map.of("path", "denied.txt", "content", "bad"),
                                                SideEffectLevel.READ_ONLY)),
                                reply()));
        try (var sdk =
                open(
                        "s",
                        "a",
                        model,
                        (r, p) ->
                                new CompletionEvaluator.CompletionDecision(
                                        false, "not verified"))) {
            var result = sdk.run("try write", CancellationToken.NONE);
            assertFalse(Files.exists(root.resolve("workspace/denied.txt")));
            assertTrue(
                    result.events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "DENIED".equals(e.payload().get("status"))));
            sdk.cancel();
            assertThrows(HarnessException.class, () -> sdk.run("again", CancellationToken.NONE));
        }
    }

    @Test
    void unknownEnvironmentCleanupRequiresExplicitOperatorFact() throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        var local =
                new LocalExecutionEnvironment(
                        "cleanup-local", root.resolve("workspace"), List.of());
        ExecutionEnvironment failing =
                new ExecutionEnvironment() {
                    @Override
                    public String id() {
                        return local.id();
                    }

                    @Override
                    public FileSystemView files() {
                        return local.files();
                    }

                    @Override
                    public ProcessRunner processes() {
                        return local.processes();
                    }

                    @Override
                    public TerminalSession terminal() {
                        return local.terminal();
                    }

                    @Override
                    public Map<String, Object> descriptor() {
                        return local.descriptor();
                    }

                    @Override
                    public void close() {
                        local.close();
                        throw new HarnessException("CONTAINER_CLEANUP_UNKNOWN", "fixture");
                    }
                };
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var sdk =
                new HarnessSdk(
                        "s",
                        "a",
                        repo,
                        failing,
                        new FakeLlm(List.of()),
                        new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor()),
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        ApprovalService.rejectAll(),
                        new Budget(2, Duration.ofSeconds(10)),
                        evidence(),
                        ApiCostGuard.unavailable(),
                        List.of());
        assertThrows(HarnessException.class, sdk::close);
        assertFalse(SessionRecovery.inspect(repo.openReader("s").events()).ready());
        assertThrows(
                HarnessException.class, () -> open("s", "a", new FakeLlm(List.of()), evidence()));
        HarnessSdk.verifyEnvironment(
                repo,
                "s",
                local.id(),
                "operator inspected cleanup externally",
                new SecretRedactor());
        assertTrue(SessionRecovery.inspect(repo.openReader("s").events()).ready());
        assertThrows(
                HarnessException.class,
                () ->
                        HarnessSdk.verifyEnvironment(
                                repo, "s", local.id(), "repeat", new SecretRedactor()));
        try (var reopened = open("s", "a", new FakeLlm(List.of()), evidence())) {
            assertEquals(TaskStatus.CREATED, reopened.snapshot().status());
        }
    }

    @Test
    void coldPendingRequestBlocksAnotherSdkRun() throws Exception {
        try (var sdk = open("s", "a", new FakeLlm(List.of()), evidence())) {
            assertEquals(TaskStatus.CREATED, sdk.snapshot().status());
        }
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(
                    new SessionEvent(
                            "pending",
                            "s",
                            "model.requested",
                            java.time.Instant.now(),
                            1,
                            EventLevel.STEP,
                            "t",
                            "step",
                            null,
                            Map.of()));
            writer.flush();
        }
        assertEquals(
                "UNKNOWN_SIDE_EFFECT",
                assertThrows(
                                HarnessException.class,
                                () -> open("s", "a", new FakeLlm(List.of()), evidence()))
                        .code());
    }
}
