package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryCapabilityTest {
    @TempDir Path base;
    private static final Set<OpenOption> READ =
            Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    private static final Set<OpenOption> NEW =
            Set.of(
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW,
                    LinkOption.NOFOLLOW_LINKS);

    @Test
    void heldDirectoryCannotRedirectReadWriteListOrCleanup() throws Exception {
        Path root = Files.createDirectory(base.resolve("root")).toRealPath();
        Files.writeString(root.resolve("original"), "original");
        Path moved = base.resolve("moved");
        boolean blocked = false;
        try (var cap =
                DirectoryCapability.open(root, DirectoryCapability.identity(root), root, true)) {
            try {
                Files.move(root, moved);
            } catch (FileSystemException refused) {
                if (!WindowsDirectoryPin.supported()) throw refused;
                blocked = true;
            }
            if (!blocked) {
                Files.createDirectory(root);
                Files.writeString(root.resolve("original"), "outside");
            }
            try (var channel = cap.channel(Path.of("original"), READ)) {
                ByteBuffer bytes = ByteBuffer.allocate(16);
                int count = channel.read(bytes);
                assertEquals(
                        "original",
                        new String(
                                bytes.array(), 0, count, java.nio.charset.StandardCharsets.UTF_8));
            }
            try (var channel = cap.channel(Path.of("new"), NEW)) {
                channel.write(ByteBuffer.wrap(new byte[] {42}));
                channel.force(true);
            }
            cap.move(Path.of("new"), Path.of("published"), false);
            cap.createDirectory(Path.of("child"));
            try (var entries = cap.entries()) {
                var names = new HashSet<String>();
                for (Path entry : entries) names.add(entry.getFileName().toString());
                assertTrue(names.containsAll(Set.of("original", "published", "child")));
            }
            cap.delete(Path.of("published"), false);
            cap.delete(Path.of("child"), true);
        }
        Path actual = blocked ? root : moved;
        assertEquals("original", Files.readString(actual.resolve("original")));
        if (!blocked) {
            assertEquals("outside", Files.readString(root.resolve("original")));
            assertFalse(Files.exists(root.resolve("published")));
            assertFalse(Files.exists(root.resolve("child")));
        }
        try (var entries = Files.newDirectoryStream(actual, ".jharness-pin-*")) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    void noClobberPublicationAndClosedAuthorityRejectBeforeUse() throws Exception {
        Path root = Files.createDirectory(base.resolve("root")).toRealPath();
        var cap = DirectoryCapability.open(root, DirectoryCapability.identity(root), root, true);
        try (cap) {
            Files.writeString(root.resolve("temporary"), "new");
            Files.writeString(root.resolve("winner"), "winner");
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> cap.move(Path.of("temporary"), Path.of("winner"), false));
            assertEquals("winner", Files.readString(root.resolve("winner")));
            assertThrows(java.io.IOException.class, () -> cap.channel(Path.of("../outside"), READ));
            assertThrows(java.io.IOException.class, () -> cap.createDirectory(Path.of("..")));
        }
        assertThrows(java.io.IOException.class, () -> cap.channel(Path.of("winner"), READ));
        assertThrows(java.io.IOException.class, () -> cap.delete(Path.of("winner"), false));
        cap.close();
        assertEquals("winner", Files.readString(root.resolve("winner")));
    }

    @Test
    void reusedDirectoryNameCannotReusePrivateAnchorIdentity() throws Exception {
        Path root = Files.createDirectory(base.resolve("root")).toRealPath();
        var identity = DirectoryCapability.bindPrivate(root);
        byte[] anchor = Files.readAllBytes(root.resolve(".directory-identity"));
        Files.move(root, base.resolve("original"));
        Files.createDirectory(root);
        Files.write(root.resolve(".directory-identity"), anchor);
        assertThrows(
                java.io.IOException.class, () -> DirectoryCapability.checkPrivate(root, identity));
        assertThrows(
                java.io.IOException.class,
                () -> DirectoryCapability.open(root, identity.fileKey(), root, false));
    }

    @Test
    void privateAnchorIsDurableStableAndCorruptionIsNotReset() throws Exception {
        Path root = Files.createDirectory(base.resolve("root")).toRealPath();
        var identity = DirectoryCapability.bindPrivate(root);
        assertEquals(identity, DirectoryCapability.bindPrivate(root));
        DirectoryCapability.checkPrivate(root, identity);
        Files.writeString(root.resolve(".directory-identity"), "invalid");
        assertThrows(java.io.IOException.class, () -> DirectoryCapability.bindPrivate(root));
        assertEquals("invalid", Files.readString(root.resolve(".directory-identity")));
    }
}
