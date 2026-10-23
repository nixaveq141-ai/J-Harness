package io.jharness.llm;

import io.jharness.api.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Secrets are kept outside ChatRequest, Session events and toString. */
public final class OpenAiCompatibleConfig {
    final URI endpoint;
    final String model;
    final String apiKey;
    final Duration timeout;
    final int maxResponseBytes;
    final int rateLimitRetries;
    final ModelCapabilities capabilities;
    final ModelProtocol protocol;

    public OpenAiCompatibleConfig(
            URI baseUrl,
            String model,
            String apiKey,
            Duration timeout,
            int maxResponseBytes,
            int rateLimitRetries,
            ModelCapabilities capabilities) {
        this(
                baseUrl,
                model,
                apiKey,
                timeout,
                maxResponseBytes,
                rateLimitRetries,
                capabilities,
                ModelProtocol.CHAT_COMPLETIONS);
    }

    public OpenAiCompatibleConfig(
            URI baseUrl,
            String model,
            String apiKey,
            Duration timeout,
            int maxResponseBytes,
            int rateLimitRetries,
            ModelCapabilities capabilities,
            ModelProtocol protocol) {
        Objects.requireNonNull(baseUrl);
        String host = baseUrl.getHost();
        boolean loopback =
                "localhost".equalsIgnoreCase(host)
                        || "127.0.0.1".equals(host)
                        || "[::1]".equals(host);
        if (host == null
                || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null
                || !("https".equalsIgnoreCase(baseUrl.getScheme())
                        || "http".equalsIgnoreCase(baseUrl.getScheme()) && loopback))
            throw new IllegalArgumentException(
                    "Use HTTPS or loopback HTTP without URL credentials/query/fragment");
        this.model = Values.required(model, "model");
        if (model.length() > 128
                || model.chars()
                        .anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)))
            throw new IllegalArgumentException("Invalid model name");
        this.protocol = Objects.requireNonNull(protocol);
        this.apiKey = Values.required(apiKey, "apiKey");
        if (apiKey.length() > 8192 || apiKey.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Invalid API key");
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || maxResponseBytes < 128
                || maxResponseBytes > 16 * 1024 * 1024
                || rateLimitRetries < 0
                || rateLimitRetries > 3) throw new IllegalArgumentException("Invalid HTTP limits");
        this.endpoint = URI.create(baseUrl.toString().replaceAll("/+$", "") + protocol.path);
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        this.rateLimitRetries = rateLimitRetries;
        Objects.requireNonNull(capabilities);
        this.capabilities =
                new ModelCapabilities(
                        capabilities.tools(),
                        capabilities.streaming(),
                        false,
                        false,
                        capabilities.maxContextTokens());
    }

    public ModelCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return "OpenAiCompatibleConfig[model="
                + new SecretRedactor(List.of(apiKey)).text(model)
                + ", apiKey=[REDACTED]]";
    }
}
