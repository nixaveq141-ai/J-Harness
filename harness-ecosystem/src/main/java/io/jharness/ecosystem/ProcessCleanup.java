package io.jharness.ecosystem;

import io.jharness.process.ProcessExit;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded identity-handle sampling; deliberately not an OS process containment guarantee. */
final class ProcessCleanup {
    private ProcessCleanup() {}

    static boolean stop(Process process) {
        boolean interrupted = Thread.interrupted(), clean = true;
        List<ProcessHandle> handles = new ArrayList<>();
        try {
            var descendants = process.descendants().limit(65).toList();
            clean = descendants.size() <= 64;
            handles.addAll(descendants.subList(0, Math.min(64, descendants.size())));
        } catch (RuntimeException ex) {
            clean = false;
        }
        handles.add(process.toHandle());
        for (var handle : handles)
            try {
                if (handle.isAlive()) handle.destroyForcibly();
            } catch (RuntimeException ex) {
                clean = false;
            }
        for (var stream :
                List.<Closeable>of(
                        process.getOutputStream(),
                        process.getInputStream(),
                        process.getErrorStream()))
            try {
                stream.close();
            } catch (IOException ex) {
                clean = false;
            }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        // OS handles can disappear before Process's cached exit state is reaped.
        while ((process.isAlive() || handles.stream().anyMatch(ProcessHandle::isAlive))
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        clean &= ProcessExit.await(process, deadline);
        clean &= !process.isAlive() && handles.stream().noneMatch(ProcessHandle::isAlive);
        if (interrupted) Thread.currentThread().interrupt();
        return clean;
    }
}
