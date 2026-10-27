import io.jharness.api.*;
import io.jharness.session.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit local sample, not a latency SLA. Requires a fresh directory and -Xmx128m. */
public final class DurableSessionCost {
    private static final int COUNT = 10000;
    private static final String SESSION = "durable-cost";
    private static final int PER_SESSION = COUNT / 2;

    private static Map<String, Object> distribution(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return Map.of("operations", sorted.length, "p50Nanos", sorted[sorted.length / 2],
                "p95Nanos", sorted[(int) Math.ceil(sorted.length * .95) - 1],
                "p99Nanos", sorted[(int) Math.ceil(sorted.length * .99) - 1],
                "maxNanos", sorted[sorted.length - 1]);
    }

    private static String content(int i) { return i + ":" + "durable fact ".repeat(10); }

    private static void verify(Path data, List<String> ids) throws Exception {
        for (int shard = 0; shard < 2; shard++) {
            String session = SESSION + "-" + shard;
            int offset = shard * PER_SESSION;
            var events = new JsonlSessionRepository(data).openReader(session).events();
            if (events.size() != PER_SESSION) throw new AssertionError("History count changed");
            for (int i = 0; i < PER_SESSION; i++) {
                var event = events.get(i);
                if (!event.eventId().equals(ids.get(offset + i)) || !event.type().equals("user.message")
                        || !event.payload().equals(Map.of("role", "user", "content", content(offset + i))))
                    throw new AssertionError("Reopened fact changed at " + i);
            }
            var projection = new EventSessionProjection(events);
            if (projection.messages().size() != PER_SESSION) throw new AssertionError("Projection lost facts");
            for (int i = 0; i < PER_SESSION; i++) {
                if (!content(offset + i).equals(projection.messages().get(i).get("content")))
                    throw new AssertionError("Projection changed content at " + i);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || Runtime.getRuntime().maxMemory() > 160L * 1024 * 1024)
            throw new IllegalArgumentException("Require fresh output directory and -Xmx128m");
        Path root = Files.createDirectory(Path.of(args[0]));
        Path data = root.resolve("sessions"), report = root.resolve("result.json");
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("status", "INCOMPLETE");
        result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        result.put("os", System.getProperty("os.name"));
        result.put("arch", System.getProperty("os.arch"));
        result.put("java", System.getProperty("java.version"));
        Files.write(report, JsonEncoding.encode(result, 32768, 32768, 64, "PROBE"));
        var forces = new AtomicInteger();
        long[] forceNanos = new long[COUNT + 32], appendNanos = new long[COUNT];
        var repo = new JsonlSessionRepository(data, JsonlSessionRepository.TailPolicy.REJECT,
                channel -> {
                    long start = System.nanoTime();
                    channel.force(true);
                    int index = forces.getAndIncrement();
                    if (index >= forceNanos.length) throw new AssertionError("Unexpected force count");
                    forceNanos[index] = System.nanoTime() - start;
                }, code -> { throw new AssertionError("Unexpected repository diagnostic: " + code); });
        var ids = new ArrayList<String>(COUNT);
        long start = System.nanoTime();
        int appendForces = 0;
        for (int shard = 0; shard < 2; shard++) {
            String session = SESSION + "-" + shard;
            try (var writer = repo.openWriter(session, WriteOwnership.EXCLUSIVE)) {
                if (!writer.durable()) throw new AssertionError("Durability disabled");
                int forceStart = forces.get();
                for (int local = 0; local < PER_SESSION; local++) {
                    int i = shard * PER_SESSION + local;
                    var event = SessionEvent.session(session, "user.message",
                            Map.of("role", "user", "content", content(i)));
                    long appendStart = System.nanoTime();
                    String id = writer.append(event);
                    appendNanos[i] = System.nanoTime() - appendStart;
                    if (!id.equals(event.eventId()) || forces.get() != forceStart + local + 1)
                        throw new AssertionError("Append did not complete exactly one real force");
                    ids.add(id);
                    appendForces++;
                }
            }
        }
        result.put("sessionCount", 2);
        result.put("eventsPerSession", PER_SESSION);
        result.put("appendElapsedNanos", System.nanoTime() - start);
        result.put("append", distribution(appendNanos));
        result.put("realAppendForceCalls", appendForces);
        result.put("forceIncludingMetadata", distribution(Arrays.copyOf(forceNanos, forces.get())));
        var logHashes = new LinkedHashMap<String, String>();
        long logBytes = 0;
        for (int shard = 0; shard < 2; shard++) {
            Path log = data.resolve(SESSION + "-" + shard + ".jsonl");
            logHashes.put(log.getFileName().toString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(log))));
            logBytes += Files.size(log);
        }
        result.put("logBytes", logBytes);
        result.put("logSha256", logHashes);
        long[] reads = new long[3];
        for (int i = 0; i < reads.length; i++) {
            start = System.nanoTime();
            verify(data, ids);
            reads[i] = System.nanoTime() - start;
        }
        result.put("reopenedReadAndProjection", distribution(reads));
        start = System.nanoTime();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                futures.add(pool.submit(() -> {
                    for (int repeat = 0; repeat < 4; repeat++) verify(data, ids);
                    return null;
                }));
            }
            for (var future : futures) future.get(60, TimeUnit.SECONDS);
        }
        result.put("concurrentReaders", 4);
        result.put("concurrentFullReads", 16);
        result.put("concurrentReadElapsedNanos", System.nanoTime() - start);
        for (var entry : logHashes.entrySet()) {
            String after = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(data.resolve(entry.getKey()))));
            if (!entry.getValue().equals(after))
                throw new AssertionError("Read/projection mutated durable history");
        }
        result.put("sampledHeapUsedAfterBytes",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        result.put("status", "PASS");
        Files.write(report, JsonEncoding.encode(result, 32768, 32768, 64, "PROBE"));
        System.out.println(Files.readString(report));
    }
}
