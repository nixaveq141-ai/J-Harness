import com.sun.jna.Native;
import java.nio.file.Path;

/** Native ARM64 component check using the existing owned ConPTY integration probe. */
public final class Arm64ConPtySourceProbe {
    private Arm64ConPtySourceProbe() {}

    public static void main(String[] args) throws Exception {
        String arch = System.getProperty("os.arch");
        if (!arch.equals("aarch64") && !arch.equals("arm64")) throw new AssertionError("ARM64_JVM_REQUIRED");
        if (args[1].equals("missing-jni")) {
            try {
                Native.getLastError();
            } catch (UnsatisfiedLinkError expected) {
                System.out.println("PASS missing exact ARM64 source JNI refused");
                return;
            }
            throw new AssertionError("MISSING_JNI_WAS_ACCEPTED");
        }
        if (Native.POINTER_SIZE != 8 || Native.LONG_SIZE != 4 || Native.WCHAR_SIZE != 2) {
            throw new AssertionError("ARM64_WINDOWS_JNI_ABI_REQUIRED");
        }
        if (!Path.of(System.getProperty("jna.boot.library.path")).isAbsolute()) throw new AssertionError("EXACT_NATIVE_PATH_REQUIRED");
        try {
            WinPtySourceProbe.main(new String[] {args[0], "conpty"});
        } catch (AssertionError expected) {
            if (args[1].equals("missing-conpty") && "EXPECTED_SOURCE_BUNDLED_CONPTY".equals(expected.getMessage())) {
                System.out.println("PASS missing bundled ARM64 ConPTY refused as source candidate");
                return;
            }
            throw expected;
        }
        if (!args[1].equals("present")) throw new AssertionError("MISSING_CONPTY_WAS_ACCEPTED");
        System.out.println("PASS ARM64 Java exact source ConPTY JNI, cwd, process list, resize and exit");
    }
}
