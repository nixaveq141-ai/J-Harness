package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R5WorkspaceBoundsTest {
    @TempDir Path root;

    @Test
    void finalLineWithOrWithoutNewlineHasExplicitEofAndNonRepeatingNextLine() throws Exception {
        var workspace = new LocalWorkspace(root.toRealPath());
        for (String content : List.of("first\nlast", "first\nlast\n")) {
            Files.writeString(root.resolve("a.txt"), content);
            var page = workspace.readPage(new ExecPath("a.txt"), 2, 2, CancellationToken.NONE);
            assertEquals("2: last\n", page.get("text"));
            assertEquals(3, page.get("nextLine"));
            assertEquals(false, page.get("truncated"));
            assertEquals(true, page.get("eof"));
            var beyond = workspace.readPage(new ExecPath("a.txt"), 50, 50, CancellationToken.NONE);
            assertEquals("", beyond.get("text"));
            assertEquals(50, beyond.get("nextLine"));
            assertEquals(false, beyond.get("more"));
        }
        Files.writeString(root.resolve("a.txt"), "first\nlast\nmore");
        assertEquals(
                true,
                workspace
                        .readPage(new ExecPath("a.txt"), 2, 2, CancellationToken.NONE)
                        .get("more"));
    }

    @Test
    void searchStopsAfterAnExtraMatchAndGrepDoesNotOpenSubsequentPaths() throws Exception {
        var workspace = new LocalWorkspace(root.toRealPath());
        for (int i = 0; i < 50; i++) Files.writeString(root.resolve("a" + i + ".txt"), "needle\n");
        var page = workspace.search("*.txt", 1, CancellationToken.NONE);
        assertEquals(1, ((List<?>) page.get("paths")).size());
        assertEquals(true, page.get("truncated"));
        // A later invalid path would throw if grep kept scanning after reaching the result cap.
        var result =
                workspace.grep(
                        "needle",
                        List.of(new ExecPath("a0.txt"), new ExecPath("../outside")),
                        1,
                        CancellationToken.NONE);
        assertEquals(1, result.get("scannedPaths"));
        assertEquals(true, result.get("truncated"));
        assertEquals(1, ((List<?>) result.get("matches")).size());
    }
}
