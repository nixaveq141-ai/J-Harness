package io.jharness.api;

/** Decisions are nonnull; inability to decide must fail closed with an exception. */
public interface PolicyEngine {
    Decision evaluate(ToolInvocation call, ToolContext context);

    enum Decision {
        ALLOW,
        ASK,
        DENY
    }
}
