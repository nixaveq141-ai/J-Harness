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
    private volatile boolean closed;

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
        List<String> command =
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
                        "--idle");
        var reply =
                transport.call(
                        command, new byte[0], Duration.ofSeconds(15), CancellationToken.NONE);
        if (reply.exitCode() != 0 || reply.uncertain()) {
            if (reply.started())
                transport.call(
                        List.of(docker, "rm", "--force", name),
                        new byte[0],
                        Duration.ofSeconds(5),
                        CancellationToken.NONE);
            throw BoundedJson.error(
                    reply.started() ? "CONTAINER_START_FAILED" : "DOCKER_UNAVAILABLE");
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
                                            cancellation);
                            if (answer.uncertain()) {
                                if (!Set.of("write", "edit", "patch").contains(request.get("op")))
                                    throw BoundedJson.error("ENVIRONMENT_READ_INTERRUPTED");
                                close();
                                throw BoundedJson.error("ENVIRONMENT_OPERATION_UNKNOWN");
                            }
                            if (answer.exitCode() != 0) throw BoundedJson.error("ENVIRONMENT_IO");
                            var response = BoundedJson.decode(answer.stdout());
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
                                cancellation);
                if (reply.uncertain()) {
                    close();
                    return new ToolResult(
                            request.callId(),
                            ToolStatus.UNKNOWN,
                            Map.of(
                                    "environment",
                                    id(),
                                    "sideEffectUncertain",
                                    true,
                                    "cleanupStatus",
                                    "container_removed"),
                            "Container command outcome unknown",
                            Map.of("cleanupStatus", "container_removed"),
                            Optional.empty());
                }
                if (reply.exitCode() != 0) throw BoundedJson.error("ENVIRONMENT_IO");
                var envelope = BoundedJson.decode(reply.stdout());
                if (envelope.get("error") instanceof String code) throw BoundedJson.error(code);
                var result = Values.object(envelope.get("value"));
                var value = new LinkedHashMap<>(Values.object(result.get("value")));
                value.put("environmentId", id());
                ToolStatus status = ToolStatus.valueOf((String) result.get("status"));
                if (Boolean.TRUE.equals(value.get("sideEffectUncertain"))
                        || status == ToolStatus.UNKNOWN) {
                    close();
                    value.put("containerCleanup", "removed");
                }
                return new ToolResult(
                        request.callId(),
                        status,
                        value,
                        (String) result.get("modelContent"),
                        Values.object(result.get("display")),
                        Optional.empty());
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

    @Override
    public synchronized void close() {
        if (closed) return;
        var reply =
                transport.call(
                        List.of(docker, "rm", "--force", name),
                        new byte[0],
                        Duration.ofSeconds(5),
                        CancellationToken.NONE);
        closed = true;
        if (reply.exitCode() != 0 || reply.uncertain())
            throw BoundedJson.error("CONTAINER_CLEANUP_UNKNOWN");
    }
}
