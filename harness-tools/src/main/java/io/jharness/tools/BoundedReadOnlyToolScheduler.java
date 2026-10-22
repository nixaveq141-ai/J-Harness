package io.jharness.tools;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** No queue: at most one bounded group of bodies, with owner-thread preparation and settlement. */
public final class BoundedReadOnlyToolScheduler implements ToolScheduler {
    private final ToolSchedulingOptions options;
    private final AtomicBoolean closed = new AtomicBoolean(), busy = new AtomicBoolean();
    private final Object lifecycle = new Object();
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
                if (run.stopped) {
                    if (prepared.isPresent())
                        run.accept(prepared.get().abandon(call.callId(), "SCHEDULER_STOPPED"));
                    break;
                }
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
                    if (run.stopped) {
                        run.accept(prepared.get().abandon(call.callId(), "SCHEDULER_STOPPED"));
                        break;
                    }
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
            if (run.results.stream().anyMatch(r -> ToolBatchExecution.unknownOutcome(r)))
                throw new HarnessException(
                        "UNKNOWN_SIDE_EFFECT", "Worker outcome requires verification");
            if (run.stopped)
                throw new HarnessException(
                        run.stopReason, "Batch stopped; inspect settled tool facts");
            return List.copyOf(run.results);
        } catch (RuntimeException ex) {
            try {
                run.abort(ex instanceof HarnessException h ? h.code() : "SCHEDULER_FAILED");
            } catch (RuntimeException secondary) {
                ex.addSuppressed(secondary);
            }
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
        final CancellationToken parentCancellation;
        final List<Task> pending = new ArrayList<>();
        final List<ToolResult> results = new ArrayList<>();
        boolean stopped;
        String stopReason;
        long grace;

        Run(ToolPipelineContext c) {
            parentCancellation = c.cancellation();
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
            if (stopReason == null
                    || "UNKNOWN_SIDE_EFFECT".equals(reason)
                    || "CANCELLED".equals(stopReason) && !"CANCELLED".equals(reason)) {
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
                        ToolBatchExecution.unknownOutcome(result)
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
                                            workers.remove(Thread.currentThread());
                                            task.done.countDown();
                                        }
                                    });
            synchronized (lifecycle) {
                if (closed.get()) {
                    task.result =
                            ToolResults.failure(
                                    call.callId(), ToolStatus.CANCELLED, "SCHEDULER_CLOSED");
                    task.done.countDown();
                    return;
                }
                workers.add(task.thread);
                task.thread.start();
            }
        }

        void drain() {
            RuntimeException failure = null;
            for (Task task : new ArrayList<>(pending)) {
                pending.remove(task);
                try {
                    while (task.done.getCount() != 0) {
                        if (parentCancellation.isCancelled() && !stopped) abort("CANCELLED");
                        if (closed.get() && !stopped) abort("SCHEDULER_CLOSED");
                        if (System.nanoTime() - task.started >= options.timeout().toNanos()
                                && !stopped) abort("SCHEDULER_TIMEOUT");
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
                                        task.call.callId(),
                                        ToolStatus.TIMEOUT,
                                        "SCHEDULER_TIMEOUT");
                    accept(task.prepared.finish(result));
                } catch (RuntimeException error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                    cancel.set(true);
                }
            }
            if (failure != null) throw failure;
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
        synchronized (lifecycle) {
            closed.set(true);
            workers.forEach(Thread::interrupt);
        }
    }
}
