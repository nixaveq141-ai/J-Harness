package io.jharness.ecosystem;

import io.jharness.api.*;

record SdkHost(
        PluginScope scope,
        SessionRepository repository,
        SessionReader reader,
        SessionWriter writer,
        ExecutionEnvironment environment,
        LlmClient model,
        ArtifactStore artifacts,
        DataSanitizer sanitizer,
        PolicyEngine policy,
        ApprovalService approvals,
        Budget budget,
        SharedFeeBudget fees) {
    SdkHost {
        java.util.Objects.requireNonNull(scope);
        java.util.Objects.requireNonNull(repository);
        java.util.Objects.requireNonNull(reader);
        java.util.Objects.requireNonNull(writer);
        java.util.Objects.requireNonNull(environment);
        java.util.Objects.requireNonNull(model);
        java.util.Objects.requireNonNull(artifacts);
        java.util.Objects.requireNonNull(sanitizer);
        java.util.Objects.requireNonNull(policy);
        java.util.Objects.requireNonNull(approvals);
        java.util.Objects.requireNonNull(budget);
        java.util.Objects.requireNonNull(fees);
    }
}
