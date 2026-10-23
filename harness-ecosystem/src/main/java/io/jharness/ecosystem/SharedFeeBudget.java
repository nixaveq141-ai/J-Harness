package io.jharness.ecosystem;

import io.jharness.api.*;
import java.math.BigDecimal;
import java.util.*;

/** Per parent turn conservative reservations shared by the parent and all delegated models. */
final class SharedFeeBudget {
    private final Optional<BigDecimal> limit;
    private BigDecimal reserved = BigDecimal.ZERO;
    private long epoch;

    SharedFeeBudget(Budget budget) {
        limit = budget.maxApiCost();
    }

    synchronized void beginTurn() {
        reserved = BigDecimal.ZERO;
        epoch++;
    }

    synchronized BigDecimal reserved() {
        return reserved;
    }

    ApiCostGuard guard(ApiCostGuard delegate) {
        return new ApiCostGuard() {
            private BigDecimal pending;
            private long pendingEpoch;

            @Override
            public String currency() {
                return delegate.currency();
            }

            @Override
            public BigDecimal maximumCost(ChatRequest request) {
                BigDecimal bound = Objects.requireNonNull(delegate.maximumCost(request));
                if (bound.signum() < 0) throw BoundedJson.error("COST_BUDGET_UNAVAILABLE");
                synchronized (SharedFeeBudget.this) {
                    if (pending != null && pendingEpoch == epoch)
                        throw BoundedJson.error("COST_RESERVATION_PENDING");
                    if (limit.isPresent() && reserved.add(bound).compareTo(limit.get()) > 0)
                        throw BoundedJson.error("COST_LIMIT");
                    reserved = reserved.add(bound);
                    pending = bound;
                    pendingEpoch = epoch;
                    return bound;
                }
            }

            @Override
            public void requestRejected(ChatRequest request) {
                synchronized (SharedFeeBudget.this) {
                    if (pending == null || pendingEpoch != epoch)
                        throw BoundedJson.error("COST_ACCOUNTING_BREACH");
                    delegate.requestRejected(request);
                    pending = null; // Keep reserved unchanged: rejection is not proof of zero
                    // billing.
                }
            }

            @Override
            public BigDecimal actualCost(ChatRequest request, ChatResponse response) {
                synchronized (SharedFeeBudget.this) {
                    if (pending == null || pendingEpoch != epoch)
                        throw BoundedJson.error("COST_ACCOUNTING_BREACH");
                    BigDecimal actual =
                            Objects.requireNonNull(delegate.actualCost(request, response));
                    if (pending == null
                            || pendingEpoch != epoch
                            || actual.signum() < 0
                            || actual.compareTo(pending) > 0)
                        throw BoundedJson.error("COST_ACCOUNTING_BREACH");
                    reserved = reserved.subtract(pending).add(actual);
                    pending = null;
                    return actual;
                }
            }
        };
    }
}
