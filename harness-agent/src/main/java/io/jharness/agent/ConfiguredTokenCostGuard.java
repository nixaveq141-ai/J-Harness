package io.jharness.agent;

import io.jharness.api.*;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Explicit current model pricing, no provider lookup. Charge the conservative bound without
 * refunds.
 */
public final class ConfiguredTokenCostGuard implements ApiCostGuard {
    private final String model;
    private final int inputCeiling, outputCeiling;
    private final BigDecimal inputUsdPerToken, outputUsdPerToken;
    private final String currency;

    public ConfiguredTokenCostGuard(
            String model,
            int inputCeiling,
            int outputCeiling,
            BigDecimal inputUsdPerToken,
            BigDecimal outputUsdPerToken) {
        this(model, inputCeiling, outputCeiling, inputUsdPerToken, outputUsdPerToken, "USD");
    }

    public ConfiguredTokenCostGuard(
            String model,
            int inputCeiling,
            int outputCeiling,
            BigDecimal inputUsdPerToken,
            BigDecimal outputUsdPerToken,
            String currency) {
        this.model = Values.required(model, "model");
        this.inputCeiling = inputCeiling;
        this.outputCeiling = outputCeiling;
        this.inputUsdPerToken = Objects.requireNonNull(inputUsdPerToken);
        this.outputUsdPerToken = Objects.requireNonNull(outputUsdPerToken);
        if (!java.util.Set.of("USD", "CNY").contains(currency))
            throw new IllegalArgumentException("currency");
        this.currency = currency;
        if (inputCeiling < 1
                || outputCeiling < 1
                || inputUsdPerToken.signum() < 0
                || outputUsdPerToken.signum() < 0)
            throw new IllegalArgumentException("Invalid cost bound");
    }

    @Override
    public BigDecimal maximumCost(ChatRequest request) {
        if (conservativeInputUnits(request) > inputCeiling)
            throw new HarnessException(
                    "COST_BUDGET_UNAVAILABLE",
                    "Prepared input exceeds configured conservative input ceiling");
        Object cap = request.parameters().get("max_tokens");
        Object count = request.parameters().getOrDefault("n", 1);
        if (!model.equals(request.parameters().get("model"))
                || !(cap instanceof Number number)
                || number.doubleValue() != number.intValue()
                || number.intValue() < 1
                || number.intValue() > outputCeiling
                || !(count instanceof Number samples)
                || samples.doubleValue() != 1)
            throw new HarnessException(
                    "COST_BUDGET_UNAVAILABLE",
                    "Request does not match configured model/output bound");
        return inputUsdPerToken
                .multiply(BigDecimal.valueOf(inputCeiling))
                .add(outputUsdPerToken.multiply(BigDecimal.valueOf(number.intValue())));
    }

    /**
     * Conservative supported-adapter bound: JSON escaped bytes plus explicit framing allowance. It
     * is not a tokenizer promise for arbitrary providers with hidden prompt expansion.
     */
    public static long conservativeInputUnits(ChatRequest request) {
        return 64L
                + 32L * request.messages().size()
                + 16L * request.tools().size()
                + jsonBytes(request.messages())
                + jsonBytes(request.tools())
                + jsonBytes(request.parameters());
    }

    private static long jsonBytes(Object value) {
        if (value == null) return 4;
        if (value instanceof String text) {
            long size = 2;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                size += c == '"' || c == '\\' ? 2 : c < 32 || c > 127 ? 6 : 1;
            }
            return size;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            long size = 2;
            for (var entry : map.entrySet())
                size += jsonBytes(entry.getKey()) + 1 + jsonBytes(entry.getValue()) + 1;
            return size;
        }
        if (value instanceof java.util.List<?> list) {
            long size = 2;
            for (Object item : list) size += jsonBytes(item) + 1;
            return size;
        }
        return value.toString().length();
    }

    @Override
    public String currency() {
        return currency;
    }

    @Override
    public BigDecimal actualCost(ChatRequest request, ChatResponse response) {
        Object input = response.usage().get("prompt_tokens"),
                output = response.usage().get("completion_tokens");
        if (!(input instanceof Number in) || !(output instanceof Number out))
            return maximumCost(request);
        int cap = ((Number) request.parameters().get("max_tokens")).intValue();
        if (in.doubleValue() != in.longValue()
                || out.doubleValue() != out.longValue()
                || in.longValue() < 0
                || in.longValue() > inputCeiling
                || out.longValue() < 0
                || out.longValue() > cap)
            throw new HarnessException(
                    "COST_ACCOUNTING_BREACH", "Usage exceeds verified request bounds");
        return inputUsdPerToken
                .multiply(BigDecimal.valueOf(in.longValue()))
                .add(outputUsdPerToken.multiply(BigDecimal.valueOf(out.longValue())));
    }
}
