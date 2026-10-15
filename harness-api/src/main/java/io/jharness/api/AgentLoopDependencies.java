package io.jharness.api;

public record AgentLoopDependencies(
        LlmClient llm,
        ToolRegistry tools,
        SessionWriter session,
        SessionProjection projection,
        PromptComposer promptComposer,
        EventBus events,
        PolicyEngine policy,
        CancellationToken cancellation,
        Budget budget) {
    public AgentLoopDependencies {
        java.util.Objects.requireNonNull(llm);
        java.util.Objects.requireNonNull(tools);
        java.util.Objects.requireNonNull(session);
        java.util.Objects.requireNonNull(projection);
        java.util.Objects.requireNonNull(promptComposer);
        java.util.Objects.requireNonNull(events);
        java.util.Objects.requireNonNull(policy);
        java.util.Objects.requireNonNull(cancellation);
        java.util.Objects.requireNonNull(budget);
    }
}
