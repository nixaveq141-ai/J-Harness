package io.jharness.testkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Fault peer for client framing/backpressure tests, not a product SDK implementation. */
public final class SdkTransportPeer {
    private SdkTransportPeer() {}

    public static void main(String[] args) throws Exception {
        var ids = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        var slow = new AtomicInteger();
        try (var reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var matcher = ids.matcher(line);
                if (!matcher.find()) return;
                String id = matcher.group(1);
                if (line.contains("\"hold\"")) continue;
                if (line.contains("\"slow\"")) {
                    slow.incrementAndGet();
                    Thread.ofVirtual()
                            .start(
                                    () -> {
                                        try {
                                            Thread.sleep(700);
                                            reply(id, "{}");
                                        } catch (InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                        }
                                    });
                    continue;
                }
                if (line.contains("\"duplicate\"")) {
                    System.out.println(
                            "{\"jsonrpc\":\"2.0\",\"id\":\""
                                    + id
                                    + "\",\"id\":\""
                                    + id
                                    + "\",\"result\":{}}");
                    System.out.flush();
                    continue;
                }
                if (line.contains("\"oversized\"")) {
                    reply(id, "{\"text\":\"" + "x".repeat(600000) + "\"}");
                    continue;
                }
                if (line.contains("\"stderr\""))
                    System.err.println(System.getenv("JHARNESS_API_KEY"));
                reply(
                        id,
                        line.contains("\"initialize\"")
                                ? "{\"protocolVersion\":\"1.0\"}"
                                : line.contains("\"stats\"")
                                        ? "{\"slowCalls\":" + slow.get() + "}"
                                        : "{}");
                if (line.contains("\"shutdown\"")) return;
            }
        }
    }

    private static void reply(String id, String result) {
        System.out.println("{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"result\":" + result + "}");
        System.out.flush();
    }
}
