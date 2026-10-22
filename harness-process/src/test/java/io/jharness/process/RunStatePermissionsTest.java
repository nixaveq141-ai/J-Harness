package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunStatePermissionsTest {
    @TempDir Path root;

    @BeforeEach
    void canonicalTemporaryRoot() throws IOException {
        root = root.toRealPath();
    }

    @Test
    void credentialsAreOwnerOnlyAndOrdinaryFoldersAreNotRepurposed() throws Exception {
        Files.writeString(root.resolve("user.txt"), "keep");
        assertThrows(IOException.class, () -> new LocalRunControl(root));
        assertEquals("keep", Files.readString(root.resolve("user.txt")));
        Path target = Files.createDirectory(root.resolve("real-runs"));
        Path linked = root.resolve("linked-runs");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process junction =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/d",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    linked.toString(),
                                    target.toString())
                            .redirectErrorStream(true)
                            .start();
            try {
                assertTrue(junction.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(
                        0,
                        junction.exitValue(),
                        new String(junction.getInputStream().readAllBytes()));
            } finally {
                junction.destroyForcibly();
            }
        } else {
            Files.createSymbolicLink(linked, target);
        }
        try {
            assertThrows(IOException.class, () -> new LocalRunControl(linked));
        } finally {
            Files.delete(linked);
        }
        try (var run = new LocalRunControl(root.resolve("runs"))) {
            Path directory = run.stateFile().getParent();
            assertEquals(Files.getOwner(directory), Files.getOwner(run.stateFile()));
            var dirPosix = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
            if (dirPosix != null)
                assertEquals(
                        PosixFilePermissions.fromString("rwx------"),
                        dirPosix.readAttributes().permissions());
            else {
                var dirAcl = Files.getFileAttributeView(directory, AclFileAttributeView.class);
                var dirOwner = dirAcl.getOwner();
                assertTrue(
                        dirAcl.getAcl().stream()
                                .allMatch(
                                        e ->
                                                e.type() == AclEntryType.ALLOW
                                                        && e.principal().equals(dirOwner)));
            }
            var posix = Files.getFileAttributeView(run.stateFile(), PosixFileAttributeView.class);
            if (posix != null)
                assertEquals(
                        PosixFilePermissions.fromString("rw-------"),
                        posix.readAttributes().permissions());
            else {
                var acl = Files.getFileAttributeView(run.stateFile(), AclFileAttributeView.class);
                var owner = acl.getOwner();
                assertTrue(
                        acl.getAcl().stream()
                                .allMatch(
                                        e ->
                                                e.type() == AclEntryType.ALLOW
                                                        && e.principal().equals(owner)));
            }
        }
    }
}
