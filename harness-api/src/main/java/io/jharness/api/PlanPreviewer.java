package io.jharness.api;

import java.util.Map;

@FunctionalInterface
public interface PlanPreviewer {
    Map<String, Object> preview(ToolInvocation call, CancellationToken cancellation);
}
