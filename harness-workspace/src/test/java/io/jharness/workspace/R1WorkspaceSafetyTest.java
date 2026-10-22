package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1WorkspaceSafetyTest {
    @TempDir Path root;

    @Test
    void temporaryCleanupFailureCannotReplaceTheOriginalConflict() throws Exception {
        var workspace =
                new LocalWorkspace(
                        root,
                        Duration.ofSeconds(5),
                        target -> {
                            try (var entries = Files.list(root)) {
                                Path temporary =
                                        entries.filter(
                                                        path ->
                                                                path.getFileName()
                                                                        .toString()
                                                                        .startsWith(
                                                                                ".jharness-write-"))
                                                .findFirst()
                                                .orElseThrow();
                                Files.delete(temporary);
                                Files.createDirectory(temporary);
                                Files.writeString(temporary.resolve("residue"), "fixture");
                            }
                            throw new HarnessException("FILE_CONFLICT", "primary fixture conflict");
                        });
        var failure =
                assertThrows(
                        HarnessException.class,
                        () ->
                                workspace.write(
                                        new ExecPath("a"),
                                        "after",
                                        false,
                                        null,
                                        CancellationToken.NONE));
        assertEquals("FILE_CONFLICT", failure.code());
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
        assertFalse(Files.exists(root.resolve("a")));
    }

    @Test
    void localGitConfigurationCannotRunExternalDiffOrFsmonitor() throws Exception {
        git("init");
        Files.writeString(root.resolve("a.txt"), "before\n");
        git("add", "a.txt");
        git(
                "-c",
                "user.name=fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "-m",
                "fixture");
        git("config", "diff.external", "must-not-run-external-diff");
        git("config", "core.fsmonitor", "must-not-run-fsmonitor");
        Files.writeString(root.resolve("a.txt"), "after\n");
        String diff = new LocalWorkspace(root).gitDiff("working", CancellationToken.NONE);
        assertTrue(diff.contains("+after"));
    }

    @Test
    void oversizedGitOutputTerminatesAtTheOutputBudget() throws Exception {
        git("init");
        Files.writeString(root.resolve("a.txt"), "before\n");
        git("add", "a.txt");
        Files.writeString(root.resolve("a.txt"), "line of output\n".repeat(200000));
        long before = System.nanoTime();
        var failure =
                assertThrows(
                        HarnessException.class,
                        () -> new LocalWorkspace(root).gitDiff("working", CancellationToken.NONE));
        assertEquals("OUTPUT_LIMIT", failure.code());
        assertTrue(System.nanoTime() - before < Duration.ofSeconds(5).toNanos());
    }

    private void git(String... arguments) throws Exception {
        var command = new ArrayList<String>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] log = process.getInputStream().readNBytes(65536);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(
                0, process.exitValue(), new String(log, java.nio.charset.StandardCharsets.UTF_8));
    }
}
