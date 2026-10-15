package io.jharness.api;

/** Task success must be supplied by goal-specific evidence, never by a model's assertion alone. */
@FunctionalInterface
public interface CompletionEvaluator {
    CompletionDecision evaluate(ChatResponse response, SessionProjection projection);

    record CompletionDecision(boolean achieved, String reason) {
        public CompletionDecision {
            reason = Values.required(reason, "reason");
        }
    }
}
