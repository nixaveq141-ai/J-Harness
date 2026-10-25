package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class R5TransportBoundsTest {
    public static final class OutputProbe {
        public static void main(String[] args) throws Exception {
            System.err.write(new byte[65537]);
            System.err.flush();
            Thread.sleep(30000);
        }
    }

    @Test
    void failedOutputPumpStopsTheLaunchedChildBeforeItsDeadlineAndRetainsUnknown() {
        var reply =
                assertTimeoutPreemptively(
                        Duration.ofSeconds(15),
                        () ->
                                new NativeCommandTransport()
                                        .call(
                                                List.of(
                                                        java.nio.file.Path.of(
                                                                        System.getProperty(
                                                                                "java.home"),
                                                                        "bin",
                                                                        System.getProperty(
                                                                                                "os.name")
                                                                                        .startsWith(
                                                                                                "Windows")
                                                                                ? "java.exe"
                                                                                : "java")
                                                                .toString(),
                                                        "-cp",
                                                        System.getProperty("java.class.path"),
                                                        OutputProbe.class.getName()),
                                                new byte[0],
                                                Duration.ofSeconds(30),
                                                CancellationToken.NONE));
        assertTrue(reply.started());
        assertTrue(reply.uncertain());
        assertEquals("CONTROL_EXCHANGE_FAILED", reply.failureCode());
    }

    @Test
    void replyRejectsOversizeBeforeCloningAndKeepsSnapshotOwnership() {
        byte[] bytes = {1, 2};
        var reply = new CommandTransport.Reply(0, bytes, new byte[0], true, false);
        bytes[0] = 9;
        assertEquals(1, reply.stdout()[0]);
        var read = reply.stdout();
        read[0] = 8;
        assertEquals(1, reply.stdout()[0]);
        assertThrows(
                HarnessException.class,
                () -> new CommandTransport.Reply(0, new byte[4194305], new byte[0], true, false));
        assertThrows(
                HarnessException.class,
                () -> new CommandTransport.Reply(0, new byte[0], new byte[65537], true, false));
    }

    @Test
    void invalidTimeoutsAreRejectedBeforeAnyCommandLaunch() {
        for (var timeout : List.of(Duration.ZERO, Duration.ofMillis(-1), Duration.ofHours(2)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new NativeCommandTransport()
                                    .call(
                                            List.of("must-not-launch"),
                                            new byte[0],
                                            timeout,
                                            CancellationToken.NONE));
    }

    @Test
    void encodedOutputAlwaysFitsTheDecoderTokenAndDepthBudgets() {
        Object nested = Map.of();
        for (int i = 0; i < 65; i++) nested = List.of(nested);
        final Object deep = nested;
        assertThrows(HarnessException.class, () -> BoundedJson.encode(deep));
        assertThrows(
                HarnessException.class, () -> BoundedJson.encode(Collections.nCopies(100001, 0)));
        var value = Map.<String, Object>of("text", "中文😀", "nested", List.of(true, 4));
        assertEquals(value, BoundedJson.decode(BoundedJson.encode(value)));
    }

    @Test
    void configurationIsBoundedAndContentOffsetsExplicitlyUseUtf16() {
        assertThrows(
                HarnessException.class,
                () -> new SdkConfiguration("", Set.of(), Map.of("oversize", "x".repeat(65536))));
        var names = new HashSet<String>();
        for (int i = 0; i < 257; i++) names.add("tool" + i);
        assertThrows(
                IllegalArgumentException.class, () -> new SdkConfiguration("", names, Map.of()));
        var page = ContentPages.page("😀\nlast", 3, "");
        assertEquals("last", page.get("text"));
        assertEquals(2L, page.get("startLine"));
        assertEquals("UTF-16 code units", page.get("offsetUnit"));
        assertThrows(HarnessException.class, () -> ContentPages.page("😀\nlast", 1, ""));
    }
}
