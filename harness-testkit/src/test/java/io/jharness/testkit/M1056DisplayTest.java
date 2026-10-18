package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.llm.StreamingLlmClient;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class M1056DisplayTest {
    @TempDir Path root;
    static final String SECRET = "synthetic-m1056-stream-private-key";

    static final class Provider implements LlmClient {
        final CountDownLatch frames = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        boolean fail, large;

        public ModelCapabilities capabilities() {
            return new ModelCapabilities(true, true, false, false, 65536);
        }

        public PreparedRequest prepare(ChatRequest request) {
            return new PreparedRequest(request);
        }

        public ChatResponse complete(ChatRequest request, CancellationToken cancellation) {
            throw new AssertionError("must stream");
        }

        public StreamHandle stream(ChatRequest request, CancellationToken token) {
            calls.incrementAndGet();
            return new StreamHandle() {
                boolean complete;
                String text =
                        (large
                                ? "x".repeat(70000)
                                : "Visible prefix " + "-".repeat(64) + SECRET + " tail");

                public void consume(java.util.function.Consumer<ChatResponse> sink) {
                    sink.accept(
                            new ChatResponse(
                                    "Visible prefix " + "-".repeat(64) + SECRET.substring(0, 8),
                                    List.of(),
                                    Map.of()));
                    sink.accept(new ChatResponse(text, List.of(), Map.of()));
                    frames.countDown();
                    try {
                        while (!release.await(20, TimeUnit.MILLISECONDS)) token.throwIfCancelled();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new HarnessException("CANCELLED", "cancelled");
                    }
                    token.throwIfCancelled();
                    if (fail) throw new HarnessException("INVALID_SSE", "incomplete");
                    complete = true;
                }

                public ChatResponse finalResponse() {
                    if (!complete) throw new AssertionError("not complete");
                    return new ChatResponse(
                            text,
                            List.of(),
                            Map.of("prompt_tokens", 5, "completion_tokens", 9, "total_tokens", 14));
                }

                public void close() {}
            };
        }
    }

    HarnessSdk open(Provider provider) throws Exception {
        Path workspace = Files.createDirectory(root.resolve("workspace"));
        var sanitizer = new SecretRedactor(List.of(SECRET));
        return new HarnessSdk(
                "display",
                "fixture",
                new JsonlSessionRepository(root.resolve("sessions")),
                new LocalExecutionEnvironment("local", workspace, List.of(SECRET)),
                new StreamingLlmClient(provider, text -> {}, List.of(SECRET)),
                new LocalArtifactStore(root.resolve("artifacts"), sanitizer),
                sanitizer,
                new DefaultPolicyEngine(),
                ApprovalService.rejectAll(),
                new Budget(2, Duration.ofSeconds(6)),
                (response, projection) ->
                        new CompletionEvaluator.CompletionDecision(true, "fixture"),
                ApiCostGuard.unavailable(),
                List.of());
    }

    @Test
    void liveSnapshotIsSanitizedScopedAndOnlyFinalResponseIsPersisted() throws Exception {
        var provider = new Provider();
        try (var sdk = open(provider);
                var executor = Executors.newSingleThreadExecutor()) {
            var run = executor.submit(() -> sdk.run("stream", CancellationToken.NONE));
            assertTrue(provider.frames.await(3, TimeUnit.SECONDS));
            var live = sdk.display().snapshot();
            assertEquals("STREAMING", live.get("state"));
            assertEquals("display", live.get("sessionId"));
            assertTrue(live.get("text").toString().startsWith("Visible prefix"));
            assertFalse(live.toString().contains(SECRET));
            assertTrue(
                    sdk.snapshot().events().stream()
                            .noneMatch(e -> e.type().equals("assistant.message")));
            assertFalse(sdk.snapshot().events().toString().contains("Visible prefix"));
            provider.release.countDown();
            run.get(3, TimeUnit.SECONDS);
            assertEquals("PERSISTED", sdk.display().snapshot().get("state"));
            var events = sdk.snapshot().events();
            var observation =
                    events.stream()
                            .filter(e -> e.type().equals("model.observed"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    Map.of("prompt_tokens", 5, "completion_tokens", 9, "total_tokens", 14),
                    observation.payload().get("usage"));
            assertTrue(
                    events.stream()
                            .anyMatch(
                                    e ->
                                            e.eventId()
                                                            .equals(
                                                                    observation
                                                                            .payload()
                                                                            .get("sourceEvent"))
                                                    && e.type().equals("model.requested")));
            assertEquals(
                    1, events.stream().filter(e -> e.type().equals("assistant.message")).count());
            assertFalse(events.toString().contains(SECRET));
            assertNotNull(sdk.content());
        }
    }

    @Test
    void brokenStreamKeepsPartialDisplayButNeverAssistantFactOrRetry() throws Exception {
        var provider = new Provider();
        provider.fail = true;
        try (var sdk = open(provider);
                var executor = Executors.newSingleThreadExecutor()) {
            var run = executor.submit(() -> sdk.run("stream", CancellationToken.NONE));
            assertTrue(provider.frames.await(3, TimeUnit.SECONDS));
            provider.release.countDown();
            run.get(3, TimeUnit.SECONDS);
            assertEquals("INTERRUPTED", sdk.display().snapshot().get("state"));
            assertEquals(1, provider.calls.get());
            assertTrue(
                    sdk.snapshot().events().stream()
                            .noneMatch(e -> e.type().equals("assistant.message")));
            assertFalse(SessionRecovery.inspect(sdk.snapshot().events()).ready());
        }
    }

    @Test
    void cancellationStopsActiveWaitAndDoesNotCreateAnswer() throws Exception {
        var provider = new Provider();
        try (var sdk = open(provider);
                var executor = Executors.newSingleThreadExecutor()) {
            var run = executor.submit(() -> sdk.run("stream", CancellationToken.NONE));
            assertTrue(provider.frames.await(3, TimeUnit.SECONDS));
            sdk.cancel();
            run.get(3, TimeUnit.SECONDS);
            assertEquals(1, provider.calls.get());
            assertEquals("INTERRUPTED", sdk.display().snapshot().get("state"));
            assertTrue(
                    sdk.snapshot().events().stream()
                            .noneMatch(e -> e.type().equals("assistant.message")));
        }
    }

    @Test
    void temporaryDisplayHasHardBoundAndNewSdkDoesNotRecoverLostBytes() throws Exception {
        var provider = new Provider();
        provider.large = true;
        try (var sdk = open(provider);
                var executor = Executors.newSingleThreadExecutor()) {
            var run = executor.submit(() -> sdk.run("stream", CancellationToken.NONE));
            assertTrue(provider.frames.await(3, TimeUnit.SECONDS));
            assertEquals(65536, sdk.display().snapshot().get("text").toString().length());
            assertEquals(true, sdk.display().snapshot().get("truncated"));
            provider.release.countDown();
            run.get(3, TimeUnit.SECONDS);
        }
        var repository = new JsonlSessionRepository(root.resolve("sessions"));
        try (var sdk =
                new HarnessSdk(
                        "display",
                        "fixture",
                        repository,
                        new LocalExecutionEnvironment(
                                "local", root.resolve("workspace"), List.of(SECRET)),
                        new StreamingLlmClient(new Provider(), text -> {}, List.of(SECRET)),
                        new LocalArtifactStore(
                                root.resolve("artifacts"), new SecretRedactor(List.of(SECRET))),
                        new SecretRedactor(List.of(SECRET)),
                        new DefaultPolicyEngine(),
                        ApprovalService.rejectAll(),
                        new Budget(2, Duration.ofSeconds(6)),
                        (r, p) -> new CompletionEvaluator.CompletionDecision(true, "fixture"),
                        ApiCostGuard.unavailable(),
                        List.of())) {
            assertEquals("", sdk.display().snapshot().get("text"));
            assertEquals("IDLE", sdk.display().snapshot().get("state"));
        }
    }
}
