package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunStatePermissionsTest {
    @TempDir Path root;

    @Test
    void credentialsAreOwnerOnlyAndOrdinaryFoldersAreNotRepurposed() throws Exception {
        Files.writeString(root.resolve("user.txt"), "keep");
        assertThrows(IOException.class, () -> new LocalRunControl(root));
        assertEquals("keep", Files.readString(root.resolve("user.txt")));
        try (var run = new LocalRunControl(root.resolve("runs"))) {
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
