package io.jharness.api;

/** Policy composition seam. Scope and durable intent guards remain mandatory in the pipeline. */
public final class PolicyExtensionPoints {
    public static final ExtensionPoint<PolicyEngine> EVALUATE =
            new ExtensionPoint<>("policy.evaluate", PolicyEngine.class);

    private PolicyExtensionPoints() {}
}
