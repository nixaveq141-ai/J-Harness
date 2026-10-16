package io.jharness.api;

import java.math.BigDecimal;

/** Host-configured USD upper bound, reserved before transmission. Never infer prices from usage. */
@FunctionalInterface
public interface ApiCostGuard {
    BigDecimal maximumCost(ChatRequest request);

    default String currency() {
        return "USD";
    }

    /** Missing reliable usage retains the entire reservation. */
    default BigDecimal actualCost(ChatRequest request, ChatResponse response) {
        return maximumCost(request);
    }

    /** Known rejected response: retain the fee upper bound but release the active attempt slot. */
    default void requestRejected(ChatRequest request) {}

    static ApiCostGuard unavailable() {
        return request -> {
            throw new HarnessException(
                    "COST_BUDGET_UNAVAILABLE",
                    "Configure a verified request cost bound before enabling API fee budget");
        };
    }
}
