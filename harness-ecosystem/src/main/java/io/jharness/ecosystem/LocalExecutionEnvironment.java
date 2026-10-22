package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.process.*;
import io.jharness.workspace.LocalWorkspace;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class LocalExecutionEnvironment implements ExecutionEnvironment {
    private final String id;
    private final java.util.concurrent.atomic.AtomicBoolean closed =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final LocalWorkspace files;
    private final LocalProcessRunner processes;
    private final LocalTerminalSession terminal;
    private final WorkspaceSearchFilter searchFilter;

    public LocalExecutionEnvironment(String id, Path root, List<String> secrets)
            throws IOException {
        this(id, root, secrets, WorkspaceSearchFilter.defaults());
    }

    public LocalExecutionEnvironment(
            String id, Path root, List<String> secrets, WorkspaceSearchFilter searchFilter)
            throws IOException {
        this.id = Values.required(id, "environment id");
        this.searchFilter = Objects.requireNonNull(searchFilter);
        files = new LocalWorkspace(root, searchFilter);
        processes =
                new LocalProcessRunner(
                        files,
                        ProcessLimits.defaults(),
                        LocalProcessRunner.buildEnvironment(),
                        secrets);
        terminal = new LocalTerminalSession(files, LocalProcessRunner.buildEnvironment(), secrets);
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
        return processes;
    }

    @Override
    public TerminalSession terminal() {
        return terminal;
    }

    @Override
    public Map<String, Object> descriptor() {
        var result =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                id,
                                "kind",
                                "local",
                                "isolation",
                                "none",
                                "trustedProjectRequired",
                                true));
        if (!searchFilter.equals(WorkspaceSearchFilter.defaults()))
            result.put("searchFilter", searchFilter.configuration());
        return Map.copyOf(result);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        files.revoke();
        try {
            terminal.close();
        } catch (RuntimeException | Error primary) {
            try {
                processes.close();
            } catch (RuntimeException | Error secondary) {
                primary.addSuppressed(secondary);
            }
            throw primary;
        }
        processes.close();
    }
}
