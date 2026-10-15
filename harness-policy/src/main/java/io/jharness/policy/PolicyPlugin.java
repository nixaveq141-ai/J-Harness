package io.jharness.policy;

import io.jharness.api.*;
import java.util.Objects;

public final class PolicyPlugin implements HarnessPlugin {
    private final PolicyEngine policy;
    private final ApprovalService approvals;

    public PolicyPlugin(PolicyEngine policy, ApprovalService approvals) {
        this.policy = Objects.requireNonNull(policy);
        this.approvals = Objects.requireNonNull(approvals);
    }

    @Override
    public String id() {
        return "policy";
    }

    @Override
    public String version() {
        return "1.3";
    }

    @Override
    public void start(PluginContext context) {
        context.register(PolicyEngine.class, "default", policy);
        context.register(ApprovalService.class, "default", approvals);
        if (approvals instanceof AutoCloseable resource) context.own(resource);
    }

    @Override
    public void stop() {}
}
