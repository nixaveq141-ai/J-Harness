package io.jharness.api;

public interface PolicyEngine {
    Decision evaluate(ToolInvocation call, ToolContext context);

    enum Decision {
        ALLOW,
        ASK,
        DENY
    }
}
