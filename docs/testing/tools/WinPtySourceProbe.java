import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.pty4j.windows.winpty.WinPtyProcess;
import com.sun.jna.Native;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Trusted native ABI probe: no model output or project code is executed. */
public final class WinPtySourceProbe {
    public interface DirectoryApi extends Library {
        int winpty_get_current_directory(Pointer session, int length, Pointer buffer, PointerByReference error);
        void winpty_error_free(Pointer error);
    }
    private WinPtySourceProbe() {}

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("jna.nounpack") || !Boolean.getBoolean("jna.nosys")) {
            throw new AssertionError("FALLBACK_MUST_BE_DISABLED");
        }
        Native.setLastError(1701);
        if (Native.getLastError() != 1701) throw new AssertionError("SOURCE_JNI_REJECTED");
        Path root = Path.of(args[0]).toAbsolutePath();
        Path first = Files.createDirectory(root.resolve("cwd-first-初始"));
        Path second = Files.createDirectory(root.resolve("cwd-second-切换"));
        for (char invalid : new char[] {'"', '%', '!', '\r', '\n'}) {
            if (second.toString().indexOf(invalid) >= 0) throw new AssertionError("PROBE_PATH_REJECTED");
        }
        var env = new HashMap<String, String>();
        for (String key : new String[] {"SystemRoot", "ComSpec", "PATH", "TEMP", "TMP"}) {
            String value = System.getenv(key);
            if (value != null) env.put(key, value);
        }
        env.put("JH_MARK", "READY");
        var process = new PtyProcessBuilder(new String[] {System.getenv("ComSpec"), "/d", "/q", "/k"})
                .setDirectory(first.toString()).setEnvironment(env).setUseWinConPty(false)
                .setInitialColumns(100).setInitialRows(30).setRedirectErrorStream(true).start();
        var output = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = process.getInputStream().read(buffer)) >= 0) {
                    synchronized (output) {
                        if (output.length() + count > 131072) return;
                        output.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                    }
                }
            } catch (java.io.IOException ignored) {
                // Only the owned-process close path ends the read.
            }
        });
        try {
            if (!(process instanceof WinPtyProcess legacy)) throw new AssertionError("EXPECTED_WINPTY_BACKEND");
            DirectoryApi directoryApi = Native.load(Path.of(System.getProperty("pty4j.preferred.native.folder"),
                    "win", "x86-64", "winpty.dll").toString(), DirectoryApi.class);
            var error = new PointerByReference();
            if (directoryApi.winpty_get_current_directory(null, 0, null, error) != 0 || error.getValue() == null) {
                throw new AssertionError("INVALID_DIRECTORY_ARGUMENTS_ACCEPTED");
            }
            directoryApi.winpty_error_free(error.getValue());
            send(process, "echo JH_START_%JH_MARK%");
            await(output, "JH_START_READY");
            if (!Files.isSameFile(first, Path.of(legacy.getWorkingDirectory()))) throw new AssertionError("INITIAL_CWD_REJECTED");
            if (legacy.getConsoleProcessCount() < 2) throw new AssertionError("CONSOLE_PROCESS_LIST_REJECTED");
            send(process, "cd /d \"" + second + "\"");
            send(process, "echo JH_CHANGED_%JH_MARK%");
            await(output, "JH_CHANGED_READY");
            if (!Files.isSameFile(second, Path.of(legacy.getWorkingDirectory()))) throw new AssertionError("CHANGED_CWD_REJECTED");
            legacy.setWinSize(new WinSize(93, 27));
            if (legacy.getWinSize().getColumns() != 93 || legacy.getWinSize().getRows() != 27) {
                throw new AssertionError("RESIZE_REJECTED");
            }
            send(process, "exit /b 0");
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) throw new AssertionError("EXIT_REJECTED");
            System.out.println("SOURCE_WINPTY_CWD_PROCESS_LIST_RESIZE_AND_EXIT_PASS");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("OWNED_PROCESS_CLEANUP_REJECTED");
            }
            process.getInputStream().close();
            process.getOutputStream().close();
            reader.join(1000);
        }
    }

    private static void send(Process process, String command) throws Exception {
        process.getOutputStream().write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
    }

    private static void await(StringBuilder output, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            synchronized (output) {
                if (output.indexOf(marker) >= 0) return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("OWNED_COMMAND_HANDSHAKE_REJECTED");
    }
}
