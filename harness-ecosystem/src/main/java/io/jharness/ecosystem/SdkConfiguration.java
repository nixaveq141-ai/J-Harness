package io.jharness.ecosystem;

import io.jharness.api.JsonEncoding;
import io.jharness.api.Values;
import java.util.*;

/** Trusted root-agent prompt, exact tool allowlist and model parameters. */
public record SdkConfiguration(
        String prompt,
        Set<String> tools,
        Map<String, Object> parameters,
        io.jharness.api.ToolSchedulingOptions scheduling,
        io.jharness.api.ModelResilienceOptions resilience) {
    @Override
    public String toString() {
        return "SdkConfiguration[promptCharacters="
                + prompt.length()
                + ", tools="
                + (tools == null ? "host-default" : tools.size())
                + ", parameters=omitted]";
    }

    public SdkConfiguration(
            String prompt,
            Set<String> tools,
            Map<String, Object> parameters,
            io.jharness.api.ToolSchedulingOptions scheduling) {
        this(
                prompt,
                tools,
                parameters,
                scheduling,
                io.jharness.api.ModelResilienceOptions.disabled());
    }

    public SdkConfiguration(String prompt, Set<String> tools, Map<String, Object> parameters) {
        this(prompt, tools, parameters, io.jharness.api.ToolSchedulingOptions.serial());
    }

    public SdkConfiguration {
        Objects.requireNonNull(prompt);
        Objects.requireNonNull(scheduling);
        Objects.requireNonNull(resilience);
        if (prompt.length() > 32768) throw new IllegalArgumentException("Prompt limit");
        if (tools != null) {
            if (tools.size() > 256
                    || tools.stream()
                            .anyMatch(
                                    name -> name == null || !name.matches("[A-Za-z0-9_-]{1,128}")))
                throw new IllegalArgumentException("Tool allowlist limit");
            tools = Set.copyOf(tools);
        }
        JsonEncoding.encode(parameters, 65536, 8192, 24, "CONFIG_LIMIT");
        parameters = Values.snapshot(parameters);
    }
}
