package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R2WorkspaceAndBudgetTest {
    private Map<String, Object> response(Map<String, Object> value) {
        return Map.of("value", value);
    }

    @Test
    void canonicalAliasesShareGuardAndPatchRefreshesOnlyAffectedVersions() {
        var requests = new ArrayList<Map<String, Object>>();
        var remote =
                new RemoteWorkspace(
                        (request, token) -> {
                            requests.add(request);
                            return switch ((String) request.get("op")) {
                                case "read" -> response(Map.of("text", "old", "version", "v1"));
                                case "write" ->
                                        response(
                                                Map.of(
                                                        "path",
                                                        "a/b",
                                                        "beforeVersion",
                                                        "v1",
                                                        "afterVersion",
                                                        "v2",
                                                        "diff",
                                                        "changed"));
                                case "patch" ->
                                        response(
                                                Map.of(
                                                        "partial",
                                                        false,
                                                        "changes",
                                                        List.of(
                                                                Map.of(
                                                                        "path",
                                                                        "a/b",
                                                                        "afterVersion",
                                                                        "v3"))));
                                default -> throw new AssertionError(request);
                            };
                        });
        remote.readText(new ExecPath("a/./b"));
        remote.readText(new ExecPath("unrelated"));
        remote.write(new ExecPath("a/b"), "new", true, null, CancellationToken.NONE);
        assertEquals("v1", requests.get(2).get("expectedVersion"));
        assertEquals("a/b", requests.getFirst().get("path"));
        remote.applyPatch(
                "--- a/a/b\n+++ b/a/b\n@@ -1 +1 @@\n-old\n+new\n",
                Map.of(),
                CancellationToken.NONE);
        assertEquals(Map.of("a/b", "v2"), requests.getLast().get("versions"));
        remote.write(new ExecPath("a/b"), "newer", true, null, CancellationToken.NONE);
        assertEquals("v3", requests.getLast().get("expectedVersion"));
    }

    @Test
    void concurrentRemoteReadsCannotPublishAnOlderGuardAfterNewerWrite() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var remote =
                new RemoteWorkspace(
                        (request, token) -> {
                            if (request.get("op").equals("read")) {
                                entered.countDown();
                                try {
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException ex) {
                                    throw new AssertionError(ex);
                                }
                                return response(Map.of("text", "old", "version", "v1"));
                            }
                            assertEquals(
                                    writes.getAndIncrement() == 0 ? "v1" : "v2",
                                    request.get("expectedVersion"));
                            return response(
                                    Map.of(
                                            "path",
                                            "a",
                                            "beforeVersion",
                                            "v1",
                                            "afterVersion",
                                            "v2",
                                            "diff",
                                            "changed"));
                        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var read = executor.submit(() -> remote.readText(new ExecPath("a")));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var write =
                    executor.submit(
                            () ->
                                    remote.write(
                                            new ExecPath("a"),
                                            "new",
                                            true,
                                            null,
                                            CancellationToken.NONE));
            release.countDown();
            assertEquals("old", read.get(5, TimeUnit.SECONDS));
            write.get(5, TimeUnit.SECONDS);
            remote.write(new ExecPath("a"), "again", true, null, CancellationToken.NONE);
            assertEquals(2, writes.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void lateEpochSettlementFailsBeforeCallingDelegateAndCannotRefundCurrentTurn() {
        var fees = new SharedFeeBudget(new Budget(2, Duration.ofSeconds(5)));
        var calls = new AtomicInteger();
        var guard =
                fees.guard(
                        new ApiCostGuard() {
                            public String currency() {
                                return "USD";
                            }

                            public BigDecimal maximumCost(ChatRequest request) {
                                return BigDecimal.ONE;
                            }

                            public BigDecimal actualCost(
                                    ChatRequest request, ChatResponse response) {
                                calls.incrementAndGet();
                                return BigDecimal.ZERO;
                            }
                        });
        var request = new ChatRequest(List.of(), List.of(), Map.of());
        fees.beginTurn();
        guard.maximumCost(request);
        fees.beginTurn();
        assertEquals(
                "COST_ACCOUNTING_BREACH",
                assertThrows(HarnessException.class, () -> guard.actualCost(request, null)).code());
        assertEquals(0, calls.get());
        assertEquals(BigDecimal.ZERO, fees.reserved());
    }

    @Test
    void invalidAnswerIdentityHasOneErrorCodeBeforeHistoryLookupOrPersistence() {
        var input = new LinkedHashMap<String, Object>();
        for (String key : List.of("questionId", "sessionId", "turnId", "callId", "requestId"))
            input.put(key, null);
        input.put("selected", List.of());
        input.put("text", "answer");
        input.put("skip", false);
        var questions = new UserQuestionsPlugin(new SecretRedactor());
        assertEquals(
                "INVALID_ANSWER",
                assertThrows(HarnessException.class, () -> questions.respond(input)).code());
        assertEquals(
                "INVALID_ANSWER",
                assertThrows(
                                HarnessException.class,
                                () -> UserQuestionsPlugin.recorded(List.of(), input))
                        .code());
        var view =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "question",
                                "Question",
                                "mode",
                                "text",
                                "options",
                                List.of(),
                                "allowText",
                                true,
                                "timeoutSeconds",
                                1));
        view.putAll(
                Map.of(
                        "questionId",
                        "question",
                        "sessionId",
                        "s",
                        "turnId",
                        "t",
                        "stepId",
                        "p",
                        "callId",
                        "c",
                        "state",
                        "PENDING",
                        "expiresAt",
                        "2026-10-22T00:00:00Z"));
        var requested =
                new SessionEvent(
                        "request",
                        "s",
                        "question.requested",
                        java.time.Instant.EPOCH,
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "c",
                        view);
        var wrongStep = new LinkedHashMap<>(view);
        wrongStep.put("stepId", "other");
        assertEquals(
                "QUESTION_ASSOCIATION_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        UserQuestionsPlugin.project(
                                                List.of(
                                                        new SessionEvent(
                                                                "bad",
                                                                "s",
                                                                "question.requested",
                                                                java.time.Instant.EPOCH,
                                                                1,
                                                                EventLevel.TOOL,
                                                                "t",
                                                                "p",
                                                                "c",
                                                                wrongStep))))
                        .code());
        var changedSpec = new LinkedHashMap<>(view);
        changedSpec.put("timeoutSeconds", 2);
        changedSpec.put("state", "TIMED_OUT");
        var resolved =
                new SessionEvent(
                        "resolved",
                        "s",
                        "question.resolved",
                        java.time.Instant.EPOCH,
                        1,
                        EventLevel.TOOL,
                        "t",
                        "p",
                        "c",
                        changedSpec);
        assertEquals(
                "QUESTION_FACT_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () -> UserQuestionsPlugin.project(List.of(requested, resolved)))
                        .code());
    }
}
