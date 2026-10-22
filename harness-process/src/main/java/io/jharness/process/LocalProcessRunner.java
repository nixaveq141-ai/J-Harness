package io.jharness.process;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Best-effort tracked tree cleanup, deliberately not an OS sandbox or Windows Job Object. */
public final class LocalProcessRunner implements ProcessRunner, AutoCloseable {
    private final LocalWorkspace workspace;
    private final ProcessLimits limits;
    private final Map<String, String> environment;
    private final SecretRedactor redactor;
    private final Semaphore slots;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Process> active = ConcurrentHashMap.newKeySet();
    private final Consumer<ProcessHandle> terminate;
    private final int secretTail;
    private final Object lifecycle = new Object();

    public LocalProcessRunner(
            LocalWorkspace workspace,
            ProcessLimits limits,
            Map<String, String> environment,
            List<String> secrets) {
        this(workspace, limits, environment, secrets, ProcessHandle::destroyForcibly);
    }

    /** Host-side failure injection seam; never available as a model argument. */
    public LocalProcessRunner(
            LocalWorkspace workspace,
            ProcessLimits limits,
            Map<String, String> environment,
            List<String> secrets,
            Consumer<ProcessHandle> terminate) {
        this.workspace = Objects.requireNonNull(workspace);
        this.limits = Objects.requireNonNull(limits);
        this.environment = validatedEnvironment(environment);
        this.redactor = new SecretRedactor(secrets);
        slots = new Semaphore(limits.maxConcurrent());
        this.terminate = Objects.requireNonNull(terminate);
        secretTail =
                secrets.stream().filter(Objects::nonNull).mapToInt(String::length).max().orElse(0);
    }

    /** Validate exactly the explicit host snapshot, never a later mutable-map view. */
    public static Map<String, String> validatedEnvironment(Map<String, String> environment) {
        Map<String, String> snapshot = Map.copyOf(environment);
        for (var entry : snapshot.entrySet())
            if (!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")
                    || entry.getValue().indexOf('\0') >= 0)
                throw new IllegalArgumentException("Invalid environment");
        return snapshot;
    }

