package io.jharness.tools;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** No queue: at most one bounded group of bodies, with owner-thread preparation and settlement. */
public final class BoundedReadOnlyToolScheduler implements ToolScheduler {
    private final ToolSchedulingOptions options;
    private final AtomicBoolean closed = new AtomicBoolean(), busy = new AtomicBoolean();
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();

    public BoundedReadOnlyToolScheduler(ToolSchedulingOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    @Override
    public List<ToolResult> execute(
            List<ToolInvocation> calls, ToolPipelineContext context, ToolPipeline pipeline) {
        if (closed.get()) throw new HarnessException("SCHEDULER_CLOSED", "Scheduler stopped");
        if (!workers.isEmpty())
            throw new HarnessException("SCHEDULER_BUSY", "Earlier worker not confirmed stopped");
        if (!busy.compareAndSet(false, true))
            throw new HarnessException("SCHEDULER_BUSY", "One batch per scoped scheduler");
        var run = new Run(context);
        try {
            for (ToolInvocation call : calls) {
                context.cancellation().throwIfCancelled();
                if (closed.get())
                    throw new HarnessException("SCHEDULER_CLOSED", "Scheduler stopped");
                boolean candidate =
                        options.maxConcurrency() > 1
                                && options.tools().contains(call.tool())
                                && context.tools()
                                        .resolve(call.tool(), context.scope())
                                        .map(t -> t.sideEffectLevel() == SideEffectLevel.READ_ONLY)
                                        .orElse(false);
                if (!candidate || run.pending.size() >= options.maxConcurrency()) run.drain();
                if (run.stopped) break;
                var prepared =
                        pipeline.prepare(
                                call,
                                run.context,
                                () -> {
                                    run.drain();
                                    if (run.stopped)
                                        throw new HarnessException(
                                                "SCHEDULER_STOPPED", "Earlier call requires stop");
                                });
                if (run.stopped) break;
                if (prepared.isEmpty()) {
                    run.drain();
                    if (run.stopped) break;
                    decision(context, call, false, "pipeline does not expose preparation");
                    run.accept(pipeline.execute(call, run.context));
                } else if (candidate && prepared.get().parallelAllowed()) {
                    decision(
                            context,
                            call,
                            true,
                            "host allowlist, registered READ_ONLY and effective policy ALLOW");
                    run.launch(call, prepared.get());
                } else {
                    run.drain();
                    if (run.stopped) break;
                    decision(
                            context,
                            call,
                            false,
                            candidate
                                    ? "approval, denial or failed preparation barrier"
                                    : "serial policy or tool outside trusted read-only set");
                    run.accept(prepared.get().finish(prepared.get().run()));
                }
                if (run.stopped) break;
            }
            run.drain();
            if (run.results.stream().anyMatch(r -> r.status() == ToolStatus.UNKNOWN))
                throw new HarnessException(
                        "UNKNOWN_SIDE_EFFECT", "Worker outcome requires verification");
            if (run.stopped)
                throw new HarnessException(
                        run.stopReason, "Batch stopped; inspect settled tool facts");
            if (run.results.stream()
                    .anyMatch(r -> Boolean.TRUE.equals(r.value().get("sideEffectUncertain"))))
                throw new HarnessException(
                        "UNKNOWN_SIDE_EFFECT", "Partial effects require verification");
            return List.copyOf(run.results);
        } catch (RuntimeException ex) {
            run.abort(ex instanceof HarnessException h ? h.code() : "SCHEDULER_FAILED");
            try {
                run.drain();
            } catch (RuntimeException settlement) {
                ex.addSuppressed(settlement);
            }
            throw ex;
        } finally {
            run.cancel.set(true);
            workers.forEach(Thread::interrupt);
            busy.set(false);
        }
    }

    private void decision(
            ToolPipelineContext c, ToolInvocation call, boolean parallel, String reason) {
        EventSupport.append(
                c.session(),
                c.events(),
                c.scope(),
                "tool.scheduled",
                EventLevel.TOOL,
                c.turnId(),
                c.stepId(),
                call.callId(),
                Map.of(
                        "mode",
                        parallel ? "readonly-parallel" : "serial",
                        "reason",
                        reason,
                        "maxConcurrency",
                        options.maxConcurrency(),
                        "timeoutMillis",
                        options.timeout().toMillis()));
    }

    private final class Run {
        final AtomicBoolean cancel = new AtomicBoolean();
        final ToolPipelineContext context;
        final List<Task> pending = new ArrayList<>();
        final List<ToolResult> results = new ArrayList<>();
        boolean stopped;
        String stopReason;
        long grace;

        Run(ToolPipelineContext c) {
            context =
                    new ToolPipelineContext(
                            c.scope(),
                            c.turnId(),
                            c.stepId(),
                            c.tools(),
                            c.session(),
                            c.events(),
                            c.policy(),
                            c.approvals(),
                            c.checkpoints(),
                            () -> c.cancellation().isCancelled() || cancel.get() || closed.get());
        }

        void abort(String reason) {
            stopped = true;
            if (stopReason == null) {
                stopReason = reason;
                grace = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                EventSupport.append(
                        context.session(),
                        context.events(),
                        context.scope(),
                        "tool.batch.stopped",
                        EventLevel.STEP,
                        context.turnId(),
                        context.stepId(),
                        null,
                        Map.of("reason", reason, "pending", pending.size(), "graceMillis", 2000));
            }
            cancel.set(true);
            workers.forEach(Thread::interrupt);
        }

        void accept(ToolResult result) {
            results.add(result);
            if (ToolBatchExecution.stops(result))
                abort(
                        result.status() == ToolStatus.UNKNOWN
                                        || result.status() != ToolStatus.CANCELLED
                                                && Boolean.TRUE.equals(
                                                        result.value().get("sideEffectUncertain"))
                                ? "UNKNOWN_SIDE_EFFECT"
                                : result.status() == ToolStatus.TIMEOUT
                                        ? "SCHEDULER_TIMEOUT"
                                        : result.status() == ToolStatus.CANCELLED
                                                ? "CANCELLED"
                                                : "CHECKPOINT_FAILED");
        }

        void launch(ToolInvocation call, PreparedToolCall prepared) {
            var task = new Task(call, prepared);
            pending.add(task);
            task.thread =
                    Thread.ofVirtual()
                            .name("readonly-tool-" + call.callId())
                            .unstarted(
                                    () -> {
                                        try {
                                            task.result = prepared.run();
                                        } catch (Throwable ex) {
                                            task.result =
                                                    ToolResults.failure(
                                                            call.callId(),
                                                            ToolStatus.UNKNOWN,
                                                            "SCHEDULER_WORKER_FAILED");
                                        } finally {
                                            if (task.result != null
                                                    && ToolBatchExecution.stops(task.result))
                                                cancel.set(true);
                                            task.done.countDown();
                                            workers.remove(Thread.currentThread());
                                        }
                                    });
            workers.add(task.thread);
            task.thread.start();
        }

        void drain() {
            for (Task task : pending) {
                while (task.done.getCount() != 0) {
                    if (context.cancellation().isCancelled() && !stopped) abort("CANCELLED");
                    if (System.nanoTime() - task.started >= options.timeout().toNanos() && !stopped)
                        abort("SCHEDULER_TIMEOUT");
                    if (stopped && System.nanoTime() >= grace) break;
                    try {
                        task.done.await(20, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) {
                        abort("CANCELLED");
                    }
                }
                ToolResult result =
                        task.done.getCount() == 0
                                ? task.result
                                : ToolResults.failure(
                                        task.call.callId(),
                                        ToolStatus.UNKNOWN,
                                        "SCHEDULER_WORKER_UNKNOWN");
                if (result == null)
                    result =
                            ToolResults.failure(
                                    task.call.callId(),
                                    ToolStatus.UNKNOWN,
                                    "SCHEDULER_WORKER_FAILED");
                if (result.status() == ToolStatus.CANCELLED
                        && "SCHEDULER_TIMEOUT".equals(stopReason))
                    result =
                            ToolResults.failure(
                                    task.call.callId(), ToolStatus.TIMEOUT, "SCHEDULER_TIMEOUT");
                accept(task.prepared.finish(result));
            }
            pending.clear();
        }
    }

    private static final class Task {
        final ToolInvocation call;
        final PreparedToolCall prepared;
        final CountDownLatch done = new CountDownLatch(1);
        final long started = System.nanoTime();
        volatile ToolResult result;
        Thread thread;

        Task(ToolInvocation call, PreparedToolCall prepared) {
            this.call = call;
            this.prepared = prepared;
        }
    }

    @Override
    public void close() {
        closed.set(true);
        workers.forEach(Thread::interrupt);
    }
}
