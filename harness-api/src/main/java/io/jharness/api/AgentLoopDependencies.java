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
        java.util.Objects.requireNonNull(llm, "llm");
        java.util.Objects.requireNonNull(tools, "tools");
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(projection, "projection");
        java.util.Objects.requireNonNull(promptComposer, "promptComposer");
        java.util.Objects.requireNonNull(events, "events");
        java.util.Objects.requireNonNull(policy, "policy");
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        java.util.Objects.requireNonNull(budget, "budget");
    }
}
