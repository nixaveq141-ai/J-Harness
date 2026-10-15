package io.jharness.api;

public interface PromptComposer {
    ChatRequest compose(SessionProjection projection);
}
