package io.jharness.api;

import java.util.Map;

public record ToolInvocation(
        String callId,
        String tool,
        Map<String, Object> arguments,
        SideEffectLevel sideEffectLevel) {
    public ToolInvocation {
        callId = Values.required(callId, "callId");
        tool = Values.required(tool, "tool");
        arguments = Values.snapshot(arguments);
        java.util.Objects.requireNonNull(sideEffectLevel, "sideEffectLevel");
    }

    @Override
    public String toString() {
        return "ToolInvocation[arguments="
                + arguments.size()
                + ", sideEffectLevel="
                + sideEffectLevel
                + "]";
    }
}
