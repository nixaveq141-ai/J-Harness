package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Explicit pinned, preinstalled JDK21 image. No automatic pulls or host credential injection. */
public final class DockerExecutionEnvironment implements ExecutionEnvironment {
    private final String name = "jharness-" + UUID.randomUUID();
    private final String docker, image;
    private final CommandTransport transport;
    private final RemoteWorkspace files;
    private final WorkspaceSearchFilter searchFilter;
    private final DockerNativeLibraries nativeLibraries;
    private volatile boolean closed;
    private volatile boolean removed;

    public DockerExecutionEnvironment(
            String docker, String image, Path workspace, Path workerJar, CommandTransport transport)
            throws IOException {
        this(docker, image, workspace, workerJar, transport, WorkspaceSearchFilter.defaults());
    }

    public DockerExecutionEnvironment(
            String docker,
            String image,
            Path workspace,
            Path workerJar,
            CommandTransport transport,
            WorkspaceSearchFilter searchFilter)
            throws IOException {
        this.searchFilter = Objects.requireNonNull(searchFilter);
        this.docker = Values.required(docker, "docker executable");
        this.image = Values.required(image, "image");
        this.transport = Objects.requireNonNull(transport);
        if (!image.matches("[A-Za-z0-9./:_-]+@sha256:[0-9a-f]{64}"))
            throw BoundedJson.error("INVALID_CONTAINER_IMAGE");
        Path root = workspace.toRealPath(), jar = workerJar.toRealPath();
        if (!Files.isDirectory(root)
                || !Files.isRegularFile(workerJar, LinkOption.NOFOLLOW_LINKS)
                || root.toString().contains(",")
                || jar.toString().contains(",")) throw BoundedJson.error("INVALID_CONTAINER_MOUNT");
        nativeLibraries = DockerNativeLibraries.prepare(jar);
        List<String> command =
                new ArrayList<>(
                        List.of(
                                docker,
                                "run",
                                "--detach",
                                "--pull=never",
                                "--name",
                                name,
                                "--label",
                                "io.jharness.owner=" + name,
                                "--network=none",
                                "--read-only",
                                "--cap-drop=ALL",
                                "--security-opt=no-new-privileges:true",
                                "--user=65534:65534",
                                "--memory=512m",
                                "--cpus=1",
                                "--pids-limit=64",
                                "--tmpfs",
                                "/tmp:rw,noexec,nosuid,size=64m",
                                "--mount",
                                "type=bind,source=" + root + ",target=/workspace",
                                "--mount",
                                "type=bind,source=" + jar + ",target=/runtime/harness.jar,readonly",
                                "--workdir",
                                "/workspace",
                                image,
                                "java",
                                "-cp",
                                "/runtime/harness.jar",
                                EnvironmentWorker.class.getName(),
                                "--idle"));
        if (nativeLibraries != null)
            command.addAll(
                    command.indexOf("--workdir"),
                    List.of(
                            "--mount",
                            "type=bind,source="
                                    + nativeLibraries.path()
                                    + ",target=/runtime/native,readonly"));
        var reply =
                transport.call(
                        command, new byte[0], Duration.ofSeconds(15), CancellationToken.NONE);
        if (reply.exitCode() != 0 || reply.uncertain()) {
            HarnessException failure =
                    BoundedJson.error(
                            reply.started() ? "CONTAINER_START_FAILED" : "DOCKER_UNAVAILABLE");
            if (reply.started()) {
                try {
                    removeContainer();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            } else if (nativeLibraries != null) {
                try {
                    nativeLibraries.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }

        files =
                new RemoteWorkspace(
                        (request, cancellation) -> {
                            ensureOpen();
                            var wire = new LinkedHashMap<>(request);
                            if (!searchFilter.equals(WorkspaceSearchFilter.defaults()))
                                wire.put("searchFilter", searchFilter.configuration());
                            var answer =
                                    transport.call(
                                            List.of(
                                                    docker,
                                                    "exec",
                                                    "-i",
                                                    name,
                                                    "java",
                                                    "-cp",
                                                    "/runtime/harness.jar",
                                                    EnvironmentWorker.class.getName(),
                                                    "/workspace"),
                                            BoundedJson.encode(wire),
                                            Duration.ofSeconds(15),
                                            () -> closed || cancellation.isCancelled());
                            boolean mutating =
                                    Set.of("write", "edit", "patch").contains(request.get("op"));
                            if (answer.uncertain() || answer.exitCode() != 0) {
                                if (!mutating || !answer.started())
                                    throw BoundedJson.error(
                                            answer.uncertain()
                                                    ? "ENVIRONMENT_READ_INTERRUPTED"
                                                    : "ENVIRONMENT_IO");
                                var unknown = BoundedJson.error("ENVIRONMENT_OPERATION_UNKNOWN");
                                try {
                                    close();
                                } catch (RuntimeException cleanup) {
                                    unknown.addSuppressed(cleanup);
                                }
                                throw unknown;
                            }
                            Map<String, Object> response;
                            try {
                                response = BoundedJson.decode(answer.stdout());
                            } catch (RuntimeException invalid) {
                                if (!mutating) throw invalid;
                                var unknown =
                                        new HarnessException(
                                                "ENVIRONMENT_OPERATION_UNKNOWN",
                                                "Worker response unavailable",
                                                invalid);
                                try {
                                    close();
                                } catch (RuntimeException cleanup) {
                                    unknown.addSuppressed(cleanup);
                                }
                                throw unknown;
                            }
                            if (!searchFilter.equals(WorkspaceSearchFilter.defaults())
                                    && !searchFilter
                                            .configuration()
                                            .equals(response.get("searchFilter")))
                                throw BoundedJson.error("ENVIRONMENT_FILTER_MISMATCH");
                            return response;
                        });
        if (!searchFilter.equals(WorkspaceSearchFilter.defaults())) {
            try {
                files.verifyFilter();
            } catch (RuntimeException ex) {
                try {
                    close();
                } catch (RuntimeException cleanup) {
                    ex.addSuppressed(cleanup);
                }
                throw ex;
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw BoundedJson.error("ENVIRONMENT_CLOSED");
    }

    @Override
    public String id() {
        return name;
    }

    @Override
    public WorkspaceFiles files() {
        return files;
    }

    @Override
    public ProcessRunner processes() {
        return new ProcessRunner() {
            @Override
            public ToolResult execute(
                    List<String> argv, ExecPath cwd, CancellationToken cancellation) {
                return execute(
                        new CommandRequest(
                                UUID.randomUUID().toString(),
                                CommandRequest.Mode.ARGV,
                                argv,
                                cwd,
                                Duration.ofSeconds(120)),
                        cancellation);
            }

            @Override
            public ToolResult execute(CommandRequest request, CancellationToken cancellation) {
                ensureOpen();
                String cwd = RemoteWorkspace.safe(request.cwd());
                var payload =
                        Map.<String, Object>of(
                                "op",
                                "command",
                                "path",
                                cwd,
                                "callId",
                                request.callId(),
                                "mode",
                                request.mode().name(),
                                "argv",
                                request.argv(),
                                "timeoutMs",
                                request.timeout().toMillis());
                var reply =
                        transport.call(
                                List.of(
                                        docker,
                                        "exec",
                                        "-i",
                                        name,
                                        "java",
                                        "-cp",
                                        "/runtime/harness.jar",
                                        EnvironmentWorker.class.getName(),
                                        "/workspace"),
                                BoundedJson.encode(payload),
                                request.timeout().plusSeconds(5),
                                () -> closed || cancellation.isCancelled());
                if (reply.uncertain() || reply.exitCode() != 0 && reply.started()) {
                    String cleanup = cleanupForUnknown();
                    return new ToolResult(
                            request.callId(),
                            ToolStatus.UNKNOWN,
                            Map.of(
                                    "environment",
                                    id(),
                                    "sideEffectUncertain",
                                    true,
                                    "cleanupStatus",
                                    cleanup),
                            "Container command outcome unknown",
                            Map.of("cleanupStatus", cleanup),
                            Optional.empty());
                }
                if (reply.exitCode() != 0) throw BoundedJson.error("ENVIRONMENT_IO");
                try {
                    var envelope = BoundedJson.decode(reply.stdout());
                    if (envelope.get("error") instanceof String code
                            && Boolean.TRUE.equals(envelope.get("executionRejected")))
                        return new ToolResult(
                                request.callId(),
                                ToolStatus.FAILED,
                                Map.of("executionRejected", true, "sideEffectUncertain", false),
                                code,
                                Map.of(),
                                Optional.of(new ToolResult.ErrorInfo(code, code)));
                    if (envelope.get("error") instanceof String code) throw BoundedJson.error(code);
                    var result = Values.object(envelope.get("value"));
                    var value = new LinkedHashMap<>(Values.object(result.get("value")));
                    value.put("environmentId", id());
                    ToolStatus status = ToolStatus.valueOf((String) result.get("status"));
                    if (Boolean.TRUE.equals(value.get("sideEffectUncertain"))
                            || status == ToolStatus.UNKNOWN) {
                        status = ToolStatus.UNKNOWN;
                        value.put("sideEffectUncertain", true);
                        value.put("containerCleanup", cleanupForUnknown());
                    }
                    return new ToolResult(
                            request.callId(),
                            status,
                            value,
                            (String) result.get("modelContent"),
                            Values.object(result.get("display")),
                            Optional.empty());
                } catch (RuntimeException unavailable) {
                    String cleanup = cleanupForUnknown();
                    return new ToolResult(
                            request.callId(),
                            ToolStatus.UNKNOWN,
                            Map.of(
                                    "environment",
                                    id(),
                                    "sideEffectUncertain",
                                    true,
                                    "controlExitCode",
                                    reply.exitCode(),
                                    "cleanupStatus",
                                    cleanup),
                            "Container command response unavailable; execution outcome unknown",
                            Map.of("cleanupStatus", cleanup),
                            Optional.empty());
                }
            }
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
        var result =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                id(),
                                "kind",
                                "docker",
                                "isolation",
                                "container",
                                "image",
                                image,
                                "network",
                                "none",
                                "readOnlyRoot",
                                true,
                                "capabilitiesDropped",
                                "ALL",
                                "user",
                                "65534:65534",
                                "terminal",
                                "unsupported"));
        if (!searchFilter.equals(WorkspaceSearchFilter.defaults()))
            result.put("searchFilter", searchFilter.configuration());
        return Map.copyOf(result);
    }

    private String cleanupForUnknown() {
        try {
            close();
            return "container_removed";
        } catch (RuntimeException cleanup) {
            return "container_cleanup_unknown";
        }
    }

    private void removeContainer() {
        var reply =
                transport.call(
                        List.of(docker, "rm", "--force", name),
                        new byte[0],
                        Duration.ofSeconds(5),
                        CancellationToken.NONE);
        if (reply.exitCode() != 0 || reply.uncertain())
            throw new HarnessException(
                    "CONTAINER_CLEANUP_UNKNOWN", "Container cleanup unknown: " + name);
        removed = true;
        if (nativeLibraries != null)
            try {
                nativeLibraries.close();
            } catch (IOException cleanup) {
                throw BoundedJson.error("CONTAINER_NATIVE_CLEANUP_FAILED");
            }
    }

    @Override
    public synchronized void close() {
        closed = true; // capabilities revoked even when removal fails; close may retry removal only
        if (!removed) removeContainer();
        else if (nativeLibraries != null)
            try {
                nativeLibraries.close();
            } catch (IOException cleanup) {
                throw BoundedJson.error("CONTAINER_NATIVE_CLEANUP_FAILED");
            }
    }
}
