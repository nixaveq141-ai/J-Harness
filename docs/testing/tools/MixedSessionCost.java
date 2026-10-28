import io.jharness.api.*;
import io.jharness.session.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit paced single-writer/four-reader sample. No latency SLA or cross-process claim. */
public final class MixedSessionCost {
    private static final int COUNT = 2048, ROUNDS = 16;
    private static final String ID = "mixed-cost";
    private static String content(int i) { return i + ":" + "mixed durable fact ".repeat(8); }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || Runtime.getRuntime().maxMemory() > 160L * 1024 * 1024)
            throw new IllegalArgumentException("Require fresh directory and -Xmx128m");
        Path root = Files.createDirectory(Path.of(args[0])), data = root.resolve("sessions");
        var result = new LinkedHashMap<String,Object>();
        result.put("status", "INCOMPLETE");
        Files.write(root.resolve("result.json"), JsonEncoding.encode(result, 32768, 32768, 64, "PROBE"));
        var events = new ArrayList<SessionEvent>();
        for (int i=0;i<COUNT;i++) events.add(SessionEvent.session(ID,"user.message",Map.of("role","user","content",content(i))));
        var forces = new AtomicInteger();
        var repo = new JsonlSessionRepository(data, JsonlSessionRepository.TailPolicy.REJECT,
                channel -> { channel.force(true); forces.incrementAndGet(); },
                code -> { throw new AssertionError("Repository diagnostic " + code); });
        var start = new CyclicBarrier(5);
        var finish = new CyclicBarrier(5);
        var reads = new AtomicInteger();
        var partialReads = new AtomicInteger();
        var appendNanos = new long[COUNT];
        var readNanos = new ConcurrentLinkedQueue<Long>();
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(p -> p.getType() == MemoryType.HEAP).toList();
        for (var pool:pools) pool.resetPeakUsage();
        long begin = System.nanoTime();
        try (var writer = repo.openWriter(ID, WriteOwnership.EXCLUSIVE);
             var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int reader=0;reader<4;reader++) futures.add(executor.submit(() -> {
                for(int round=0;round<ROUNDS;round++) {
                    start.await(20,TimeUnit.SECONDS);
                    long at=System.nanoTime();
                    var snapshot = new JsonlSessionRepository(data).openReader(ID).events();
                    if(snapshot.size()>COUNT) throw new AssertionError("Extra facts");
                    for(int i=0;i<snapshot.size();i++) {
                        var e=snapshot.get(i);
                        if(!e.eventId().equals(events.get(i).eventId()) || !e.payload().equals(events.get(i).payload()))
                            throw new AssertionError("Non-prefix snapshot");
                    }
                    var projection = new EventSessionProjection(snapshot);
                    if(projection.messages().size()!=snapshot.size()) throw new AssertionError("Projection count");
                    for(int i=0;i<snapshot.size();i++)
                        if(!content(i).equals(projection.messages().get(i).get("content"))) throw new AssertionError("Projection content");
                    readNanos.add(System.nanoTime()-at);reads.incrementAndGet();
                    if(snapshot.size()>0 && snapshot.size()<COUNT) partialReads.incrementAndGet();
                    finish.await(20,TimeUnit.SECONDS);
                }
                return null;
            }));
            for(int round=0;round<ROUNDS;round++) {
                start.await(20,TimeUnit.SECONDS);
                for(int i=round*(COUNT/ROUNDS);i<(round+1)*(COUNT/ROUNDS);i++) {
                    long at=System.nanoTime();
                    if(!writer.append(events.get(i)).equals(events.get(i).eventId())) throw new AssertionError("Append identity");
                    appendNanos[i]=System.nanoTime()-at;
                }
                finish.await(20,TimeUnit.SECONDS);
            }
            for(var future:futures) future.get(20,TimeUnit.SECONDS);
        }
        var finalFacts=new JsonlSessionRepository(data).openReader(ID).events();
        if(finalFacts.size()!=COUNT || reads.get()!=64 || partialReads.get()<56 || forces.get()!=COUNT+1)
            throw new AssertionError("Incomplete mixed sample");
        for(int i=0;i<COUNT;i++) if(!finalFacts.get(i).eventId().equals(events.get(i).eventId())) throw new AssertionError("Final identity");
        Arrays.sort(appendNanos);
        var sortedReads=readNanos.stream().mapToLong(Long::longValue).sorted().toArray();
        result.put("elapsedNanosIncludingBarriers",System.nanoTime()-begin);
        result.put("appendP95Nanos",appendNanos[(int)Math.ceil(COUNT*.95)-1]);
        result.put("readProjectionP95Nanos",sortedReads[(int)Math.ceil(sortedReads.length*.95)-1]);
        result.put("heapPools",pools.stream().map(p -> Map.of("name",p.getName(),"peakUsedBytes",p.getPeakUsage().getUsed())).toList());
        result.put("sumHeapPoolPeaksUpperBoundBytes",pools.stream().mapToLong(p -> p.getPeakUsage().getUsed()).sum());
        result.put("heapLimitBytes",Runtime.getRuntime().maxMemory());
        result.put("os",System.getProperty("os.name"));result.put("arch",System.getProperty("os.arch"));
        result.put("events",COUNT);result.put("readers",4);result.put("rounds",ROUNDS);
        result.put("verifiedPrefixReads",reads.get());result.put("partialPrefixReads",partialReads.get());
        result.put("realForceCallsIncludingHeader",forces.get());result.put("status","PASS");
        Files.write(root.resolve("result.json"),JsonEncoding.encode(result,32768,32768,64,"PROBE"));
    }
}
