package io.jharness.testkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Fault peer for client framing/backpressure tests, not a product SDK implementation. */
public final class SdkTransportPeer {
    private SdkTransportPeer() {}

    public static void main(String[] args) throws Exception {
        var ids = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        var slow = new AtomicInteger();
        var deferred = new ArrayList<String>();
        var workers = new ArrayList<Thread>();
        var methods = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
        try (var reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var matcher = ids.matcher(line);
                if (!matcher.find()) return;
                String id = matcher.group(1);
                var methodMatcher = methods.matcher(line);
                if (!methodMatcher.find()) return;
                String method = methodMatcher.group(1);
                if (method.equals("defer")) {
                    deferred.add(id);
                    continue;
                }
                if (method.equals("flush")) {
                    int count = deferred.size();
                    for (String late : deferred) reply(late, "{}");
                    deferred.clear();
                    reply(id, "{\"lateReplies\":" + count + "}");
                    continue;
                }
                if (method.equals("unknownid")) {
                    reply("not-pending", "{}");
                    continue;
                }
                if (method.equals("hold")) continue;
                if (method.equals("slow")) {
                    slow.incrementAndGet();
                    workers.add(
                            Thread.ofVirtual()
                                    .start(
                                            () -> {
                                                try {
                                                    Thread.sleep(700);
                                                    reply(id, "{}");
                                                } catch (InterruptedException ex) {
                                                    Thread.currentThread().interrupt();
                                                }
                                            }));
                    continue;
                }
                if (method.equals("duplicate")) {
                    System.out.println(
                            "{\"jsonrpc\":\"2.0\",\"id\":\""
                                    + id
                                    + "\",\"id\":\""
                                    + id
                                    + "\",\"result\":{}}");
                    System.out.flush();
                    continue;
                }
                if (method.equals("oversized")) {
                    reply(id, "{\"text\":\"" + "x".repeat(600000) + "\"}");
                    continue;
                }
                if (method.equals("stderr")) System.err.println(System.getenv("JHARNESS_API_KEY"));
                reply(
                        id,
                        method.equals("initialize")
                                ? "{\"protocolVersion\":\"1.0\"}"
                                : method.equals("stats")
                                        ? "{\"slowCalls\":" + slow.get() + "}"
                                        : "{}");
                if (method.equals("shutdown")) {
                    for (Thread worker : workers) worker.join(2000);
                    return;
                }
            }
        }
    }

    private static void reply(String id, String result) {
        System.out.println("{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"result\":" + result + "}");
        System.out.flush();
    }
}
