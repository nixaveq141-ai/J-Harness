package io.jharness.process;

import com.pty4j.*;
import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Bounded real PTY/background tasks; lifetime is the owning CLI/REPL session. */
public final class LocalTerminalSession implements TerminalSession {
    private final LocalWorkspace workspace;
    private final Map<String, String> environment;
    private final int hold;
    private final List<String> secrets;
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private final ArtifactStore artifacts;
    private final String session;
    private java.util.function.Consumer<List<Map<String, Object>>> closeObserver = states -> {};

    public void onClose(java.util.function.Consumer<List<Map<String, Object>>> observer) {
        closeObserver = Objects.requireNonNull(observer);
    }

    public LocalTerminalSession(
            LocalWorkspace workspace, Map<String, String> environment, List<String> secrets) {
        this(workspace, environment, secrets, null, null);
    }

    public LocalTerminalSession(
            LocalWorkspace workspace,
            Map<String, String> environment,
            List<String> secrets,
            ArtifactStore artifacts,
            String session) {
        this.artifacts = artifacts;
        this.session = session;
        this.workspace = Objects.requireNonNull(workspace);
        this.environment = LocalProcessRunner.validatedEnvironment(environment);
        this.secrets =
                secrets.stream()
                        .filter(s -> s != null && !s.isEmpty())
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList();
        hold = secrets.stream().filter(Objects::nonNull).mapToInt(String::length).max().orElse(0);
    }

    @Override
    public synchronized Map<String, Object> start(
            CommandRequest request, boolean pty, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        if (closed) throw error("TERMINAL_CLOSED");
        if (tasks.size() >= 32
                || tasks.values().stream().filter(t -> t.process.isAlive()).count() >= 4)
            throw error("TERMINAL_LIMIT");
        try {
            Path cwd = workspace.commandDirectory(request.cwd());
            List<String> argv = LocalProcessRunner.nativeArgv(request);
            Process process;
            if (pty)
                process =
                        new PtyProcessBuilder(argv.toArray(String[]::new))
                                .setDirectory(cwd.toString())
                                .setEnvironment(environment)
                                .setUseWinConPty(true)
                                .setInitialColumns(100)
                                .setInitialRows(30)
                                .setRedirectErrorStream(true)
                                .start();
            else {
                ProcessBuilder builder =
                        new ProcessBuilder(argv).directory(cwd.toFile()).redirectErrorStream(true);
                builder.environment().clear();
                builder.environment().putAll(environment);
                process = builder.start();
            }
            String id = UUID.randomUUID().toString();
            Task task = new Task(id, process, pty, request.timeout().toNanos(), request.callId());
            task.begin();
            tasks.put(id, task);
            return task.view();
        } catch (IOException | LinkageError | IllegalArgumentException ex) {
            throw error(pty ? "PTY_UNAVAILABLE" : "PROCESS_IO");
        }
    }

    private Task task(String id) {
        Task task = tasks.get(id);
        if (task == null) throw error("TERMINAL_NOT_FOUND");
        return task;
    }

    @Override
    public Map<String, Object> status(String id) {
        return task(id).view();
    }

