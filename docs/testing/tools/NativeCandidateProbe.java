import com.pty4j.PtyProcessBuilder;
import com.sun.jna.Native;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Trusted source-build probe; never compiles or executes model output. */
public final class NativeCandidateProbe {
    private NativeCandidateProbe() {}

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("jna.nounpack") || !Boolean.getBoolean("jna.nosys")) {
            throw new AssertionError("NATIVE_FALLBACK_MUST_BE_DISABLED");
        }
        Native.setLastError(1701);
        if (Native.getLastError() != 1701) {
            throw new AssertionError("SOURCE_JNI_CALL_REJECTED");
        }
        if (args.length >= 1 && args[0].equals("pty")) {
            byte[] marker = "JHARNESS_NATIVE_SOURCE_PTY".getBytes(StandardCharsets.US_ASCII);
            var process = new PtyProcessBuilder(new String[] {"/bin/sh", "-c",
                    "printf JHARNESS_NATIVE_SOURCE_PTY; IFS= read -r ack; test \"$ack\" = ack && printf JHARNESS_NATIVE_ACK"})
                    .setEnvironment(Map.of("PATH", "/usr/bin:/bin"))
                    .start();
            Throwable primary = null;
            try {
                byte[] actual = process.getInputStream().readNBytes(marker.length);
                if (!java.util.Arrays.equals(marker, actual)) {
                    throw new AssertionError("SOURCE_PTY_BEHAVIOR_REJECTED");
                }
                process.getOutputStream().write("ack\n".getBytes(StandardCharsets.US_ASCII));
                process.getOutputStream().flush();
                var acknowledged = new AtomicBoolean();
                Thread reader = Thread.ofVirtual().name("probe-ack-reader").unstarted(() -> {
                    try {
                        var bytes = new java.io.ByteArrayOutputStream();
                        for (int i = 0; i < 128; i++) {
                            int value = process.getInputStream().read();
                            if (value < 0) break;
                            bytes.write(value);
                            if (bytes.toString(StandardCharsets.US_ASCII).contains("JHARNESS_NATIVE_ACK")) {
                                acknowledged.set(true);
                                break;
                            }
                        }
                    } catch (java.io.IOException ignored) {
                        // Closed by the owned-process cleanup path; no acknowledgement.
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                if (args.length == 2 && args[1].equals("defer-drain")) {
                    System.out.println("PTY_EXIT_BEFORE_DRAIN=" + process.waitFor(500, TimeUnit.MILLISECONDS));
                }
                reader.start();
                if (!process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                        || process.exitValue() != 0) {
                    System.out.println("PTY_CHILD_ACKNOWLEDGED=" + acknowledged.get());
                    var status = new ProcessBuilder("/bin/ps", "-o", "state=", "-p", Long.toString(process.pid())).start();
                    if (status.waitFor(1, TimeUnit.SECONDS)) {
                        String state = new String(status.getInputStream().readNBytes(16), StandardCharsets.US_ASCII).trim();
                        System.out.println("PTY_OS_STATE=" + (state.matches("[A-Z?+<>NslsWX]{0,8}") ? state : "REJECTED"));
                    } else {
                        status.destroyForcibly();
                    }
                    System.out.println("PTY_TIMEOUT_OS_ALIVE="
                            + ProcessHandle.of(process.pid()).map(ProcessHandle::isAlive).orElse(false));
                    Thread.getAllStackTraces().values().stream()
                            .filter(stack -> java.util.Arrays.stream(stack)
                                    .anyMatch(frame -> frame.getClassName().startsWith("com.pty4j.")))
                            .limit(4)
                            .forEach(stack -> java.util.Arrays.stream(stack).limit(12)
                                    .forEach(frame -> System.out.println("PTY_WAIT_FRAME="
                                            + frame.getClassName() + "." + frame.getMethodName())));
                    throw new AssertionError("SOURCE_PTY_BEHAVIOR_REJECTED");
                }
            } catch (Exception | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        var cleanup = new AssertionError("SOURCE_PTY_CLEANUP_REJECTED");
                        if (primary == null) {
                            throw cleanup;
                        }
                        primary.addSuppressed(cleanup);
                    }
                }
                process.getInputStream().close();
                process.getOutputStream().close();
                process.getErrorStream().close();
            }
            System.out.println("SOURCE_PTY_PASS");
        }
        System.out.println("SOURCE_JNI_PASS");
    }
}
