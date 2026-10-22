package io.jharness.policy;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PolicyApprovalTest {
    private final PluginScope scope = new PluginScope("s", "a");

    ToolInvocation command(Map<String, Object> args) {
        return new ToolInvocation("c", "execute_command", args, SideEffectLevel.SYSTEM_IMPACT);
    }

    @Test
    void trustedCommandIsExactAndShellOrSuffixNeedsApproval() {
        var policy = new DefaultPolicyEngine();
        var context = new ToolContext(scope, CancellationToken.NONE);
        List<String> safe =
                List.of(
                        "git",
                        "--no-optional-locks",
                        "-c",
                        "core.fsmonitor=false",
                        "status",
                        "--short");
        assertEquals(
                PolicyEngine.Decision.ALLOW,
                policy.evaluate(command(Map.of("argv", safe)), context));
        assertEquals(
                PolicyEngine.Decision.ASK,
                policy.evaluate(
                        command(
                                Map.of(
                                        "argv",
                                        List.of(
                                                "git",
                                                "--no-optional-locks",
                                                "status",
                                                "--short",
                                                "&&",
                                                "del",
                                                "x"))),
                        context));
        assertEquals(
                PolicyEngine.Decision.ASK,
                policy.evaluate(
                        command(Map.of("mode", "CMD", "argv", List.of("git status && del x"))),
                        context));
        assertEquals(
                PolicyEngine.Decision.ASK,
                policy.evaluate(
                        command(Map.of("argv", List.of("git", "reset", "--hard"))), context));
        assertEquals(
                PolicyEngine.Decision.ASK,
                policy.evaluate(command(Map.of("argv", safe, "env", Map.of())), context));
        assertEquals(
                PolicyEngine.Decision.DENY,
                policy.evaluate(command(Map.of("argv", safe, "cwd", "../outside")), context));
        assertEquals(
                PolicyEngine.Decision.DENY,
                policy.evaluate(
                        new ToolInvocation(
                                "x",
                                "read_file",
                                Map.of("path", ".env.production"),
                                SideEffectLevel.READ_ONLY),
                        context));
    }

    @Test
    void approvalPromptContainsFullCallAndSingleUserDecision() throws Exception {
        ToolInvocation call =
                command(Map.of("argv", List.of("git", "reset", "--hard"), "cwd", "project"));
        try (var approvals = new InteractiveApprovalService(Duration.ofSeconds(2));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ApprovalService.ApprovalDecision> decision =
                    executor.submit(
                            () ->
                                    approvals.decide(
                                            call, new ToolContext(scope, CancellationToken.NONE)));
            var prompt = awaitPrompt(approvals);
            assertEquals(call, prompt.call());
            assertEquals("project", prompt.target());
            assertEquals(SideEffectLevel.SYSTEM_IMPACT, prompt.call().sideEffectLevel());
            assertTrue(prompt.impact().contains("arbitrary commands"));
            assertTrue(approvals.answer(prompt.id(), false, "Keep working tree", "user"));
            assertFalse(approvals.answer(prompt.id(), true, "late", "user"));
            var rejected = decision.get(1, TimeUnit.SECONDS);
            assertFalse(rejected.approved());
            assertEquals("user", rejected.actor());
            assertEquals("Keep working tree", rejected.reason());
            assertTrue(approvals.prompts().isEmpty());
        }
    }

    @Test
    void cancellationRevokesPromptAndTimeoutAndCloseDeny() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean();
        try (var approvals = new InteractiveApprovalService(Duration.ofSeconds(2));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future =
                    executor.submit(
                            () ->
                                    approvals.decide(
                                            command(Map.of("argv", List.of("del", "x"))),
                                            new ToolContext(scope, cancel::get)));
            var prompt = awaitPrompt(approvals);
            cancel.set(true);
            var error =
                    assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertEquals("CANCELLED", ((HarnessException) error.getCause()).code());
            assertFalse(approvals.answer(prompt.id(), true, "stale", "user"));
        }
        try (var approvals = new InteractiveApprovalService(Duration.ofMillis(30))) {
            assertFalse(
                    approvals
                            .decide(
                                    command(Map.of("argv", List.of("rm", "x"))),
                                    new ToolContext(scope, CancellationToken.NONE))
                            .approved());
        }
        var closed = new InteractiveApprovalService(Duration.ofSeconds(1));
        closed.close();
        assertFalse(
                closed.decide(
                                command(Map.of("argv", List.of("rm", "x"))),
                                new ToolContext(scope, CancellationToken.NONE))
                        .approved());
    }

    static InteractiveApprovalService.Prompt awaitPrompt(InteractiveApprovalService service)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            var snapshot = service.prompts();
            if (!snapshot.isEmpty()) return snapshot.getFirst();
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No prompt before deadline");
    }
}
