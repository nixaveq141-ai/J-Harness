package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import io.jharness.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class R1NativeWorkerTest {
    static List<Map<String, String>> operation(String action) {
        return List.of(
                Map.of("action", action, "target", "J-Harness/synthetic-worker-test", "value", ""));
    }

    static int invoke(
            byte[] bytes,
            java.util.function.Supplier<NativeCredentials.Vault> factory,
            ByteArrayOutputStream out) {
        var source =
                new ByteArrayInputStream(bytes) {
                    @Override
                    public byte[] readNBytes(int length) {
                        return bytes;
                    }
                };
        return NativeCredentials.execute(source, new PrintStream(out), factory);
    }

    @Test
    void workerClearsReadBufferOnSuccessAndNativeLinkFailureWithoutDiagnostics() {
        for (boolean unavailable : List.of(false, true)) {
            byte[] bytes = Json.text(operation("probe")).getBytes(StandardCharsets.UTF_8);
            var calls = new ArrayList<String>();
            var output = new ByteArrayOutputStream();
            int exit =
                    invoke(
                            bytes,
                            () -> {
                                if (unavailable)
                                    throw new UnsatisfiedLinkError("synthetic-private-path");
                                return (action, target, value) -> {
                                    calls.add(action);
                                    return "available";
                                };
                            },
                            output);
            assertArrayEquals(new byte[bytes.length], bytes);
            assertEquals(unavailable ? 7 : 0, exit);
            assertEquals(unavailable ? List.of() : List.of("probe"), calls);
            assertFalse(output.toString(StandardCharsets.UTF_8).contains("synthetic-private-path"));
        }
    }

    @Test
    void invalidBatchNeverLoadsNativeAndMutatingFailureIsUnknown() {
        var output = new ByteArrayOutputStream();
        byte[] invalid = "[{\"action\":\"write\"}]".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                6,
                invoke(
                        invalid,
                        () -> {
                            throw new AssertionError("must not load");
                        },
                        output));
        assertArrayEquals(new byte[invalid.length], invalid);
        byte[] valid = Json.text(operation("delete")).getBytes(StandardCharsets.UTF_8);
        assertEquals(
                8,
                invoke(
                        valid,
                        () ->
                                (a, t, v) -> {
                                    throw new IllegalStateException("synthetic-secret");
                                },
                        output));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertArrayEquals(new byte[valid.length], valid);
    }

    static final class FakeProcess extends Process {
        final int code;
        final boolean timeout;
        final AtomicBoolean killed = new AtomicBoolean();
        final ByteArrayOutputStream input = new ByteArrayOutputStream();

        FakeProcess(int code, boolean timeout) {
            this.code = code;
            this.timeout = timeout;
        }

        public OutputStream getOutputStream() {
            return input;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        public int waitFor() {
            return code;
        }

        public boolean waitFor(long amount, TimeUnit unit) {
            return !timeout;
        }

        public int exitValue() {
            return code;
        }

        public void destroy() {
            killed.set(true);
        }

        public Process destroyForcibly() {
            destroy();
            return this;
        }
    }

    @Test
    void parentClassifiesReadTimeoutAndUnknownMutationWithoutRawException() {
        for (String action : List.of("probe", "read", "write", "delete")) {
            var process = new FakeProcess(0, true);
            var error =
                    assertThrows(
                            IOException.class,
                            () -> SystemCredentials.exchange(process, operation(action)));
            assertEquals(
                    Set.of("write", "delete").contains(action)
                            ? "CREDENTIAL_UPDATE_UNKNOWN"
                            : "CREDENTIAL_WORKER_TIMEOUT",
                    error.getMessage());
            assertTrue(process.killed.get());
            assertNull(error.getCause());
        }
        var process = new FakeProcess(8, false);
        assertEquals(
                "CREDENTIAL_UPDATE_UNKNOWN",
                assertThrows(
                                IOException.class,
                                () -> SystemCredentials.exchange(process, operation("write")))
                        .getMessage());
        assertTrue(process.killed.get());
    }

    @Test
    void corruptWindowsBlobIsBoundedAndFreedAndProbeNeverMutates() {
        for (int size : List.of(-1, 1025, 1)) {
            var frees = new AtomicInteger();
            var mutations = new AtomicInteger();
            var credential = new NativeCredentials.WindowsCredential();
            credential.size = size;
            credential.blob = null;
            credential.write();
            var api =
                    new NativeCredentials.WindowsApi() {
                        public boolean CredReadW(
                                WString target, int type, int flags, PointerByReference result) {
                            result.setValue(credential.getPointer());
                            return true;
                        }

                        public boolean CredWriteW(
                                NativeCredentials.WindowsCredential value, int flags) {
                            mutations.incrementAndGet();
                            return true;
                        }

                        public boolean CredDeleteW(WString target, int type, int flags) {
                            mutations.incrementAndGet();
                            return true;
                        }

                        public void CredFree(Pointer pointer) {
                            frees.incrementAndGet();
                        }
                    };
            try (var vault = new NativeCredentials.WindowsVault(api)) {
                assertThrows(
                        IllegalStateException.class,
                        () -> vault.run("read", "J-Harness/synthetic-worker-test", ""));
                assertEquals(1, frees.get());
                assertEquals("available", vault.run("probe", "J-Harness/probe", ""));
                assertEquals(2, frees.get());
                assertEquals(0, mutations.get());
            }
        }
    }
}
