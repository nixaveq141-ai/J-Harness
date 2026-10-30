import com.pty4j.PtyProcessBuilder;
import com.sun.jna.Native;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        if (args.length == 1 && args[0].equals("pty")) {
            byte[] marker = "JHARNESS_NATIVE_SOURCE_PTY".getBytes(StandardCharsets.US_ASCII);
            var process = new PtyProcessBuilder(new String[] {"/bin/sh", "-c", "printf JHARNESS_NATIVE_SOURCE_PTY; read ack"})
                    .setEnvironment(Map.of("PATH", "/usr/bin:/bin"))
                    .start();
            try {
                byte[] actual = process.getInputStream().readNBytes(marker.length);
                if (!java.util.Arrays.equals(marker, actual)) {
                    throw new AssertionError("SOURCE_PTY_BEHAVIOR_REJECTED");
                }
                process.getOutputStream().write("ack\n".getBytes(StandardCharsets.US_ASCII));
                process.getOutputStream().flush();
                if (!process.waitFor(5, TimeUnit.SECONDS)
                        || process.exitValue() != 0) {
                    throw new AssertionError("SOURCE_PTY_BEHAVIOR_REJECTED");
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("SOURCE_PTY_CLEANUP_REJECTED");
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
