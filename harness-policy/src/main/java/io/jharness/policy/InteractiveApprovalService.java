package io.jharness.policy;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI adapter submits a decision for one prompt ID. Cancellation/timeout revokes pending prompts.
 */
public final class InteractiveApprovalService implements ApprovalService, AutoCloseable {
    public record Prompt(String id, ToolInvocation call, String target, String impact) {}

    private record Pending(
            Prompt prompt,
            CompletableFuture<ApprovalDecision> answer,
            CancellationToken cancellation,
            long deadline) {}

    private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final AtomicBoolean closed = new AtomicBoolean();

    public InteractiveApprovalService(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("timeout");
    }

    public List<Prompt> prompts() {
        return pending.values().stream().map(Pending::prompt).toList();
    }

    public boolean answer(String promptId, boolean approved, String reason, String actor) {
        if (promptId == null || promptId.isBlank()) return false;
        Pending value = pending.get(promptId);
        if (value == null) return false;
        synchronized (value) {
            if (closed.get()
                    || value.cancellation().isCancelled()
                    || System.nanoTime() - value.deadline() >= 0
                    || value.answer().isDone()) return false;
            return value.answer().complete(new ApprovalDecision(approved, reason, actor));
        }
    }

    @Override
    public ApprovalDecision decide(ToolInvocation call, ToolContext context) {
        context.cancellation().throwIfCancelled();
        if (closed.get()) return new ApprovalDecision(false, "Approval interface closed", "system");
        String id = UUID.randomUUID().toString();
        Prompt prompt =
                new Prompt(
                        id,
                        call,
                        String.valueOf(
                                call.arguments()
                                        .getOrDefault(
                                                "path", call.arguments().getOrDefault("cwd", "."))),
                        call.sideEffectLevel().name()
                                + (call.sideEffectLevel() == SideEffectLevel.SYSTEM_IMPACT
                                        ? ": arbitrary commands may change files, spawn processes or use network; cannot preview"
                                        : ": workspace mutation"));
        Pending value =
                new Pending(
                        prompt,
                        new CompletableFuture<>(),
                        context.cancellation(),
                        System.nanoTime() + timeout.toNanos());
        synchronized (this) {
            if (closed.get())
                return new ApprovalDecision(false, "Approval interface closed", "system");
            pending.put(id, value);
        }
        long start = System.nanoTime();
        try {
            while (true) {
                context.cancellation().throwIfCancelled();
                if (closed.get())
                    return new ApprovalDecision(false, "Approval interface closed", "system");
                if (System.nanoTime() - start >= timeout.toNanos())
                    return new ApprovalDecision(false, "Approval timed out", "system");
                try {
                    var decision = value.answer().get(20, TimeUnit.MILLISECONDS);
                    synchronized (value) {
                        context.cancellation().throwIfCancelled();
                        if (closed.get())
                            return new ApprovalDecision(
                                    false, "Approval interface closed", "system");
                        if (System.nanoTime() - value.deadline() >= 0)
                            return new ApprovalDecision(false, "Approval timed out", "system");
                        return decision;
                    }
                } catch (TimeoutException ex) {
                    /* bounded cancellation polling */
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new HarnessException("CANCELLED", "Approval interrupted");
                } catch (ExecutionException ex) {
                    return new ApprovalDecision(false, "Approval failed", "system");
                }
            }
        } finally {
            pending.remove(id, value);
        }
    }

    @Override
    public synchronized void close() {
        closed.set(true);
        pending.forEach(
                (id, value) -> {
                    synchronized (value) {
                        value.answer()
                                .complete(
                                        new ApprovalDecision(
                                                false, "Approval interface closed", "system"));
                        pending.remove(id, value);
                    }
                });
    }
}
