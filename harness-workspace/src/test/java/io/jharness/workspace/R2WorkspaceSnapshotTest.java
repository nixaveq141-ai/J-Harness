package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2WorkspaceSnapshotTest {
    @TempDir Path root;

    @Test
    void utf8FullTextAndPaginatedVersionIdentifyActualReturnedSnapshot() throws Exception {
        String text = "中文😀\r\nnext\n";
        Files.writeString(root.resolve("a"), text);
        var workspace = new LocalWorkspace(root);
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(text.getBytes(StandardCharsets.UTF_8)));
        var snapshot = workspace.readSnapshot(new ExecPath("a"));
        assertEquals(text, snapshot.text());
        assertEquals(hash, snapshot.version());
        var page = workspace.readPage(new ExecPath("a"), 1, 1, CancellationToken.NONE);
        assertEquals("1: 中文😀\n", page.get("text"));
        assertEquals(hash, page.get("version"));
        Files.writeString(root.resolve("a"), "external");
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        workspace.write(
                                                new ExecPath("a"),
                                                "overwrite",
                                                true,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        assertEquals("external", Files.readString(root.resolve("a")));
    }

    @Test
    void atomicReplacementPreservesSupportedAccessAttributes() throws Exception {
        Path file = root.resolve("script");
        Files.writeString(file, "old");
        var workspace = new LocalWorkspace(root);
        var posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        Set<PosixFilePermission> mode = null;
        if (posix != null) {
            mode = PosixFilePermissions.fromString("rwxr-x---");
            posix.setPermissions(mode);
        }
        var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        var entries = acl == null ? List.<AclEntry>of() : acl.getAcl();
        var owner = Files.getOwner(file);
        workspace.readText(new ExecPath("script"));
        workspace.write(new ExecPath("script"), "new", true, null, CancellationToken.NONE);
        assertEquals("new", Files.readString(file));
        assertEquals(owner, Files.getOwner(file));
        if (mode != null) assertEquals(mode, posix.readAttributes().permissions());
        if (acl != null) assertEquals(entries, acl.getAcl());
    }
}
