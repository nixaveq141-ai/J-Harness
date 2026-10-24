package io.jharness.process;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owned-process exit confirmation within the caller's existing cleanup deadline. */
public final class ProcessExit {
    private ProcessExit() {}

    public static boolean await(Process process, long deadline) {
        if (System.nanoTime() >= deadline) return false;
        boolean interrupted = Thread.interrupted();
        var exited = new AtomicBoolean();
        // On Windows isAlive()/exitValue() use GetExitCodeProcess. An exit code
        // can precede the kernel termination signal and release of the CWD.
        // The untimed JDK Process.waitFor() waits on the owned process handle;
        // its timed overload can return early from the same exit-code check.
        Thread waiter =
                Thread.ofVirtual()
                        .name("owned-process-exit")
                        .start(
                                () -> {
                                    try {
                                        process.waitFor();
                                        exited.set(true);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    } catch (RuntimeException ignored) {
                                        /* no confirmation: report UNKNOWN */
                                    }
                                });
        try {
            long remaining;
            while (waiter.isAlive() && (remaining = deadline - System.nanoTime()) > 0) {
                try {
                    waiter.join(Duration.ofNanos(remaining));
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
            return exited.get() && !process.isAlive();
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (waiter.isAlive()) waiter.interrupt();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
