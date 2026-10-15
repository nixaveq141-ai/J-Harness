package io.jharness.ecosystem;

import io.jharness.api.Values;
import java.util.*;

/** Trusted root-agent prompt, exact tool allowlist and model parameters. */
public record SdkConfiguration(String prompt, Set<String> tools, Map<String, Object> parameters) {
    public SdkConfiguration {
        Objects.requireNonNull(prompt);
        if (prompt.length() > 32768) throw new IllegalArgumentException("Prompt limit");
        if (tools != null) tools = Set.copyOf(tools);
        parameters = Values.snapshot(parameters);
    }
}
