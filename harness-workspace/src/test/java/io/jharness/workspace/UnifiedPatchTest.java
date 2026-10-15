package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnifiedPatchTest {
    @TempDir Path root;
    static final String UPDATE = "--- a/a.txt\n+++ b/a.txt\n@@ -1,2 +1,2 @@\n one\n-two\n+new\n";
    static final String CREATE = "--- /dev/null\n+++ b/b.txt\n@@ -0,0 +1,1 @@\n+created\n";

    @Test
    void exactMultiFileUpdateAndCreationPreserveUnrelatedFiles() throws Exception {
        Files.writeString(root.resolve("a.txt"), "one\r\ntwo\r\n");
        Files.writeString(root.resolve("keep"), "unchanged");
        var files = new LocalWorkspace(root);
        files.readText(new ExecPath("a.txt"));
        var result = files.applyPatch(UPDATE + CREATE, Map.of(), CancellationToken.NONE);
        assertEquals(false, result.get("partial"));
        assertEquals(2, ((List<?>) result.get("changes")).size());
        assertEquals("one\r\nnew\r\n", Files.readString(root.resolve("a.txt")));
        assertEquals("created\n", Files.readString(root.resolve("b.txt")));
        assertEquals("unchanged", Files.readString(root.resolve("keep")));
    }

    @Test
    void staleVersionAndOutsidePathFailAllPreflightBeforeFirstWrite() throws Exception {
        Files.writeString(root.resolve("a.txt"), "one\ntwo\n");
        var files = new LocalWorkspace(root);
        files.readText(new ExecPath("a.txt"));
        assertEquals(
                "PATH_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        files.applyPatch(
                                                UPDATE
                                                        + CREATE.replace(
                                                                "b/b.txt", "b/../outside.txt"),
                                                Map.of(),
                                                CancellationToken.NONE))
                        .code());
        assertEquals("one\ntwo\n", Files.readString(root.resolve("a.txt")));
        Files.writeString(root.resolve("a.txt"), "one\nuser changed\n");
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () -> files.applyPatch(UPDATE, Map.of(), CancellationToken.NONE))
                        .code());
        assertEquals("one\nuser changed\n", Files.readString(root.resolve("a.txt")));
    }

    @Test
    void secondCommitFailureReportsConfirmedChangesAndPendingPaths() throws Exception {
        Files.writeString(root.resolve("a.txt"), "one\ntwo\n");
        var files =
                new LocalWorkspace(
                        root,
                        Duration.ofSeconds(5),
                        path -> {
                            if (path.getFileName().toString().equals("b.txt"))
                                throw new IOException("fault");
                        });
        files.readText(new ExecPath("a.txt"));
        var result = files.applyPatch(UPDATE + CREATE, Map.of(), CancellationToken.NONE);
        assertEquals(true, result.get("partial"));
        assertEquals(true, result.get("sideEffectUncertain"));
        assertEquals(List.of("b.txt"), result.get("pendingPaths"));
        assertEquals(1, ((List<?>) result.get("changes")).size());
        assertEquals("one\nnew\n", Files.readString(root.resolve("a.txt")));
        assertFalse(Files.exists(root.resolve("b.txt")));
    }

    @Test
    void deletionFuzzAndUnsupportedNewlineMarkersNeverMutate() throws Exception {
        Files.writeString(root.resolve("a.txt"), "one\ntwo\n");
        var files = new LocalWorkspace(root);
        files.readText(new ExecPath("a.txt"));
        assertThrows(
                HarnessException.class,
                () ->
                        files.applyPatch(
                                UPDATE.replace("+++ b/a.txt", "+++ /dev/null"),
                                Map.of(),
                                CancellationToken.NONE));
        assertThrows(
                HarnessException.class,
                () ->
                        files.applyPatch(
                                UPDATE.replace("-two", "-mismatch"),
                                Map.of(),
                                CancellationToken.NONE));
        assertThrows(
                HarnessException.class,
                () ->
                        files.applyPatch(
                                UPDATE + "\\ No newline at end of file\n",
                                Map.of(),
                                CancellationToken.NONE));
        assertEquals("one\ntwo\n", Files.readString(root.resolve("a.txt")));
    }
}
