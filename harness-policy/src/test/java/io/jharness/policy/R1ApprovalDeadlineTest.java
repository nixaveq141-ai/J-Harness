package io.jharness.policy;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R1ApprovalDeadlineTest {
    @Test
    void acceptedAnswerSurvivesOwnerDelayedBeyondDeadline() throws Exception {
        delayedOwner("accept");
    }

    @Test
    void cancellationStillWinsOverAcceptedAnswerBeforeOwnerReadsIt() throws Exception {
        delayedOwner("cancel");
    }

    @Test
    void closeStillWinsOverAcceptedAnswerBeforeOwnerReadsIt() throws Exception {
        delayedOwner("close");
    }

    @SuppressWarnings("try") // Explicit close is the lifecycle fault under test.
    void delayedOwner(String action) throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ownerThread = new AtomicReference<Thread>();
        var serviceReference = new AtomicReference<InteractiveApprovalService>();
        var heldOnce = new AtomicBoolean();
        var cancelled = new AtomicBoolean();
        CancellationToken token =
                () -> {
                    if (Thread.currentThread() == ownerThread.get()
                            && serviceReference.get() != null
                            && !serviceReference.get().prompts().isEmpty()
                            && heldOnce.compareAndSet(false, true)) {
                        held.countDown();
                        await(release);
                    }
                    return cancelled.get();
                };
        try (var service = new InteractiveApprovalService(Duration.ofMillis(800));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            serviceReference.set(service);
            var owner =
                    executor.submit(
                            () -> {
                                ownerThread.set(Thread.currentThread());
                                return service.decide(
                                        call(), new ToolContext(new PluginScope("s", "a"), token));
                            });
            try {
                assertTrue(held.await(2, TimeUnit.SECONDS));
                var prompt = service.prompts().getFirst();
                Object pending = pending(service, prompt.id());
                long deadline = deadline(pending);
                assertTrue(service.answer(prompt.id(), true, "operator accepted", "operator"));
                while (System.nanoTime() - deadline < 0) Thread.sleep(2);
                if (action.equals("cancel")) cancelled.set(true);
                if (action.equals("close")) service.close();
                release.countDown();
                if (action.equals("cancel")) {
                    var error =
                            assertThrows(
                                    ExecutionException.class, () -> owner.get(2, TimeUnit.SECONDS));
                    assertEquals("CANCELLED", ((HarnessException) error.getCause()).code());
                } else {
                    var result = owner.get(2, TimeUnit.SECONDS);
                    assertEquals(action.equals("accept"), result.approved());
                    assertEquals(
                            action.equals("accept")
                                    ? "operator accepted"
                                    : "Approval interface closed",
                            result.reason());
                    assertEquals(action.equals("accept") ? "operator" : "system", result.actor());
                }
                assertTrue(service.prompts().isEmpty());
                assertFalse(service.answer(prompt.id(), true, "late", "operator"));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void expiredAnswerAndOwnerContendOnPendingMonitorAndBothDeny() throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ownerThread = new AtomicReference<Thread>();
        var answerThread = new AtomicReference<Thread>();
        var serviceReference = new AtomicReference<InteractiveApprovalService>();
        var heldOnce = new AtomicBoolean();
        CancellationToken token =
                () -> {
                    if (Thread.currentThread() == ownerThread.get()
                            && serviceReference.get() != null
                            && !serviceReference.get().prompts().isEmpty()
                            && heldOnce.compareAndSet(false, true)) {
                        held.countDown();
                        await(release);
                    }
                    return false;
                };
        try (var service = new InteractiveApprovalService(Duration.ofMillis(800));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            serviceReference.set(service);
            var owner =
                    executor.submit(
                            () -> {
                                ownerThread.set(Thread.currentThread());
                                return service.decide(
                                        call(), new ToolContext(new PluginScope("s", "a"), token));
                            });
            try {
                assertTrue(held.await(2, TimeUnit.SECONDS));
                var prompt = service.prompts().getFirst();
                Object pending = pending(service, prompt.id());
                long deadline = deadline(pending);
                Future<Boolean> answer;
                synchronized (pending) {
                    release.countDown();
                    answer =
                            executor.submit(
                                    () -> {
                                        answerThread.set(Thread.currentThread());
                                        return service.answer(
                                                prompt.id(), true, "too late", "operator");
                                    });
                    long waitEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (System.nanoTime() < waitEnd
                            && (answerThread.get() == null
                                    || answerThread.get().getState() != Thread.State.BLOCKED
                                    || ownerThread.get().getState() != Thread.State.BLOCKED))
                        Thread.sleep(2);
                    assertNotNull(answerThread.get());
                    assertEquals(Thread.State.BLOCKED, answerThread.get().getState());
                    assertEquals(Thread.State.BLOCKED, ownerThread.get().getState());
                    while (System.nanoTime() - deadline < 0) Thread.sleep(2);
                }
                assertFalse(answer.get(2, TimeUnit.SECONDS));
                var result = owner.get(2, TimeUnit.SECONDS);
                assertFalse(result.approved());
                assertEquals("Approval timed out", result.reason());
                assertEquals("system", result.actor());
                assertTrue(service.prompts().isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    static ToolInvocation call() {
        return new ToolInvocation(
                "call", "write_file", Map.of("path", "x"), SideEffectLevel.WORKSPACE_WRITE);
    }

    static Object pending(InteractiveApprovalService service, String id) throws Exception {
        var field = InteractiveApprovalService.class.getDeclaredField("pending");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(service)).get(id);
    }

    static long deadline(Object pending) throws Exception {
        var method = pending.getClass().getDeclaredMethod("deadline");
        method.setAccessible(true);
        return (Long) method.invoke(pending);
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(4, TimeUnit.SECONDS))
                throw new AssertionError("owner release timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
