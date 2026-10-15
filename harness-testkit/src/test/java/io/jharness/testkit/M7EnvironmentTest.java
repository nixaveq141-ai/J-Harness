package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M7EnvironmentTest {
    @TempDir Path root;

    @Test
    void uncertainReadCannotRemoveParentsContainerThroughIsolatedView() throws Exception {
        Path jar = root.resolve("worker.jar");
        Files.writeString(jar, "fixture");
        var calls = new ArrayList<List<String>>();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        CommandTransport transport =
                (command, input, timeout, token) -> {
                    calls.add(command);
                    if (command.contains("exec") && reads.incrementAndGet() == 1)
                        return new CommandTransport.Reply(-1, new byte[0], new byte[0], true, true);
                    return new CommandTransport.Reply(
                            0,
                            ("{\"value\":{\"text\":\"readable\",\"version\":\""
                                            + "a".repeat(64)
                                            + "\"}}")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            new byte[0],
                            true,
                            false);
                };
        try (var environment =
                new DockerExecutionEnvironment(
                        "docker",
                        "eclipse-temurin@sha256:" + "a".repeat(64),
                        root,
                        jar,
                        transport)) {
            var childView = environment.files().isolatedView();
            assertEquals(
                    "ENVIRONMENT_READ_INTERRUPTED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> childView.readText(new ExecPath("file.txt")))
                            .code());
            assertFalse(calls.stream().anyMatch(command -> command.contains("rm")));
            assertEquals("readable", environment.files().readText(new ExecPath("file.txt")));
        }
        assertEquals(1, calls.stream().filter(command -> command.contains("rm")).count());
    }

    private RemoteWorkspace remote() {
        return new RemoteWorkspace(
                (request, token) -> {
                    try {
                        return EnvironmentWorker.dispatch(new LocalWorkspace(root), request);
                    } catch (HarnessException ex) {
                        return Map.of("error", ex.code());
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
    }

    @Test
    void independentWorkersPreserveReadVersionAcrossCalls() throws Exception {
        var files = remote();
        var path = new ExecPath("value.txt");
        Files.writeString(root.resolve("value.txt"), "before");
        assertEquals("before", files.readText(path));
        Files.writeString(root.resolve("value.txt"), "external");
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        files.write(
                                                path, "after", true, null, CancellationToken.NONE))
                        .code());
        assertEquals("external", Files.readString(root.resolve("value.txt")));
        assertEquals("external", files.readText(path));
        files.edit(path, "external", "after", 1, null, CancellationToken.NONE);
        assertEquals("after", files.readText(path));
    }

    @Test
    void noObservedVersionCannotOverwriteAndPathsCannotEscape() throws Exception {
        Files.writeString(root.resolve("value.txt"), "existing");
        var files = remote();
        assertThrows(
                HarnessException.class,
                () ->
                        files.write(
                                new ExecPath("value.txt"),
                                "bad",
                                true,
                                null,
                                CancellationToken.NONE));
        for (String path :
                List.of("../outside", "C:/outside", "/etc/passwd", ".git/config", "sub/NUL.txt"))
            assertThrows(HarnessException.class, () -> files.readText(new ExecPath(path)));
        files.write(new ExecPath("new.txt"), "new", false, null, CancellationToken.NONE);
        assertEquals("new", files.readText(new ExecPath("new.txt")));
    }

    @Test
    void containerFlagsOwnershipAndUnknownOutcomeAreConservative() throws Exception {
        Path jar = root.resolve("worker.jar");
        Files.writeString(jar, "fixture");
        var commands = new ArrayList<List<String>>();
        CommandTransport transport =
                (command, input, timeout, token) -> {
                    commands.add(List.copyOf(command));
                    return new CommandTransport.Reply(
                            command.contains("exec") ? -1 : 0,
                            new byte[0],
                            new byte[0],
                            true,
                            command.contains("exec"));
                };
        String image = "eclipse-temurin@sha256:" + "a".repeat(64);
        try (var environment =
                new DockerExecutionEnvironment("docker", image, root, jar, transport)) {
            var run = commands.getFirst();
            for (String flag :
                    List.of(
                            "--network=none",
                            "--read-only",
                            "--cap-drop=ALL",
                            "--security-opt=no-new-privileges:true",
                            "--pull=never",
                            "--pids-limit=64")) assertTrue(run.contains(flag));
            assertEquals(
                    ToolStatus.UNKNOWN,
                    environment
                            .processes()
                            .execute(
                                    new CommandRequest(
                                            "c",
                                            CommandRequest.Mode.SH,
                                            List.of("sleep 10"),
                                            new ExecPath("."),
                                            Duration.ofSeconds(1)),
                                    CancellationToken.NONE)
                            .status());
            assertEquals(List.of("docker", "rm", "--force", environment.id()), commands.getLast());
            assertThrows(
                    HarnessException.class, () -> environment.files().readText(new ExecPath("a")));
            assertThrows(HarnessException.class, () -> environment.terminal().status("x"));
        }
        assertEquals(3, commands.size());
        assertThrows(
                HarnessException.class,
                () -> new DockerExecutionEnvironment("docker", "latest", root, jar, transport));
    }

    @Test
    void transportLaunchesNativeProcessWithoutUncertainty() {
        var transport = new NativeCommandTransport();
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        if (!Files.exists(Path.of(java)))
            java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var reply =
                transport.call(
                        List.of(java, "-version"),
                        new byte[0],
                        Duration.ofSeconds(10),
                        CancellationToken.NONE);
        assertTrue(reply.started());
        assertFalse(reply.uncertain());
        assertEquals(0, reply.exitCode());
    }
}
