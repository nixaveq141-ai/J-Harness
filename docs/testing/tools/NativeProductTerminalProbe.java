import io.jharness.api.CancellationToken;
import io.jharness.api.CommandRequest;
import io.jharness.api.ExecPath;
import io.jharness.process.LocalTerminalSession;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Exercise the actual product terminal with synthetic commands and owned tasks. */
public final class NativeProductTerminalProbe {
    private NativeProductTerminalProbe() {}

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")
                || !Boolean.getBoolean("jna.nounpack") || !Boolean.getBoolean("jna.nosys")) {
            throw new AssertionError("EXPLICIT_WINDOWS_SOURCE_CONFIGURATION_REQUIRED");
        }
        Path root = Files.createDirectory(Path.of(args[0]).toAbsolutePath().resolve("product-初始"));
        Path second = Files.createDirectory(root.resolve("second-切换"));
        for (char invalid : new char[] {'"', '%', '!', '\r', '\n'}) {
            if (second.toString().indexOf(invalid) >= 0) throw new AssertionError("PROBE_PATH_REJECTED");
        }
        Files.writeString(second.resolve("owned-cwd.marker"), "synthetic fixture");
        var env = new HashMap<String, String>();
        for (String key : new String[] {"SystemRoot", "ComSpec", "PATH", "TEMP", "TMP"}) {
            String value = System.getenv(key);
            if (value != null) env.put(key, value);
        }
        env.put("JH_MARK", "READY");
        try (var terminal = new LocalTerminalSession(new LocalWorkspace(root), env, List.of())) {
            String id = start(terminal, Duration.ofSeconds(20));
            terminal.write(id, "echo JH_PRODUCT_%JH_MARK%\r\n", CancellationToken.NONE);
            awaitLog(terminal, id, "JH_PRODUCT_READY");
            if (!Boolean.TRUE.equals(terminal.resize(id, 93, 27).get("pty"))) {
                throw new AssertionError("PRODUCT_RESIZE_REJECTED");
            }
            terminal.write(id, "cd /d \"" + second + "\"\r\n", CancellationToken.NONE);
            terminal.write(id, "if exist owned-cwd.marker echo JH_CWD_%JH_MARK%\r\n", CancellationToken.NONE);
            awaitLog(terminal, id, "JH_CWD_READY");
            terminal.write(id, "exit /b 0\r\n", CancellationToken.NONE);
            Map<String, Object> exited = awaitExit(terminal, id);
            if (!Integer.valueOf(0).equals(exited.get("exitCode"))) throw new AssertionError("PRODUCT_EXIT_REJECTED");
            String stopped = start(terminal, Duration.ofSeconds(20));
            terminal.stop(stopped);
            if (!"STOPPED".equals(awaitExit(terminal, stopped).get("state"))) {
                throw new AssertionError("PRODUCT_STOP_REJECTED");
            }
            String timed = start(terminal, Duration.ofMillis(600));
            if (!"TIMED_OUT".equals(awaitExit(terminal, timed).get("state"))) {
                throw new AssertionError("PRODUCT_TIMEOUT_REJECTED");
            }
        }
        System.out.println("SOURCE_NATIVE_PRODUCT_TERMINAL_INPUT_CWD_RESIZE_EXIT_STOP_TIMEOUT_PASS");
    }

    private static String start(LocalTerminalSession terminal, Duration timeout) {
        var row = terminal.start(new CommandRequest("owned-source-native-probe", CommandRequest.Mode.CMD,
                List.of("cmd /d /q /k"), new ExecPath("."), timeout),
                true, CancellationToken.NONE);
        if (!Boolean.TRUE.equals(row.get("pty"))) throw new AssertionError("PRODUCT_PTY_REQUIRED");
        return (String) row.get("id");
    }

    private static Map<String, Object> awaitExit(LocalTerminalSession terminal, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            var row = terminal.status(id);
            if (Boolean.FALSE.equals(row.get("alive")) && !"RUNNING".equals(row.get("state"))
                    && !"UNKNOWN".equals(row.get("state"))) {
                if (!List.of().equals(row.get("residualPids"))) throw new AssertionError("PRODUCT_RESIDUAL_PROCESS");
                return row;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("PRODUCT_EXIT_OR_CLEANUP_TIMEOUT");
    }

    private static void awaitLog(LocalTerminalSession terminal, String id, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (((String) terminal.log(id, 0, 65536).get("text")).contains(marker)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("PRODUCT_SYNTHETIC_HANDSHAKE_REJECTED");
    }
}
