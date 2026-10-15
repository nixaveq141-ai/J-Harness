package io.jharness.ecosystem;

import io.jharness.api.Values;
import java.util.*;

record SdkOptions(
        String agentId,
        String prompt,
        Set<String> tools,
        Map<String, Object> origin,
        SharedFeeBudget fees,
        Map<String, Object> parameters) {
    SdkOptions(
            String agentId,
            String prompt,
            Set<String> tools,
            Map<String, Object> origin,
            SharedFeeBudget fees) {
        this(agentId, prompt, tools, origin, fees, Map.of());
    }

    SdkOptions {
        origin = Values.snapshot(origin);
        parameters = Values.snapshot(parameters);
        if (tools != null) tools = Set.copyOf(tools);
    }

    static SdkOptions root() {
        return new SdkOptions("default", "", null, Map.of(), null);
    }
}
