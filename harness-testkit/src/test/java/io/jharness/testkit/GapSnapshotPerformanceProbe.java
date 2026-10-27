package io.jharness.testkit;

import io.jharness.api.*;
import io.jharness.ecosystem.ContentPages;
import io.jharness.session.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit fixed-heap probe, separate from timing-sensitive unit tests. No provider requests. */
public final class GapSnapshotPerformanceProbe {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static Map<String, Object> measure(Callable<?> operation, int repetitions)
            throws Exception {
        long id = Thread.currentThread().threadId();
        long bytes = THREADS.getThreadAllocatedBytes(id), start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) operation.call();
        return Map.of(
                "operations",
                repetitions,
                "elapsedNanos",
                System.nanoTime() - start,
                "threadAllocatedBytes",
                THREADS.getThreadAllocatedBytes(id) - bytes);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !THREADS.isThreadAllocatedMemorySupported())
            throw new IllegalArgumentException(
                    "Require a new output directory and allocation counters");
        THREADS.setThreadAllocatedMemoryEnabled(true);
        Path root = Files.createDirectory(Path.of(args[0]));
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "INCOMPLETE");
        result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        if (Runtime.getRuntime().maxMemory() > 160L * 1024 * 1024)
            throw new IllegalArgumentException("Run with -Xmx128m");
        String text = "line facts\n".repeat(350000);
        var store = new LocalArtifactStore(root.resolve("artifacts"), data -> data);
        var artifact = store.put("scope", text);
        result.put("artifactBytes", artifact.bytes());
        result.put(
                "coldArtifact",
                measure(
                        () ->
                                new LocalArtifactStore(root.resolve("artifacts"), data -> data)
                                        .readText("scope", artifact.id()),
                        8));
        store.readText("scope", artifact.id());
        result.put(
                "warmArtifact",
                measure(
                        () -> {
                            String actual = store.readText("scope", artifact.id());
                            if (!actual.equals(text)) throw new AssertionError("Artifact mismatch");
                            return actual;
                        },
                        8));
        result.put("coldPages", measure(() -> ContentPages.page(text, 32768, ""), 32));
        var pages = new ContentPages.Cache();
        pages.page(text, 0, "");
        result.put("warmPages", measure(() -> pages.page(text, 32768, ""), 32));
        var repo = new InMemorySessionRepository();
        try (var writer = repo.openWriter("long", WriteOwnership.EXCLUSIVE)) {
            for (int i = 0; i < 10000; i++)
                writer.append(
                        SessionEvent.session(
                                "long",
                                "user.message",
                                Map.of("role", "user", "content", i + ":" + "fact".repeat(100))));
        }
        result.put(
                "projection10000",
                measure(
                        () -> {
                            var projection =
                                    new EventSessionProjection(repo.openReader("long").events());
                            if (projection.messages().size() != 10000)
                                throw new AssertionError("History lost");
                            return projection;
                        },
                        3));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++)
                futures.add(
                        pool.submit(
                                () -> {
                                    try {
                                        for (int page = 0; page < 16; page++) {
                                            if (!text.equals(
                                                    store.readText("scope", artifact.id())))
                                                throw new AssertionError(
                                                        "Concurrent artifact mismatch");
                                            if (!ContentPages.sha(text)
                                                    .equals(
                                                            pages.page(text, page * 16384, "")
                                                                    .get("contentSha256")))
                                                throw new AssertionError(
                                                        "Concurrent page mismatch");
                                        }
                                    } catch (Exception failure) {
                                        throw new RuntimeException(failure);
                                    }
                                }));
            for (var future : futures) future.get(60, TimeUnit.SECONDS);
        }
        result.put("concurrentReads", 64);
        result.put(
                "sampledHeapUsedAfterBytes",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        for (String category : List.of("Artifact", "Pages")) {
            long cold =
                    ((Number)
                                    Values.object(result.get("cold" + category))
                                            .get("threadAllocatedBytes"))
                            .longValue();
            long warm =
                    ((Number)
                                    Values.object(result.get("warm" + category))
                                            .get("threadAllocatedBytes"))
                            .longValue();
            if (warm >= cold / 2)
                throw new AssertionError("Allocation reduction missing: " + category);
        }
        result.put("status", "PASS");
        Files.write(
                root.resolve("result.json"),
                JsonEncoding.encode(result, 32768, 32768, 64, "PROBE"));
        System.out.println(
                new String(
                        JsonEncoding.encode(result, 32768, 32768, 64, "PROBE"),
                        java.nio.charset.StandardCharsets.UTF_8));
    }
}
