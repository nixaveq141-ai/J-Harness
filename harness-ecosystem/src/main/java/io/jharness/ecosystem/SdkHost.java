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
        SharedFeeBudget fees) {}
