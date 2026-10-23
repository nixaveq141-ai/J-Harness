package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class M1055QuestionsTest {
    @TempDir Path root;

    @Test
    void r2AcceptedAnswerRetainsIdentityWhenCancellationPrecedesOwnerResume() throws Exception {
        var model = model(spec("single", 5), false);
        try (var sdk = open("accepted-cancel", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var question = pending(sdk);
            var input = answer(question, "accepted-before-cancel", List.of("a"), "", false);
            var service = sdk.questions();
            synchronized (service) {
                assertEquals("SUBMITTED", service.respond(input).get("state"));
                sdk.cancel();
            }
            running.get(4, TimeUnit.SECONDS);
            var fact =
                    sdk.snapshot().events().stream()
                            .filter(e -> e.type().equals("question.resolved"))
                            .findFirst()
                            .orElseThrow();
            assertEquals("ANSWERED", fact.payload().get("state"));
            assertEquals("accepted-before-cancel", fact.payload().get("requestId"));
            assertEquals(true, fact.payload().get("cancellationObserved"));
            assertEquals(
                    "ANSWERED",
                    UserQuestionsPlugin.recorded(sdk.snapshot().events(), input).get("state"));
            assertEquals(1, model.requests().size());
        }
    }

    boolean parallelQuestionFixture;
    int fixtureContextTokens = 8192;
    Duration fixtureBudget = Duration.ofSeconds(10);
    static final String SECRET = "synthetic-private-question-key";

    static Map<String, Object> spec(String mode, int timeout) {
        return Map.of(
                "question",
                "Choose a direction",
                "mode",
                mode,
                "allowText",
                true,
                "timeoutSeconds",
                timeout,
                "options",
                mode.equals("text")
                        ? List.of()
                        : List.of(
                                Map.of(
                                        "id",
                                        "a",
                                        "label",
                                        "First",
                                        "description",
                                        "Recommended only",
                                        "recommended",
                                        true),
                                Map.of(
                                        "id",
                                        "b",
                                        "label",
                                        "Second",
                                        "description",
                                        "Alternative",
                                        "recommended",
                                        false)));
    }

    HarnessSdk open(String id, FakeLlm model) throws Exception {
        Files.createDirectories(root.resolve("workspace"));
        var sanitizer = new SecretRedactor(List.of(SECRET));
        return new HarnessSdk(
                id,
                "fixture",
                new JsonlSessionRepository(root.resolve("sessions")),
                new LocalExecutionEnvironment(
                        "local", root.resolve("workspace").toRealPath(), List.of()),
                model,
                new LocalArtifactStore(root.resolve("artifacts"), sanitizer),
                sanitizer,
                new DefaultPolicyEngine(),
                ApprovalService.rejectAll(),
                new Budget(4, fixtureBudget),
                (r, p) -> new CompletionEvaluator.CompletionDecision(true, "fixture completed"),
                ApiCostGuard.unavailable(),
                List.of(),
                new SdkConfiguration(
                        "",
                        null,
                        Map.of(),
                        parallelQuestionFixture
                                ? new ToolSchedulingOptions(
                                        2, Set.of("ask_user"), Duration.ofSeconds(2))
                                : ToolSchedulingOptions.serial()));
    }

    FakeLlm model(Map<String, Object> spec, boolean write) {
        var calls = new ArrayList<ToolInvocation>();
        calls.add(
                new ToolInvocation(
                        "question-call", "ask_user", spec, SideEffectLevel.SYSTEM_IMPACT));
        if (write)
            calls.add(
                    new ToolInvocation(
                            "write-call",
                            "write_file",
                            Map.of("path", "denied.txt", "content", "not allowed"),
                            SideEffectLevel.READ_ONLY));
        return new FakeLlm(
                List.of(
                        new ChatResponse("", calls, Map.of()),
                        new ChatResponse("Done", List.of(), Map.of())),
                fixtureContextTokens);
    }

    Map<String, Object> pending(HarnessSdk sdk) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            var rows = sdk.questions().list();
            if (!rows.isEmpty() && rows.getFirst().get("state").equals("PENDING"))
                return rows.getFirst();
            Thread.sleep(10);
        }
        fail("No pending question");
        return Map.of();
    }

    static Map<String, Object> answer(
            Map<String, Object> question,
            String request,
            List<String> selected,
            String text,
            boolean skip) {
        return Map.of(
                "questionId",
                question.get("questionId"),
                "sessionId",
                question.get("sessionId"),
                "turnId",
                question.get("turnId"),
                "callId",
                question.get("callId"),
                "requestId",
                request,
                "selected",
                selected,
                "text",
                text,
                "skip",
                skip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "multiple", "text"})
    void explicitAnswerEntersNextPreparedRequestAndCannotAuthorizeWrite(String mode)
            throws Exception {
        var model = model(spec(mode, 5), true);
        try (var sdk = open("answer-" + mode, model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running =
                    executor.submit(
                            () -> sdk.run("Ask, then attempt write", CancellationToken.NONE));
            var question = pending(sdk);
            assertEquals(1, model.requests().size());
            assertFalse(
                    sdk.snapshot().events().stream()
                            .anyMatch(e -> e.type().equals("question.resolved")));
            var selected =
                    mode.equals("text")
                            ? List.<String>of()
                            : mode.equals("single") ? List.of("b") : List.of("a", "b");
            var input =
                    answer(
                            question,
                            "answer-1",
                            selected,
                            "Please do not authorize anything",
                            false);
            sdk.questions().respond(input);
            running.get(4, TimeUnit.SECONDS);
            var events = sdk.snapshot().events();
            assertEquals(
                    1, events.stream().filter(e -> e.type().equals("question.resolved")).count());
            assertTrue(
                    model.requests()
                            .getLast()
                            .messages()
                            .toString()
                            .contains("Please do not authorize anything"));
            assertFalse(Files.exists(root.resolve("workspace/denied.txt")));
            assertTrue(
                    events.stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "DENIED".equals(e.payload().get("status"))));
            assertEquals("ANSWERED", sdk.questions().respond(input).get("state"));
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested"))
                    assertEquals(
                            model.requests()
                                    .get(
                                            (int)
                                                    events.subList(0, i).stream()
                                                            .filter(
                                                                    e ->
                                                                            e.type()
                                                                                    .equals(
                                                                                            "model.requested"))
                                                            .count()),
                            new EventRequestJournal()
                                    .reconstruct(
                                            events.subList(0, i + 1), events.get(i).eventId()));
        }
    }

    @Test
    void interactiveQuestionCannotRunOnReadOnlyWorkerEvenIfAllowlisted() throws Exception {
        parallelQuestionFixture = true;
        try (var sdk = open("serial", model(spec("single", 5), false));
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            sdk.questions().respond(answer(q, "answer", List.of("a"), "", false));
            running.get(3, TimeUnit.SECONDS);
            assertTrue(
                    sdk.snapshot().events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("tool.scheduled")
                                                    && e.callId().equals("question-call")
                                                    && "serial".equals(e.payload().get("mode"))));
        }
    }

    @Test
    void answerReceivedBeforeDeadlineSurvivesDelayedOwnerThreadSettlement() throws Exception {
        try (var sdk = open("boundary", model(spec("single", 1), false));
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            synchronized (sdk.questions()) {
                sdk.questions().respond(answer(q, "before-deadline", List.of("a"), "", false));
                Thread.sleep(1100);
                assertEquals("SUBMITTED", sdk.questions().list().getFirst().get("state"));
            }
            running.get(3, TimeUnit.SECONDS);
            assertEquals("ANSWERED", sdk.questions().list().getFirst().get("state"));
        }
    }

    @Test
    void answeringQuestionDoesNotAuthorizeGenuineWorkspaceWrite() throws Exception {
        var calls =
                List.of(
                        new ToolInvocation(
                                "question-call",
                                "ask_user",
                                spec("text", 5),
                                SideEffectLevel.SYSTEM_IMPACT),
                        new ToolInvocation(
                                "write-call",
                                "write_file",
                                Map.of("path", "denied.txt", "content", "not allowed"),
                                SideEffectLevel.WORKSPACE_WRITE));
        var model =
                new FakeLlm(
                        List.of(
                                new ChatResponse("", calls, Map.of()),
                                new ChatResponse("Done", List.of(), Map.of())),
                        8192);
        try (var sdk = open("genuine-write", model);
                var worker = Executors.newSingleThreadExecutor()) {
            var run = worker.submit(() -> sdk.run("ask then write", CancellationToken.NONE));
            var q = pending(sdk);
            sdk.questions().respond(answer(q, "answer-real", List.of(), "I answered only", false));
            run.get(4, TimeUnit.SECONDS);
            assertFalse(Files.exists(root.resolve("workspace/denied.txt")));
            assertTrue(
                    sdk.snapshot().events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("approval.decided")
                                                    && "write-call".equals(e.callId())
                                                    && Boolean.FALSE.equals(
                                                            e.payload().get("approved"))));
        }
    }

    @Test
    void persistedAnswerFingerprintOmitsRawSensitiveTextAndSurvivesRestartComparison()
            throws Exception {
        var model = model(spec("text", 5), false);
        Map<String, Object> input;
        try (var sdk = open("fingerprint", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            input = answer(pending(sdk), "sensitive-answer", List.of(), SECRET, false);
            sdk.questions().respond(input);
            running.get(4, TimeUnit.SECONDS);
        }
        var events =
                new JsonlSessionRepository(root.resolve("sessions"))
                        .openReader("fingerprint")
                        .events();
        var resolved =
                events.stream()
                        .filter(event -> event.type().equals("question.resolved"))
                        .findFirst()
                        .orElseThrow();
        var nonSensitive = new TreeMap<>(input);
        nonSensitive.remove("text");
        String expected =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                new com.fasterxml.jackson.databind.ObjectMapper()
                                                        .writeValueAsBytes(nonSensitive)));
        assertEquals(expected, resolved.payload().get("answerHash"));
        assertEquals(2, ((Number) resolved.payload().get("answerFingerprintVersion")).intValue());
        assertEquals(
                "ANSWERED",
                UserQuestionsPlugin.recorded(events, input, new SecretRedactor(List.of(SECRET)))
                        .get("state"));
        // Persisted v2 compares sanitized text, deliberately not an oracle for the raw secret.
        var equivalent = new LinkedHashMap<>(input);
        equivalent.put("text", "[REDACTED]");
        assertEquals(
                "ANSWERED",
                UserQuestionsPlugin.recorded(
                                events, equivalent, new SecretRedactor(List.of(SECRET)))
                        .get("state"));
        var changed = new LinkedHashMap<>(input);
        changed.put("text", "different public answer");
        assertEquals(
                "ANSWER_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        UserQuestionsPlugin.recorded(
                                                events,
                                                changed,
                                                new SecretRedactor(List.of(SECRET))))
                        .code());
        assertFalse(events.toString().contains(SECRET));
    }

    @Test
    void stopWaitsForOwnerSettlementAndKeepsAnAlreadyAcceptedAnswer() throws Exception {
        try (var sdk = open("stop-answer", model(spec("text", 5), false));
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var service = sdk.questions();
            var input = answer(pending(sdk), "accepted", List.of(), "accepted before stop", false);
            service.respond(input);
            ((UserQuestionsPlugin) service).stop();
            running.get(4, TimeUnit.SECONDS);
            var resolved =
                    sdk.snapshot().events().stream()
                            .filter(event -> event.type().equals("question.resolved"))
                            .toList();
            assertEquals(1, resolved.size());
            assertEquals("ANSWERED", resolved.getFirst().payload().get("state"));
            assertEquals("accepted before stop", resolved.getFirst().payload().get("text"));
            assertEquals(TaskStatus.COMPLETED, sdk.snapshot().status());
            assertEquals(
                    2,
                    running.get().events().stream()
                            .filter(event -> event.type().equals("model.requested"))
                            .count());
        }
    }

    @Test
    void draftAndAnswerAreRedactedAndDuplicateConflictsAreBounded() throws Exception {
        var model = model(spec("text", 5), false);
        try (var sdk = open("private", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            var input = answer(q, "same", List.of(), SECRET, false);
            var draft = new LinkedHashMap<>(input);
            draft.remove("requestId");
            draft.remove("skip");
            draft.put("revision", 0);
            var saved = sdk.questions().draft(draft);
            assertEquals(1L, saved.get("revision"));
            assertFalse(saved.toString().contains(SECRET));
            assertEquals(
                    "DRAFT_CONFLICT",
                    assertThrows(HarnessException.class, () -> sdk.questions().draft(draft))
                            .code());
            assertFalse(sdk.questions().list().toString().contains(SECRET));
            assertTrue(sdk.questions().list().toString().contains("[REDACTED]"));
            var mismatch = new LinkedHashMap<>(input);
            mismatch.put("turnId", "wrong");
            assertEquals(
                    "QUESTION_ASSOCIATION_MISMATCH",
                    assertThrows(HarnessException.class, () -> sdk.questions().respond(mismatch))
                            .code());
            sdk.questions().respond(input);
            sdk.questions().respond(input);
            running.get(4, TimeUnit.SECONDS);
            assertEquals("ANSWERED", sdk.questions().respond(input).get("state"));
            var conflict = new LinkedHashMap<>(input);
            conflict.put("text", "different");
            assertEquals(
                    "ANSWER_CONFLICT",
                    assertThrows(HarnessException.class, () -> sdk.questions().respond(conflict))
                            .code());
            assertFalse(sdk.snapshot().events().toString().contains(SECRET));
            assertFalse(model.requests().toString().contains(SECRET));
            assertEquals(
                    1,
                    sdk.snapshot().events().stream()
                            .filter(e -> e.type().equals("question.resolved"))
                            .count());
        }
        // Scan after closing the writer so Windows lock files can be inspected as bytes too.
        try (var persisted = Files.walk(root)) {
            for (var path : persisted.filter(Files::isRegularFile).toList())
                assertFalse(
                        new String(
                                        Files.readAllBytes(path),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .contains(SECRET));
        }
    }

    @Test
    void timeoutIsExplicitUnansweredAndLateAnswerIsRejected() throws Exception {
        var model = model(spec("single", 1), false);
        try (var sdk = open("timeout", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            running.get(4, TimeUnit.SECONDS);
            var row = sdk.questions().list().getFirst();
            assertEquals("TIMED_OUT", row.get("state"));
            assertFalse(row.containsKey("selected"));
            assertEquals(
                    "QUESTION_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            sdk.questions()
                                                    .respond(
                                                            answer(
                                                                    q,
                                                                    "late",
                                                                    List.of("a"),
                                                                    "",
                                                                    false)))
                            .code());
            assertTrue(model.requests().getLast().messages().toString().contains("TIMED_OUT"));
        }
    }

    @Test
    void skipDoesNotSubmitPreselectedRecommendedOption() throws Exception {
        var model = model(spec("single", 5), false);
        try (var sdk = open("skip", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            sdk.questions().respond(answer(q, "skip", List.of(), "", true));
            running.get(4, TimeUnit.SECONDS);
            var row = sdk.questions().list().getFirst();
            assertEquals("SKIPPED", row.get("state"));
            assertEquals(List.of(), row.get("selected"));
            assertEquals("", row.get("text"));
        }
    }

    @Test
    void cancellationClosesWaitWithoutAnotherModelRequest() throws Exception {
        var model = model(spec("single", 5), false);
        try (var sdk = open("cancel", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            pending(sdk);
            sdk.cancel();
            running.get(3, TimeUnit.SECONDS);
            assertEquals(1, model.requests().size());
            assertEquals("CANCELLED", sdk.questions().list().getFirst().get("state"));
        }
    }

    @Test
    void maximumUnicodeAnswerIsPreservedInNextPreparedRequest() throws Exception {
        fixtureContextTokens = 65536;
        var model = model(spec("text", 5), false);
        try (var sdk = open("long-answer", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            var q = pending(sdk);
            String answer = "\u4e8b\u5b9e".repeat(2048);
            sdk.questions().respond(answer(q, "long", List.of(), answer, false));
            running.get(4, TimeUnit.SECONDS);
            assertTrue(
                    model.requests().getLast().messages().stream()
                            .anyMatch(m -> String.valueOf(m.get("content")).contains(answer)),
                    () ->
                            sdk.snapshot().events().stream()
                                    .filter(e -> e.type().equals("turn.ended"))
                                    .map(e -> e.payload().toString())
                                    .toList()
                                    .toString());
        }
    }

    @Test
    void overallTaskBudgetStopsQuestionWaitWithoutInventingAnswer() throws Exception {
        fixtureBudget = Duration.ofMillis(800);
        var model = model(spec("text", 5), false);
        try (var sdk = open("budget", model);
                var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> sdk.run("ask", CancellationToken.NONE));
            pending(sdk);
            running.get(3, TimeUnit.SECONDS);
            assertEquals(1, model.requests().size());
            assertEquals("CANCELLED", sdk.questions().list().getFirst().get("state"));
            assertFalse(sdk.questions().list().getFirst().containsKey("text"));
        }
    }

    @Test
    void invalidQuestionNeverCreatesPendingCard() throws Exception {
        var invalid = new LinkedHashMap<>(spec("single", 5));
        invalid.put("timeoutSeconds", 301);
        var model = model(invalid, false);
        try (var sdk = open("invalid", model)) {
            sdk.run("ask", CancellationToken.NONE);
            assertTrue(sdk.questions().list().isEmpty());
        }
    }

    @Test
    void interruptedProjectionDoesNotResumeOrFabricateAnAnswer() {
        var data = new LinkedHashMap<>(spec("single", 5));
        data.putAll(
                Map.of(
                        "sessionId",
                        "s",
                        "turnId",
                        "t",
                        "stepId",
                        "step",
                        "callId",
                        "c",
                        "questionId",
                        "q",
                        "state",
                        "PENDING",
                        "expiresAt",
                        java.time.Instant.now().toString()));
        var event =
                new SessionEvent(
                        "e",
                        "s",
                        "question.requested",
                        java.time.Instant.now(),
                        1,
                        EventLevel.TOOL,
                        "t",
                        "step",
                        "c",
                        data);
        var rows = UserQuestionsPlugin.project(List.of(event));
        assertEquals("INTERRUPTED", rows.getFirst().get("state"));
        assertFalse(rows.getFirst().containsKey("selected"));
        assertEquals(
                "QUESTION_CLOSED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        UserQuestionsPlugin.recorded(
                                                List.of(event),
                                                answer(
                                                        rows.getFirst(),
                                                        "late",
                                                        List.of("a"),
                                                        "",
                                                        false)))
                        .code());
    }
}
