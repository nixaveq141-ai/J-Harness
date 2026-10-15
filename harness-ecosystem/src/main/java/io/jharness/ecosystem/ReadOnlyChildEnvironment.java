package io.jharness.ecosystem;

import io.jharness.api.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Borrowed read-only view. A child never closes the parent's environment or terminal. */
final class ReadOnlyChildEnvironment implements ExecutionEnvironment {
    private final ExecutionEnvironment parent;
    private final String id;
    private final BooleanSupplier cancelled;
    private final WorkspaceFiles files;
    private final AtomicBoolean closed = new AtomicBoolean();

    ReadOnlyChildEnvironment(ExecutionEnvironment parent, String id, BooleanSupplier cancelled)
            throws IOException {
        this.parent = parent;
        this.id = id;
        this.cancelled = cancelled;
        if (!(parent.files() instanceof WorkspaceFiles workspace))
            throw BoundedJson.error("UNSUPPORTED_WORKSPACE");
        WorkspaceFiles base = workspace.isolatedView();
        files =
                new WorkspaceFiles() {
                    private void check() {
                        if (closed.get() || cancelled.getAsBoolean())
                            throw BoundedJson.error("CANCELLED");
                    }

                    @Override
                    public ExecPath root() {
                        return base.root();
                    }

                    @Override
                    public String readText(ExecPath path) throws IOException {
                        check();
                        return base.readText(path);
                    }

                    @Override
                    public String versionOf(ExecPath path) throws IOException {
                        check();
                        return base.versionOf(path);
                    }

                    @Override
                    public Map<String, Object> readPage(
                            ExecPath path, int start, int end, CancellationToken token)
                            throws IOException {
                        check();
                        return base.readPage(path, start, end, token);
                    }

                    @Override
                    public Map<String, Object> list(
                            ExecPath path, int depth, int limit, CancellationToken token)
                            throws IOException {
                        check();
                        return base.list(path, depth, limit, token);
                    }

                    @Override
                    public Map<String, Object> search(
                            String glob, int limit, CancellationToken token) throws IOException {
                        check();
                        return base.search(glob, limit, token);
                    }

                    @Override
                    public Map<String, Object> grep(
                            String text, List<ExecPath> paths, int limit, CancellationToken token)
                            throws IOException {
                        check();
                        return base.grep(text, paths, limit, token);
                    }

                    @Override
                    public String gitDiff(String scope, CancellationToken token)
                            throws IOException {
                        check();
                        throw denied();
                    }

                    @Override
                    public FileChange writeText(ExecPath path, String content, VersionGuard guard) {
                        throw denied();
                    }

                    @Override
                    public FileChange write(
                            ExecPath path,
                            String content,
                            boolean overwrite,
                            String version,
                            CancellationToken token) {
                        throw denied();
                    }

                    @Override
                    public FileChange edit(
                            ExecPath path,
                            String oldText,
                            String newText,
                            int count,
                            String version,
                            CancellationToken token) {
                        throw denied();
                    }

                    @Override
                    public Map<String, Object> applyPatch(
                            String patch, Map<String, String> versions, CancellationToken token) {
                        throw denied();
                    }
                };
    }

    private static HarnessException denied() {
        return BoundedJson.error("PERMISSION_DENIED");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public WorkspaceFiles files() {
        return files;
    }

    @Override
    public ProcessRunner processes() {
        return (argv, cwd, cancellation) -> {
            throw denied();
        };
    }

    @Override
    public TerminalSession terminal() {
        return new TerminalSession() {
            @Override
            public void close() {}
        };
    }

    @Override
    public Map<String, Object> descriptor() {
        return Map.of(
                "id",
                id,
                "kind",
                "read-only-child",
                "parent",
                parent.descriptor(),
                "terminal",
                "unsupported");
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
