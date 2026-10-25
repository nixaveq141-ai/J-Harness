package io.jharness.llm;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class R4DisplayIsolationTest {
    private LlmClient provider(boolean missing) {
        return new LlmClient() {
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, true, false, false, 4096);
            }

            public PreparedRequest prepare(ChatRequest request) {
                throw new UnsupportedOperationException();
            }

            public ChatResponse complete(ChatRequest request, CancellationToken token) {
                throw new UnsupportedOperationException();
            }

            public StreamHandle stream(ChatRequest request, CancellationToken token) {
                return new StreamHandle() {
                    public void consume(Consumer<ChatResponse> consumer) {
                        consumer.accept(new ChatResponse("draft", List.of(), Map.of()));
                    }

                    public ChatResponse finalResponse() {
                        return missing
                                ? null
                                : new ChatResponse("final answer", List.of(), Map.of());
                    }

                    public void close() {}
                };
            }

            public void close() {}
        };
    }

    @Test
    void failingDisplayCannotChangeValidatedCompletion() {
        var client =
                new StreamingLlmClient(
                                provider(false),
                                text -> {
                                    throw new IllegalStateException("display failed");
                                },
                                List.of())
                        .withDisplayUpdates(
                                update -> {
                                    throw new IllegalStateException("updates failed");
                                });
        assertEquals(
                "final answer",
                client.complete(
                                new ChatRequest(List.of(), List.of(), Map.of()),
                                CancellationToken.NONE)
                        .text());
    }

    @Test
    void missingFinalResponseHasExplicitProtocolFailure() {
        var client = new StreamingLlmClient(provider(true), text -> {}, List.of());
        var error =
                assertThrows(
                        HarnessException.class,
                        () ->
                                client.complete(
                                        new ChatRequest(List.of(), List.of(), Map.of()),
                                        CancellationToken.NONE));
        assertEquals("MODEL_BAD_RESPONSE", error.code());
    }

    @Test
    void independentObserversStillReceiveFramesWhenEarlierSinksFail() {
        var text = new ArrayList<String>();
        var updates = new ArrayList<ModelDisplay.Update>();
        var client =
                new StreamingLlmClient(
                                provider(false),
                                value -> {
                                    throw new IllegalStateException();
                                },
                                List.of())
                        .withDisplay(
                                value -> {
                                    throw new IllegalStateException();
                                })
                        .withDisplay(text::add)
                        .withDisplayUpdates(
                                value -> {
                                    throw new IllegalStateException();
                                })
                        .withDisplayUpdates(updates::add);
        assertEquals(
                "final answer",
                client.complete(
                                new ChatRequest(List.of(), List.of(), Map.of()),
                                CancellationToken.NONE)
                        .text());
        assertTrue(text.contains("final answer"));
        assertTrue(updates.stream().anyMatch(update -> update.text().equals("final answer")));
    }

    @Test
    void frozenRouteAndFutureRouteBothForwardDisplayObservers() {
        var texts = new ArrayList<String>();
        var updates = new ArrayList<ModelDisplay.Update>();
        ModelRouter router =
                new ModelRouter() {
                    public Set<String> routes() {
                        return Set.of("a", "b");
                    }

                    public LlmClient select(String route) {
                        return new StreamingLlmClient(provider(false), value -> {}, List.of());
                    }

                    public void close() {}
                };
        var frozen =
                new RoutedLlmClient(router, "a")
                        .withDisplay(texts::add)
                        .withDisplayUpdates(updates::add);
        var request = new ChatRequest(List.of(), List.of(), Map.of());
        frozen.complete(request, CancellationToken.NONE);
        assertTrue(texts.contains("final answer"));
        assertTrue(updates.stream().anyMatch(update -> update.text().equals("final answer")));
        texts.clear();
        updates.clear();
        ((ModelRouter) frozen).select("b").complete(request, CancellationToken.NONE);
        assertTrue(texts.contains("final answer"));
        assertTrue(updates.stream().anyMatch(update -> update.text().equals("final answer")));
    }
}