    /** Minimal inherited variables needed by native build tools. No arbitrary host variables. */
    public static Map<String, String> buildEnvironment() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name :
                List.of(
                        "PATH",
                        "SystemRoot",
                        "WINDIR",
                        "JAVA_HOME",
                        "TEMP",
                        "TMP",
                        "HOME",
                        "USERPROFILE",
                        "LANG",
                        "LC_ALL",
                        "LC_CTYPE")) {
            String value = System.getenv(name);
            if (value != null) result.put(name, value);
        }
        if (!System.getProperty("os.name").startsWith("Windows"))
            result.putIfAbsent("LANG", "C.UTF-8");
        return Map.copyOf(result);
    }

    @Override
    public ToolResult execute(List<String> argv, ExecPath cwd, CancellationToken cancellation) {
        return execute(
                new CommandRequest(
                        UUID.randomUUID().toString(),
                        CommandRequest.Mode.ARGV,
                        argv,
                        cwd,
                        limits.timeout()),
                cancellation);
    }

    @Override
    public ToolResult execute(CommandRequest request, CancellationToken cancellation) {
        long start = System.nanoTime();
        if (closed.get())
            return notStarted(request, ToolStatus.FAILED, "PROCESS_SERVICE_CLOSED", start);
        if (cancellation.isCancelled())
            return notStarted(request, ToolStatus.CANCELLED, "CANCELLED", start);
        if (!slots.tryAcquire())
            return notStarted(request, ToolStatus.FAILED, "PROCESS_LIMIT", start);
        Process process = null;
        Collector stdout = null, stderr = null;
        Thread outThread = null, errThread = null;
        Tree tree = null;
        String reason = "EXIT";
        int exit = -1;
        boolean interrupted = false;
        try {
            Path cwd = workspace.commandDirectory(request.cwd());
            List<String> argv = nativeArgv(request);
            if (closed.get() || cancellation.isCancelled())
                return notStarted(request, ToolStatus.CANCELLED, "CANCELLED", start);
            ProcessBuilder builder = new ProcessBuilder(argv).directory(cwd.toFile());
            builder.environment().clear();
            builder.environment().putAll(environment);
            synchronized (lifecycle) {
                if (closed.get())
                    return notStarted(request, ToolStatus.FAILED, "PROCESS_SERVICE_CLOSED", start);
                process = builder.start();
                active.add(process);
            }
            tree = new Tree(process.toHandle(), limits.maxTreeProcesses(), terminate);
            process.getOutputStream().close(); // noninteractive service: stdin EOF
            stdout =
                    new Collector(
                            process.getInputStream(),
                            limits.outputBytes(),
                            limits.outputEncoding(),
                            secretTail);
            stderr =
                    new Collector(
                            process.getErrorStream(),
                            limits.outputBytes(),
                            limits.outputEncoding(),
                            secretTail);
            outThread = Thread.ofVirtual().name("process-stdout").start(stdout);
            errThread = Thread.ofVirtual().name("process-stderr").start(stderr);
            long budget = Math.min(request.timeout().toNanos(), limits.timeout().toNanos());
            while (true) {
                tree.sample();
                if (closed.get() || cancellation.isCancelled()) {
                    reason = "CANCELLED";
                    break;
                }
                if (System.nanoTime() - start >= budget) {
                    reason = "TIMEOUT";
                    break;
                }
                if (tree.exceeded) {
                    reason = "PROCESS_LIMIT";
                    break;
                }
                if (!process.isAlive()) {
                    exit = process.exitValue();
                    break;
                }
                Thread.sleep(10);
            }
        } catch (InterruptedException ex) {
            reason = "CANCELLED";
            interrupted = true;
        } catch (Exception ex) {
            reason = ex instanceof HarnessException h ? h.code() : "PROCESS_IO";
        } finally {
            if (tree != null) interrupted |= tree.cleanup();
            if (stdout != null) stdout.stop.set(true);
            if (stderr != null) stderr.stop.set(true);
            interrupted |= join(outThread) | join(errThread);
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                interrupted |= join(outThread) | join(errThread);
                active.remove(process);
            }
            slots.release();
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (process == null)
            return notStarted(
                    request,
                    reason.equals("PATH_DENIED") ? ToolStatus.DENIED : ToolStatus.FAILED,
                    reason,
                    start);
        if (exit < 0 && !process.isAlive()) exit = process.exitValue();
        List<Long> residual = tree == null ? List.of(process.pid()) : tree.residual();
        boolean readersStopped =
                (outThread == null || !outThread.isAlive())
                        && (errThread == null || !errThread.isAlive());
        boolean observedClean =
                residual.isEmpty()
                        && readersStopped
                        && tree != null
                        && !tree.inspectionFailed
                        && !tree.exceeded;
        if (!readersStopped) {
            if (stdout != null) {
                stdout.truncated = true;
                stdout.ioFailed = true;
            }
            if (stderr != null) {
                stderr.truncated = true;
                stderr.ioFailed = true;
            }
        }
        if (reason.equals("EXIT")
                && (stdout != null && stdout.ioFailed || stderr != null && stderr.ioFailed))
            reason = "OUTPUT_IO";
        ToolStatus status =
                !observedClean
                        ? ToolStatus.UNKNOWN
                        : reason.equals("CANCELLED")
                                ? ToolStatus.CANCELLED
                                : reason.equals("TIMEOUT")
                                        ? ToolStatus.TIMEOUT
                                        : reason.equals("EXIT")
                                                        && exit == 0
                                                        && stdout != null
                                                        && !stdout.ioFailed
                                                        && stderr != null
                                                        && !stderr.ioFailed
                                                ? ToolStatus.SUCCESS
                                                : ToolStatus.FAILED;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("argv", request.argv());
        value.put("mode", request.mode().name());
        value.put("cwd", request.cwd().value());
        value.put(
                "environment",
                Map.of(
                        "names",
                        environment.keySet().stream().sorted().toList(),
                        "values",
                        "omitted"));
        value.put("exitCode", exit);
        value.put("outputEncoding", limits.outputEncoding().name());
        value.put("stdout", stdout == null ? "" : redactor.text(stdout.text()));
        value.put("stderr", stderr == null ? "" : redactor.text(stderr.text()));
        value.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        value.put("timedOut", reason.equals("TIMEOUT"));
        value.put("cancelled", reason.equals("CANCELLED"));
        value.put(
                "truncated",
                stdout != null && stdout.truncated || stderr != null && stderr.truncated);
        value.put("cleanupStatus", observedClean ? "partial" : "failed");
        value.put("observedClean", observedClean);
        value.put("residualPids", residual);
        value.put(
                "trackedPids",
                tree == null
                        ? List.of(process.pid())
                        : tree.handles.values().stream().map(ProcessHandle::pid).toList());
        value.put(
                "limitations",
                "ProcessHandle snapshots only; rapidly detached/unobserved descendants cannot be enumerated or guaranteed terminated. No Job Object or OS sandbox.");
        value.put("sideEffectsMayHaveOccurred", true);
        value.put("sideEffectUncertain", !reason.equals("EXIT") || !observedClean);
        value.put("reason", reason);
        Map<String, Object> clean = redactor.sanitize(value);
        return new ToolResult(
                request.callId(),
                status,
                clean,
                Map.of(
                                "exitCode",
                                exit,
                                "stdout",
                                preview((String) clean.get("stdout")),
                                "stderr",
                                preview((String) clean.get("stderr")),
                                "timedOut",
                                reason.equals("TIMEOUT"),
                                "cancelled",
                                reason.equals("CANCELLED"),
                                "truncated",
                                clean.get("truncated"),
                                "cleanupStatus",
                                clean.get("cleanupStatus"),
                                "residualPids",
                                residual,
                                "reason",
                                reason.equals("EXIT") && exit != 0 ? "NONZERO_EXIT" : reason,
                                "sideEffectsMayHaveOccurred",
                                true)
                        .toString(),
                Map.of("exitCode", exit, "cleanupStatus", clean.get("cleanupStatus")),
                status == ToolStatus.SUCCESS
                        ? Optional.empty()
                        : Optional.of(
                                new ToolResult.ErrorInfo(
                                        status == ToolStatus.UNKNOWN
                                                ? "CLEANUP_INCOMPLETE"
                                                : reason.equals("EXIT") ? "NONZERO_EXIT" : reason,
                                        "Command did not complete successfully; inspect output and cleanup evidence")));
    }

    static List<String> nativeArgv(CommandRequest request) {
        boolean windows =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        if (request.mode() == CommandRequest.Mode.SH) {
            if (windows)
                throw new HarnessException(
                        "UNSUPPORTED_SHELL", "SH requires POSIX provider; WSL is not implicit");
            return List.of("/bin/sh", "-c", request.argv().getFirst());
        }
        if (request.mode() != CommandRequest.Mode.ARGV && !windows)
            throw new HarnessException(
                    "UNSUPPORTED_SHELL",
                    "Native CMD/PowerShell require Windows; WSL is opt-in future provider");
        if (request.mode() == CommandRequest.Mode.CMD)
            return List.of(
                    Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe").toString(),
                    "/d",
                    "/s",
                    "/c",
                    request.argv().getFirst());
        if (request.mode() == CommandRequest.Mode.POWERSHELL)
            return List.of(
                    Path.of(
                                    System.getenv("SystemRoot"),
                                    "System32",
                                    "WindowsPowerShell",
                                    "v1.0",
                                    "powershell.exe")
                            .toString(),
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    request.argv().getFirst());
        String executable = request.argv().getFirst().toLowerCase(Locale.ROOT);
        if (windows && (executable.endsWith(".cmd") || executable.endsWith(".bat")))
            throw new HarnessException(
                    "SHELL_REQUIRED", "Batch files require explicit CMD mode and approval");
        if (Set.of(
                        "cmd",
                        "cmd.exe",
                        "powershell",
                        "powershell.exe",
                        "pwsh",
                        "pwsh.exe",
                        "sh",
                        "bash")
                .contains(Path.of(executable).getFileName().toString()))
            throw new HarnessException(
                    "SHELL_REQUIRED", "Shell interpreters must use explicit Shell mode");
        return request.argv();
    }

    private static String preview(String text) {
        return text.length() <= 1024 ? text : text.substring(0, 1024) + "[MODEL PREVIEW TRUNCATED]";
    }

    private ToolResult notStarted(
            CommandRequest request, ToolStatus status, String code, long start) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("argv", request.argv());
        value.put("mode", request.mode().name());
        value.put("cwd", request.cwd().value());
        value.put(
                "environment",
                Map.of(
                        "names",
                        environment.keySet().stream().sorted().toList(),
                        "values",
                        "omitted"));
        value.put("exitCode", -1);
        value.put("stdout", "");
        value.put("stderr", "");
        value.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        value.put("timedOut", false);
        value.put("truncated", false);
        value.put("cleanupStatus", "not_started");
        value.put("residualPids", List.of());
        value.put("sideEffectUncertain", false);
        return new ToolResult(
                request.callId(),
                status,
                redactor.sanitize(value),
                code,
                Map.of(),
                Optional.of(new ToolResult.ErrorInfo(code, "Command body not started")));
    }

    private static boolean join(Thread thread) {
        if (thread == null) return false;
        try {
            thread.join(1000);
            return false;
        } catch (InterruptedException ex) {
            return true;
        }
    }

    private static void closeQuietly(Closeable stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            /* result reports incomplete collection */
        }
    }

    @Override
    public void close() {
        List<Process> snapshot;
        synchronized (lifecycle) {
            closed.set(true);
            snapshot = List.copyOf(active);
        }
        boolean interrupted = false;
        for (Process process : snapshot)
            interrupted |=
                    new Tree(process.toHandle(), limits.maxTreeProcesses(), terminate).cleanup();
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static final class Collector implements Runnable {
        private final InputStream stream;
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final AtomicBoolean stop = new AtomicBoolean();
        private volatile boolean truncated;
        private volatile boolean ioFailed;
        private final Charset encoding;
        private final int secretTail;

        Collector(InputStream stream, int limit, Charset encoding, int secretTail) {
            this.stream = stream;
            this.limit = limit;
            this.encoding = encoding;
            this.secretTail = secretTail;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int finalDrain = 0;
                do {
                    if (stop.get() && (finalDrain += buffer.length) > limit + 8192) {
                        truncated = true;
                        break;
                    }
                    int available = stream.available();
                    if (available > 0) {
                        int count = stream.read(buffer, 0, Math.min(buffer.length, available));
                        if (count > 0) {
                            synchronized (bytes) {
                                int retained = Math.min(count, limit - bytes.size());
                                bytes.write(buffer, 0, retained);
                                if (retained < count) truncated = true;
                            }
                        }
                    } else if (stop.get()) break;
                    else Thread.sleep(2);
                } while (true);
            } catch (IOException | InterruptedException ex) {
                truncated = true;
                ioFailed = true;
            }
        }

        String text() {
            // Never expose an incomplete final token/line where a configured secret may be clipped.
            String text;
            synchronized (bytes) {
                text = bytes.toString(encoding);
            }
            if (truncated) {
                text = text.substring(0, Math.max(0, text.length() - secretTail));
                int line = text.lastIndexOf('\n');
                return (line < 0 ? "" : text.substring(0, line + 1)) + "[OUTPUT TRUNCATED]";
            }
            return text;
        }
    }

    private static final class Tree {
        private final Map<ProcessHandle, ProcessHandle> handles = new LinkedHashMap<>();
        private final int limit;
        private boolean exceeded, inspectionFailed;

        private final Consumer<ProcessHandle> terminate;

        Tree(ProcessHandle root, int limit, Consumer<ProcessHandle> terminate) {
            handles.put(root, root);
            this.limit = limit;
            this.terminate = terminate;
        }

        void sample() {
            try {
                for (ProcessHandle parent : List.copyOf(handles.values())) {
                    try (var descendants = parent.descendants()) {
                        descendants
                                .limit(limit + 1L)
                                .forEach(
                                        child -> {
                                            if (handles.size() < limit + 1)
                                                handles.putIfAbsent(child, child);
                                            else exceeded = true;
                                        });
                    }
                }
                if (handles.size() > limit) exceeded = true;
            } catch (RuntimeException ex) {
                inspectionFailed = true;
            }
        }

        boolean cleanup() {
            boolean interrupted = false;
            sample();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            do {
                sample();
                for (ProcessHandle handle : List.copyOf(handles.values())) {
                    try {
                        if (handle.isAlive()) terminate.accept(handle);
                    } catch (RuntimeException ex) {
                        inspectionFailed = true;
                    }
                }
                if (residual().isEmpty()) break;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            } while (System.nanoTime() < deadline);
            return interrupted;
        }

        List<Long> residual() {
            List<Long> alive = new ArrayList<>();
            for (ProcessHandle handle : handles.values()) {
                try {
                    if (handle.isAlive()) alive.add(handle.pid());
                } catch (RuntimeException ex) {
                    inspectionFailed = true;
                    alive.add(handle.pid());
                }
            }
            return List.copyOf(alive);
        }
    }
}
