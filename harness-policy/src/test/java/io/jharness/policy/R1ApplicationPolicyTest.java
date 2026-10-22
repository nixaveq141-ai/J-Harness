package io.jharness.policy;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class R1ApplicationPolicyTest {
    ToolContext context() {
        return new ToolContext(new PluginScope("test", "test"), CancellationToken.NONE);
    }

    @Test
    void grepChecksEveryDeclaredPathRatherThanOnlyCwd() {
        var p = new DefaultPolicyEngine();
        assertEquals(
                PolicyEngine.Decision.DENY,
                p.evaluate(
                        new ToolInvocation(
                                "g",
                                "grep",
                                Map.of("paths", List.of("src", ".env.local")),
                                SideEffectLevel.READ_ONLY),
                        context()));
        assertEquals(
                PolicyEngine.Decision.ALLOW,
                p.evaluate(
                        new ToolInvocation(
                                "g",
                                "grep",
                                Map.of("paths", List.of("src", "README.md")),
                                SideEffectLevel.READ_ONLY),
                        context()));
    }

    @Test
    void windowsMetadataAliasesAndParentPathsAreDenied() {
        var p = new DefaultPolicyEngine();
        for (String s : List.of(".git./config", ".jharness /config", "../a", "/a", "C:/a"))
            assertEquals(
                    PolicyEngine.Decision.DENY,
                    p.evaluate(
                            new ToolInvocation(
                                    "r", "read_file", Map.of("path", s), SideEffectLevel.READ_ONLY),
                            context()),
                    s);
    }

    @Test
    void nullAndBlankAnswerAreSimplyRejected() {
        try (var s = new InteractiveApprovalService(Duration.ofSeconds(1))) {
            assertFalse(s.answer(null, true, "r", "actor"));
            assertFalse(s.answer("", true, "r", "actor"));
        }
    }

    @SuppressWarnings("try")
    @Test
    void closingImmediatelyRevokesPromptsAndCannotAcceptNewAnswers() throws Exception {
        try (var s = new InteractiveApprovalService(Duration.ofSeconds(2));
                var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var task =
                    worker.submit(
                            () ->
                                    s.decide(
                                            new ToolInvocation(
                                                    "id",
                                                    "terminal_start",
                                                    Map.of("cwd", "."),
                                                    SideEffectLevel.SYSTEM_IMPACT),
                                            context()));
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            List<InteractiveApprovalService.Prompt> snapshot;
            do {
                snapshot = s.prompts();
                Thread.sleep(1);
            } while (snapshot.isEmpty() && System.nanoTime() < end);
            assertEquals(1, snapshot.size());
            var prompt = snapshot.getFirst();
            assertTrue(prompt.impact().contains("arbitrary commands"));
            s.close();
            assertTrue(s.prompts().isEmpty());
            assertFalse(s.answer(prompt.id(), true, "late", "actor"));
            var decision = task.get(1, TimeUnit.SECONDS);
            assertFalse(decision.approved());
            assertEquals("system", decision.actor());
            assertEquals("Approval interface closed", decision.reason());
        }
    }

    @Test
    void concurrentAnswersHaveExactlyOneWinnerAndKeepItsDecision() throws Exception {
        try (var s = new InteractiveApprovalService(Duration.ofSeconds(4));
                var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var result =
                    worker.submit(
                            () ->
                                    s.decide(
                                            new ToolInvocation(
                                                    "id",
                                                    "write_file",
                                                    Map.of("path", "a"),
                                                    SideEffectLevel.WORKSPACE_WRITE),
                                            context()));
            var p = PolicyApprovalTest.awaitPrompt(s);
            var gate = new CyclicBarrier(3);
            var a =
                    worker.submit(
                            () -> {
                                gate.await();
                                return s.answer(p.id(), true, "allow", "a");
                            });
            var b =
                    worker.submit(
                            () -> {
                                gate.await();
                                return s.answer(p.id(), false, "deny", "b");
                            });
            gate.await();
            boolean first = a.get(1, TimeUnit.SECONDS), second = b.get(1, TimeUnit.SECONDS);
            assertNotEquals(first, second);
            var decision = result.get(1, TimeUnit.SECONDS);
            assertEquals(first, decision.approved());
            assertEquals(first ? "a" : "b", decision.actor());
            assertTrue(s.prompts().isEmpty());
        }
    }

    @Test
    void expiredPromptHasPreciseReasonAndIsWithdrawn() {
        try (var s = new InteractiveApprovalService(Duration.ofMillis(10))) {
            var decision =
                    s.decide(
                            new ToolInvocation(
                                    "id",
                                    "write_file",
                                    Map.of("path", "a"),
                                    SideEffectLevel.WORKSPACE_WRITE),
                            context());
            assertFalse(decision.approved());
            assertEquals("Approval timed out", decision.reason());
            assertEquals("system", decision.actor());
            assertTrue(s.prompts().isEmpty());
        }
    }

    @SuppressWarnings("try")
    @Test
    void concurrentAnswersAndCloseNeverLeaveAnApprovedPromptAfterRevocation() throws Exception {
        for (int round = 0; round < 20; round++) {
            try (var s = new InteractiveApprovalService(Duration.ofSeconds(4));
                    var worker = Executors.newVirtualThreadPerTaskExecutor()) {
                var result =
                        worker.submit(
                                () ->
                                        s.decide(
                                                new ToolInvocation(
                                                        "id",
                                                        "write_file",
                                                        Map.of("path", "a"),
                                                        SideEffectLevel.WORKSPACE_WRITE),
                                                context()));
                var prompt = PolicyApprovalTest.awaitPrompt(s);
                var gate = new CyclicBarrier(4);
                var allow =
                        worker.submit(
                                () -> {
                                    gate.await();
                                    return s.answer(prompt.id(), true, "allow", "allow-actor");
                                });
                var deny =
                        worker.submit(
                                () -> {
                                    gate.await();
                                    return s.answer(prompt.id(), false, "deny", "deny-actor");
                                });
                var close =
                        worker.submit(
                                () -> {
                                    gate.await();
                                    s.close();
                                    return true;
                                });
                gate.await();
                boolean allowed = allow.get(1, TimeUnit.SECONDS),
                        denied = deny.get(1, TimeUnit.SECONDS);
                close.get(1, TimeUnit.SECONDS);
                assertFalse(allowed && denied);
                var decision = result.get(1, TimeUnit.SECONDS);
                if (decision.approved()) {
                    assertTrue(allowed);
                    assertEquals("allow-actor", decision.actor());
                } else if ("deny-actor".equals(decision.actor())) {
                    assertTrue(denied);
                    assertEquals("deny", decision.reason());
                } else {
                    assertEquals("system", decision.actor());
                    assertEquals("Approval interface closed", decision.reason());
                }
                assertTrue(s.prompts().isEmpty());
                assertFalse(s.answer(prompt.id(), true, "late", "late"));
            }
        }
    }
}
