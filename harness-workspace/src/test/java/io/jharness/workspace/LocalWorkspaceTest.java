package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalWorkspaceTest {
    @Test
    void publicCreationRetainsHostDefaultModesWhilePrivateCapabilitiesStaySeparate()
            throws Exception {
        Path directory = root();
        Path reference = Files.writeString(directory.resolve("reference.txt"), "reference");
        Path referenceDirectory = Files.createDirectory(directory.resolve("reference-directory"));
        var workspace = new LocalWorkspace(directory);
        workspace.write(
                new ExecPath("generated/sub/file.txt"),
                "created",
                false,
                null,
                CancellationToken.NONE);
        assertEquals("created", workspace.readText(new ExecPath("generated/sub/file.txt")));
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            assertEquals(
                    Files.getPosixFilePermissions(reference),
                    Files.getPosixFilePermissions(directory.resolve("generated/sub/file.txt")));
            assertEquals(
                    Files.getPosixFilePermissions(referenceDirectory),
                    Files.getPosixFilePermissions(directory.resolve("generated")));
            assertEquals(
                    Files.getPosixFilePermissions(referenceDirectory),
                    Files.getPosixFilePermissions(directory.resolve("generated/sub")));
        }
    }

    @TempDir Path temporary;

    @Test
    void replacedRootCannotRebindExistingOrIsolatedViewOutsideBoundary() throws Exception {
        Path root = root(), outside = Files.createDirectory(temporary.resolve("outside-root"));
        Files.writeString(outside.resolve("secret.txt"), "private");
        var original = new LocalWorkspace(root);
        var child = original.isolatedView();
        Path saved = temporary.resolve("saved-root");
        assertTrue(
                root.toAbsolutePath()
                        .normalize()
                        .startsWith(temporary.toAbsolutePath().normalize()));
        assertTrue(
                saved.toAbsolutePath()
                        .normalize()
                        .startsWith(temporary.toAbsolutePath().normalize()));
        Files.move(root, saved);
        if (System.getProperty("os.name").startsWith("Windows")) {
            var process =
                    new ProcessBuilder(
                                    "cmd",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    root.toString(),
                                    outside.toString())
                            .redirectErrorStream(true)
                            .start();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(
                    0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        } else Files.createSymbolicLink(root, outside);
        try {
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(HarnessException.class, original::isolatedView).code());
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> original.readText(new ExecPath("secret.txt")))
                            .code());
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> child.readText(new ExecPath("secret.txt")))
                            .code());
            assertEquals("private", Files.readString(outside.resolve("secret.txt")));
        } finally {
            Files.delete(root);
        }
    }

    private Path root() throws IOException {
        return Files.createDirectory(temporary.resolve("工作区 project"));
    }

    @Test
    void relativeReadsWritesAndDiffPreserveOtherFilesAndCrLfBom() throws Exception {
        Path root = root();
        Files.writeString(
                root.resolve("Main.java"), "\uFEFFclass Main {\r\nint value = 1;\r\n}\r\n");
        Files.writeString(root.resolve("untouched"), "same");
        LocalWorkspace workspace = new LocalWorkspace(root);
        assertTrue(workspace.readText(new ExecPath("Main.java")).contains("value = 1"));
        FileSystemView.FileChange change =
                workspace.edit(
                        new ExecPath("Main.java"),
                        "value = 1",
                        "value = 2",
                        1,
                        null,
                        CancellationToken.NONE);
        assertEquals(
                "\uFEFFclass Main {\r\nint value = 2;\r\n}\r\n",
                Files.readString(root.resolve("Main.java")));
        assertEquals("same", Files.readString(root.resolve("untouched")));
        assertTrue(change.diff().contains("-int value = 1;"));
        assertTrue(change.diff().contains("+int value = 2;"));
        assertNotEquals(change.beforeVersion(), change.afterVersion());
        assertEquals(".", workspace.root().value());
    }

    @Test
    void traversalAbsoluteAdsDevicesAndProtectedMetadataAreDenied() throws Exception {
        Path root = root();
        LocalWorkspace workspace = new LocalWorkspace(root);
        Path outside = temporary.resolve("outside.txt");
        Files.writeString(outside, "private");
        for (String path :
                List.of(
                        "../outside.txt",
                        outside.toString(),
                        "/etc/passwd",
                        "C:/outside.txt",
                        "safe.txt:secret",
                        ".git/config",
                        ".jharness/session.jsonl",
                        "NUL",
                        "a. ")) {
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> workspace.readText(new ExecPath(path)))
                            .code());
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            workspace.write(
                                                    new ExecPath(path),
                                                    "modified",
                                                    false,
                                                    null,
                                                    CancellationToken.NONE))
                            .code());
        }
        assertEquals("private", Files.readString(outside));
    }

    @Test
    void escapeLinkOrWindowsJunctionIsRejectedForReadsWritesAndSearchTraversal() throws Exception {
        Path root = root(), outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = root.resolve("escape");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process process =
                    new ProcessBuilder(
                                    "cmd",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    link.toString(),
                                    outside.toString())
                            .redirectErrorStream(true)
                            .start();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(
                    0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        } else Files.createSymbolicLink(link, outside);
        try {
            LocalWorkspace workspace = new LocalWorkspace(root);
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> workspace.readText(new ExecPath("escape/secret.txt")))
                            .code());
            assertEquals(
                    "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            workspace.write(
                                                    new ExecPath("escape/secret.txt"),
                                                    "changed",
                                                    true,
                                                    null,
                                                    CancellationToken.NONE))
                            .code());
            assertTrue(
                    ((List<?>) workspace.search("**/*", 100, CancellationToken.NONE).get("paths"))
                            .isEmpty());
            assertEquals("secret", Files.readString(outside.resolve("secret.txt")));
        } finally {
            Files.delete(link);
        } // Delete junction entry only; never recursively traverse its target.
    }

    @Test
    void staleReadVersionAndWrongMatchCountsNeverOverwriteUserChanges() throws Exception {
        Path root = root();
        Path file = root.resolve("a.txt");
        Files.writeString(file, "same");
        LocalWorkspace workspace = new LocalWorkspace(root);
        workspace.readText(new ExecPath("a.txt"));
        Files.writeString(file, "same plus user edit");
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.edit(
                                                new ExecPath("a.txt"),
                                                "same",
                                                "replacement",
                                                1,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        assertEquals("same plus user edit", Files.readString(file));
        workspace.readText(new ExecPath("a.txt"));
        assertEquals(
                "EDIT_MATCH_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.edit(
                                                new ExecPath("a.txt"),
                                                "absent",
                                                "x",
                                                1,
                                                null,
                                                CancellationToken.NONE))
                        .code());
    }

    @Test
    void missingReadGuardAndExplicitOverwriteAreEnforcedAndNewParentsCanBeCreated()
            throws Exception {
        Path root = root();
        Files.writeString(root.resolve("existing"), "user");
        LocalWorkspace workspace = new LocalWorkspace(root);
        assertEquals(
                "FILE_EXISTS",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("existing"),
                                                "new",
                                                false,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("existing"),
                                                "new",
                                                true,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        workspace.write(new ExecPath("src/new/文件.txt"), "new", false, null, CancellationToken.NONE);
        assertEquals("new", Files.readString(root.resolve("src/new/文件.txt")));
    }

    @Test
    void aRaceBeforeCommitIsDetectedAndTemporaryFileIsRemoved() throws Exception {
        Path root = root();
        Path file = root.resolve("a");
        Files.writeString(file, "original");
        LocalWorkspace workspace =
                new LocalWorkspace(
                        root,
                        Duration.ofSeconds(5),
                        path -> Files.writeString(path, "user concurrent"));
        String version = workspace.versionOf(new ExecPath("a"));
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("a"),
                                                "agent",
                                                true,
                                                version,
                                                CancellationToken.NONE))
                        .code());
        assertEquals("user concurrent", Files.readString(file));
        try (var files = Files.list(root)) {
            assertEquals(List.of("a"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void cancellationAndIoFailureDoNotLeaveDelayedWritesOrDamagedOriginals() throws Exception {
        Path root = root();
        Files.writeString(root.resolve("a"), "original");
        AtomicBoolean cancelled = new AtomicBoolean();
        LocalWorkspace workspace =
                new LocalWorkspace(root, Duration.ofSeconds(5), path -> cancelled.set(true));
        String version = workspace.versionOf(new ExecPath("a"));
        assertEquals(
                "CANCELLED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("a"),
                                                "later",
                                                true,
                                                version,
                                                cancelled::get))
                        .code());
        assertEquals("original", Files.readString(root.resolve("a")));
        LocalWorkspace fault =
                new LocalWorkspace(
                        root,
                        Duration.ofSeconds(5),
                        path -> {
                            throw new IOException("injected");
                        });
        assertThrows(
                IOException.class,
                () ->
                        fault.write(
                                new ExecPath("a"), "agent", true, version, CancellationToken.NONE));
        assertEquals("original", Files.readString(root.resolve("a")));
    }

    @Test
    void binaryInvalidUtf8AndOversizedEditsAreRejected() throws Exception {
        Path root = root();
        Files.write(root.resolve("binary"), new byte[] {1, 0, 3});
        Files.write(root.resolve("encoding"), new byte[] {(byte) 0xff});
        LocalWorkspace workspace = new LocalWorkspace(root);
        assertEquals(
                "BINARY_FILE",
                assertThrows(
                                HarnessException.class,
                                () -> workspace.readText(new ExecPath("binary")))
                        .code());
        assertEquals(
                "UNSUPPORTED_ENCODING",
                assertThrows(
                                HarnessException.class,
                                () -> workspace.readText(new ExecPath("encoding")))
                        .code());
        assertEquals(
                "FILE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("large"),
                                                "x".repeat(1024 * 1024 + 1),
                                                false,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        assertFalse(Files.exists(root.resolve("large")));
    }

    @Test
    void largeFilesArePagedAndLiteralGrepFindsBeyondFirstPageWithoutRegexWork() throws Exception {
        Path root = root();
        Path large = root.resolve("large.txt");
        Files.writeString(large, "ordinary\n".repeat(3000) + "needle [a.*]\n");
        LocalWorkspace workspace = new LocalWorkspace(root);
        Map<String, Object> page =
                workspace.readPage(new ExecPath("large.txt"), 100, 110, CancellationToken.NONE);
        assertTrue(((String) page.get("text")).startsWith("100: ordinary"));
        assertEquals(true, page.get("truncated"));
        List<?> matches =
                (List<?>)
                        workspace
                                .grep(
                                        "[a.*]",
                                        List.of(new ExecPath("large.txt")),
                                        10,
                                        CancellationToken.NONE)
                                .get("matches");
        assertEquals(1, matches.size());
        assertEquals(3001, Values.object(matches.getFirst()).get("line"));
    }

    @Test
    void gitignoreAndDefaultExcludedDirectoriesFilterSearchAndLimitIsReported() throws Exception {
        Path root = root();
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("target/build.txt"), "ignored");
        Files.writeString(root.resolve(".gitignore"), "*.tmp\nsecret/\n");
        Files.writeString(root.resolve("skip.tmp"), "ignored");
        Files.createDirectory(root.resolve("secret"));
        Files.writeString(root.resolve("secret/a.txt"), "ignored");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/A.java"), "class A");
        Files.writeString(root.resolve("root.java"), "class Root");
        LocalWorkspace workspace = new LocalWorkspace(root);
        List<?> paths =
                (List<?>) workspace.search("**/*.java", 100, CancellationToken.NONE).get("paths");
        assertEquals(Set.of("src/A.java", "root.java"), new HashSet<>(paths));
        assertEquals(
                true, workspace.search("**/*.java", 1, CancellationToken.NONE).get("truncated"));
        assertFalse(
                workspace
                        .list(new ExecPath("."), 2, 100, CancellationToken.NONE)
                        .toString()
                        .contains("target/build.txt"));
    }

    @Test
    void thousandDirectoryEntriesMeetLocalTwoSecondTarget() throws Exception {
        Path root = root();
        for (int i = 0; i < 1000; i++) Files.writeString(root.resolve("file-" + i), "x");
        LocalWorkspace workspace = new LocalWorkspace(root);
        List<Long> timings = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            long started = System.nanoTime();
            assertEquals(
                    1000,
                    ((List<?>)
                                    workspace
                                            .list(
                                                    new ExecPath("."),
                                                    1,
                                                    1000,
                                                    CancellationToken.NONE)
                                            .get("entries"))
                            .size());
            long milliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            timings.add(milliseconds);
            assertTrue(milliseconds <= 2000, "1000 entry listing took " + milliseconds + " ms");
        }
        System.out.println("M2_DIRECTORY_1000_MS=" + timings);
    }

    @Test
    void unifiedDiffHandlesEmptyFilesAndMissingFinalNewlineAndNonGitWorkspace() throws Exception {
        assertEquals("", UnifiedDiff.between("a", "same", "same"));
        assertTrue(UnifiedDiff.between("a", "", "new").contains("@@ -0,0 +1,1 @@"));
        assertTrue(UnifiedDiff.between("a", "old", "new").contains("\\ No newline at end of file"));
        LocalWorkspace workspace = new LocalWorkspace(root());
        assertEquals(
                "GIT_DIFF_UNAVAILABLE",
                assertThrows(
                                HarnessException.class,
                                () -> workspace.gitDiff("working", CancellationToken.NONE))
                        .code());
    }
}
