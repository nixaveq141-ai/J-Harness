package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPlanPreviewerTest {
    @TempDir Path root;

    @Test
    void fileWriteAndEditPreviewDiffWithoutMutationAndShellIsExplicitlyUnpreviewable()
            throws Exception {
        Files.writeString(root.resolve("a.txt"), "before\n");
        var files = new LocalWorkspace(root);
        var preview = new LocalPlanPreviewer(files, new SecretRedactor());
        String version = files.versionOf(new ExecPath("a.txt"));
        Map<String, Object> edit =
                preview.preview(
                        new ToolInvocation(
                                "c",
                                "edit_file",
                                Map.of(
                                        "path",
                                        "a.txt",
                                        "oldText",
                                        "before",
                                        "newText",
                                        "after",
                                        "expectedVersion",
                                        version),
                                SideEffectLevel.WORKSPACE_WRITE),
                        CancellationToken.NONE);
        assertEquals(true, edit.get("previewable"));
        assertTrue(edit.get("diff").toString().contains("+after"));
        assertEquals("before\n", Files.readString(root.resolve("a.txt")));
        Map<String, Object> create =
                preview.preview(
                        new ToolInvocation(
                                "w",
                                "write_file",
                                Map.of("path", "new.txt", "content", "new"),
                                SideEffectLevel.WORKSPACE_WRITE),
                        CancellationToken.NONE);
        assertEquals("MISSING", create.get("beforeVersion"));
        assertFalse(Files.exists(root.resolve("new.txt")));
        Map<String, Object> shell =
                preview.preview(
                        new ToolInvocation(
                                "s",
                                "execute_command",
                                Map.of(
                                        "mode",
                                        "CMD",
                                        "argv",
                                        List.of("echo changed > a.txt & start cmd /c exit")),
                                SideEffectLevel.SYSTEM_IMPACT),
                        CancellationToken.NONE);
        assertEquals(false, shell.get("previewable"));
        assertTrue(shell.get("reason").toString().contains("无法预览"));
        assertEquals(false, shell.get("executed"));
    }

    @Test
    void previewRejectsOutsideAndStaleVersions() throws Exception {
        Files.writeString(root.resolve("a.txt"), "before");
        var preview = new LocalPlanPreviewer(new LocalWorkspace(root), new SecretRedactor());
        var outside =
                new ToolInvocation(
                        "c",
                        "write_file",
                        Map.of("path", "../x", "content", "x"),
                        SideEffectLevel.WORKSPACE_WRITE);
        assertEquals(
                "PATH_DENIED",
                assertThrows(
                                HarnessException.class,
                                () -> preview.preview(outside, CancellationToken.NONE))
                        .code());
        var stale =
                new ToolInvocation(
                        "c",
                        "write_file",
                        Map.of(
                                "path",
                                "a.txt",
                                "content",
                                "x",
                                "overwrite",
                                true,
                                "expectedVersion",
                                "stale"),
                        SideEffectLevel.WORKSPACE_WRITE);
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () -> preview.preview(stale, CancellationToken.NONE))
                        .code());
        assertEquals("before", Files.readString(root.resolve("a.txt")));
    }
}