    @Override
    public Map<String, Object> log(String id, long offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 65536) throw error("INVALID_TERMINAL_PAGE");
        Task task = task(id);
        synchronized (task) {
            String clean = task.output.toString();
            long first = task.dropped;
            if (offset < first) throw error("TERMINAL_LOG_ROTATED");
            int start = (int) Math.min(clean.length(), offset - first);
            int end = (int) Math.min(clean.length(), (long) start + limit);
            return Map.of(
                    "id",
                    id,
                    "offset",
                    first + start,
                    "nextOffset",
                    first + end,
                    "firstOffset",
                    first,
                    "text",
                    clean.substring(start, end),
                    "rotated",
                    first > 0);
        }
    }

    @Override
    public Map<String, Object> write(String id, String text, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        if (text.length() > 65536 || text.indexOf('\0') >= 0) throw error("INVALID_TERMINAL_INPUT");
        Task task = task(id);
        if (!task.state.equals("RUNNING") || !task.process.isAlive())
            throw error("TERMINAL_EXITED");
        if (!task.inputSlot.tryAcquire()) throw error("TERMINAL_INPUT_BUSY");
        CompletableFuture<Void> sent = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("terminal-input")
                .start(
                        () -> {
                            try {
                                task.process
                                        .getOutputStream()
                                        .write(text.getBytes(StandardCharsets.UTF_8));
                                task.process.getOutputStream().flush();
                                sent.complete(null);
                            } catch (IOException | RuntimeException ex) {
                                sent.completeExceptionally(ex);
                            } finally {
                                task.inputSlot.release();
                            }
                        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                if (closed || cancellation.isCancelled() || System.nanoTime() >= deadline) {
                    task.stop("STOPPED");
                    throw error("TERMINAL_INPUT_UNKNOWN");
                }
                try {
                    sent.get(25, TimeUnit.MILLISECONDS);
                    return task.view();
                } catch (TimeoutException ignored) {
                    /* cancellation and bounded input wait */
                }
            }
        } catch (InterruptedException ex) {
            task.stop("STOPPED");
            Thread.currentThread().interrupt();
            throw error("TERMINAL_INPUT_UNKNOWN");
        } catch (ExecutionException ex) {
            task.stop("STOPPED");
            throw error("TERMINAL_INPUT_UNKNOWN");
        }
    }

    @Override
    public Map<String, Object> resize(String id, int columns, int rows) {
        if (columns < 10 || columns > 500 || rows < 5 || rows > 200)
            throw error("INVALID_TERMINAL_SIZE");
        Task task = task(id);
        if (!(task.process instanceof PtyProcess pty)) throw error("UNSUPPORTED_TERMINAL");
        pty.setWinSize(new WinSize(columns, rows));
        return task.view();
    }

    @Override
    public Map<String, Object> stop(String id) {
        Task task = task(id);
        task.stop("STOPPED");
        return task.view();
    }

    @Override
    public List<Map<String, Object>> states() {
        return tasks.values().stream()
                .map(Task::view)
                .sorted(Comparator.comparing(m -> (String) m.get("id")))
                .toList();
    }

    @Override
    public void stopAll() {
        tasks.values()
                .forEach(
                        t -> {
                            t.stop("STOPPED");
                            t.drain();
                            t.persist();
                        });
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        stopAll();
        closeObserver.accept(states());
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }

    private final class Task {
        final String id;
        final String sourceCall;
        final Semaphore inputSlot = new Semaphore(1);
        final ProcessHandle rootHandle;
        volatile ArtifactStore.Artifact artifact;
        final Process process;
        final boolean pty;
        final long timeout, start = System.nanoTime();
        final String startedAt = Instant.now().toString();
        final StringBuilder output = new StringBuilder();
        final StringBuilder pendingOutput = new StringBuilder();
        final Map<Long, ProcessHandle> observed = new ConcurrentHashMap<>();
        volatile String state = "RUNNING";
        volatile boolean outputFailed;
        volatile boolean terminationUnconfirmed;
        volatile long dropped;
        Thread reader, watcher;

        Task(String id, Process process, boolean pty, long timeout, String sourceCall) {
            this.sourceCall = sourceCall;
            this.rootHandle = ProcessHandle.of(process.pid()).orElse(null);
            this.id = id;
            this.process = process;
            this.pty = pty;
            this.timeout = timeout;
        }

        void begin() {
            if (rootHandle != null) observed.put(process.pid(), rootHandle);
            reader =
                    Thread.ofVirtual()
                            .name("terminal-output")
                            .start(
                                    () -> {
                                        try (Reader input =
                                                new InputStreamReader(
                                                        process.getInputStream(),
                                                        StandardCharsets.UTF_8)) {
                                            char[] buffer = new char[2048];
                                            int read;
                                            while ((read = input.read(buffer)) >= 0)
                                                synchronized (this) {
                                                    pendingOutput.append(buffer, 0, read);
                                                    publishOutput(false);
                                                }
                                        } catch (IOException ex) {
                                            outputFailed = process.isAlive();
                                        } finally {
                                            synchronized (this) {
                                                publishOutput(true);
                                            }
                                        }
                                    });
            watcher =
                    Thread.ofVirtual()
                            .name("terminal-watch")
                            .start(
                                    () -> {
                                        try {
                                            while (process.isAlive()) {
                                                if (rootHandle != null)
                                                    rootHandle
                                                            .descendants()
                                                            .limit(129)
                                                            .forEach(
                                                                    handle ->
                                                                            observed.putIfAbsent(
                                                                                    handle.pid(),
                                                                                    handle));
                                                if (observed.size() > 128) {
                                                    stop("PROCESS_LIMIT");
                                                    return;
                                                }
                                                if (System.nanoTime() - start > timeout) {
                                                    stop("TIMED_OUT");
                                                    return;
                                                }
                                                Thread.sleep(20);
                                            }
                                            boolean finish;
                                            synchronized (this) {
                                                finish = state.equals("RUNNING");
                                                if (finish) state = "EXITED";
                                            }
                                            if (finish) {
                                                drain();
                                                cleanup();
                                                persist();
                                            }
                                        } catch (InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                            stop("STOPPED");
                                        }
                                    });
        }

        /** Publish an immutable sanitized prefix; offsets never count unresolved raw bytes. */
        void publishOutput(boolean ended) {
            int at = 0;
            int boundary =
                    ended ? pendingOutput.length() : Math.max(0, pendingOutput.length() - hold);
            while (at < boundary) {
                String matched = null;
                for (String secret : secrets) {
                    if (pendingOutput.indexOf(secret, at) == at) {
                        matched = secret;
                        break;
                    }
                }
                if (matched != null) {
                    output.append("[REDACTED]");
                    at += matched.length();
                    continue;
                }
                if (ended) {
                    String tail = pendingOutput.substring(at);
                    if (secrets.stream().anyMatch(secret -> secret.startsWith(tail))) break;
                } else if (at + 1 == pendingOutput.length()
                        && Character.isHighSurrogate(pendingOutput.charAt(at))) break;
                output.append(pendingOutput.charAt(at++));
            }
            pendingOutput.delete(0, at);
            if (ended) pendingOutput.setLength(0);
            int remove = Math.max(0, output.length() - 256 * 1024);
            if (remove > 0) {
                if (remove < output.length() && Character.isLowSurrogate(output.charAt(remove)))
                    remove++;
                output.delete(0, remove);
                dropped += remove;
            }
        }

        void cleanup() {
            observed.values().stream()
                    .filter(ProcessHandle::isAlive)
                    .forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((process.isAlive()
                            || observed.values().stream().anyMatch(ProcessHandle::isAlive))
                    && System.nanoTime() < until) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            terminationUnconfirmed = !ProcessExit.await(process, until);
        }

        void stop(String reason) {
            synchronized (this) {
                if (!state.equals("RUNNING")) return;
                state = reason;
            }
            if (rootHandle != null)
                rootHandle
                        .descendants()
                        .limit(128)
                        .forEach(handle -> observed.putIfAbsent(handle.pid(), handle));
            cleanup();
            try {
                process.getOutputStream().close();
                process.getInputStream().close();
                process.getErrorStream().close();
            } catch (IOException ignored) {
                /* report residuals below */
            }
            drain();
            persist();
        }

        void drain() {
            if (reader != null && reader != Thread.currentThread())
                try {
                    reader.join(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
        }

        synchronized void persist() {
            if (reader != null && reader.isAlive()) {
                outputFailed = true;
                return;
            }
            if (artifact != null || artifacts == null) return;
            try {
                artifact = artifacts.put(session, output.toString());
            } catch (IOException | RuntimeException ex) {
                outputFailed = true;
            }
        }

        Map<String, Object> view() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("sourceCall", sourceCall);
            if (artifact != null)
                result.put(
                        "logArtifact",
                        Map.of(
                                "id",
                                artifact.id(),
                                "bytes",
                                artifact.bytes(),
                                "sha256",
                                artifact.sha256()));
            result.put("pid", process.pid());
            result.put("pty", pty);
            boolean residual =
                    !state.equals("RUNNING")
                            && (terminationUnconfirmed
                                    || process.isAlive()
                                    || observed.values().stream().anyMatch(ProcessHandle::isAlive));
            result.put("state", residual ? "UNKNOWN" : state);
            result.put("startedAt", startedAt);
            result.put("alive", process.isAlive());
            if (!process.isAlive()) result.put("exitCode", process.exitValue());
            result.put("cleanupStatus", "partial");
            result.put(
                    "observation",
                    "sampled ProcessHandle tree; detached unseen processes are not guaranteed");
            var residualPids =
                    new ArrayList<>(
                            observed.values().stream()
                                    .filter(ProcessHandle::isAlive)
                                    .map(ProcessHandle::pid)
                                    .toList());
            if (terminationUnconfirmed && !residualPids.contains(process.pid()))
                residualPids.add(process.pid());
            result.put("residualPids", residualPids.stream().sorted().toList());
            result.put("outputFailed", outputFailed);
            result.put("outputDrained", reader == null || !reader.isAlive());
            result.put("firstOffset", dropped);
            return Map.copyOf(result);
        }
    }
}
