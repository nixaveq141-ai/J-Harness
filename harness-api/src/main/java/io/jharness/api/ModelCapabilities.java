package io.jharness.api;

public record ModelCapabilities(
        boolean tools,
        boolean streaming,
        boolean images,
        boolean parallelTools,
        int maxContextTokens) {
    public ModelCapabilities {
        if (maxContextTokens <= 0) throw new IllegalArgumentException("maxContextTokens");
    }
}
