package io.jharness.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

public record Budget(
        int maxSteps, Duration maxDuration, Optional<BigDecimal> maxApiCost, String currency) {
    public Budget(int maxSteps, Duration maxDuration, Optional<BigDecimal> maxApiCostUsd) {
        this(maxSteps, maxDuration, maxApiCostUsd, "USD");
    }

    public Optional<BigDecimal> maxApiCostUsd() {
        if (!currency.equals("USD")) throw new IllegalStateException("Budget currency is not USD");
        return maxApiCost;
    }

    public Budget(int maxSteps, Duration maxDuration) {
        this(maxSteps, maxDuration, Optional.empty());
    }

    public Budget {
        if (maxDuration != null) {
            try {
                maxDuration.toNanos();
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Duration exceeds monotonic budget range");
            }
        }
        if (!java.util.Set.of("USD", "CNY").contains(currency))
            throw new IllegalArgumentException("Unsupported currency");
        java.util.Objects.requireNonNull(maxApiCost);
        if (maxApiCost.isPresent() && maxApiCost.get().signum() <= 0)
            throw new IllegalArgumentException("Cost budget must be positive");
        if (maxSteps <= 0
                || maxDuration == null
                || maxDuration.isZero()
                || maxDuration.isNegative())
            throw new IllegalArgumentException("Budget must be positive");
    }
}
