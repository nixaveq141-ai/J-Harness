package io.jharness.api;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;

/** Preserve supported access attributes before replacing an existing file with a new inode. */
public final class AtomicFileMetadata {
    private AtomicFileMetadata() {}

    public static void copy(Path source, Path target) throws IOException {
        copy(source, target, false);
    }

    /** Workspace changes may report unavailable POSIX ownership without weakening access mode. */
    public static java.util.Map<String, Object> copyForWorkspace(Path source, Path target)
            throws IOException {
        return copy(source, target, true);
    }

    private static java.util.Map<String, Object> copy(
            Path source, Path target, boolean reportOwnership) throws IOException {
        var notes = new java.util.LinkedHashMap<String, Object>();
        var posix =
                Files.getFileAttributeView(
                        source, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            var attributes = posix.readAttributes();
            var destination =
                    Files.getFileAttributeView(
                            target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (destination == null)
                throw new IOException("POSIX access mode unsupported by target");
            try {
                if (!destination.readAttributes().owner().equals(attributes.owner()))
                    destination.setOwner(attributes.owner());
            } catch (IOException failure) {
                if (!reportOwnership) throw failure;
            }
            try {
                if (!destination.readAttributes().group().equals(attributes.group()))
                    destination.setGroup(attributes.group());
            } catch (IOException failure) {
                if (!reportOwnership) throw failure;
            }
            destination.setPermissions(attributes.permissions());
            var actual = destination.readAttributes();
            if (!actual.permissions().equals(attributes.permissions()))
                throw new IOException("POSIX access mode could not be preserved");
            if (!actual.owner().equals(attributes.owner())
                    || !actual.group().equals(attributes.group())) {
                notes.put("ownerPreserved", actual.owner().equals(attributes.owner()));
                notes.put("groupPreserved", actual.group().equals(attributes.group()));
                notes.put(
                        "permissionsPreserved",
                        actual.permissions().equals(attributes.permissions()));
                notes.put(
                        "ownershipNote",
                        "POSIX ownership reassignment unavailable; creating identity/group retained");
            }
        }
        var acl =
                Files.getFileAttributeView(
                        source, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var destination =
                    Files.getFileAttributeView(
                            target, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (destination == null) throw new IOException("ACL access mode unsupported by target");
            try {
                if (!destination.getOwner().equals(acl.getOwner()))
                    destination.setOwner(acl.getOwner());
            } catch (IOException failure) {
                if (!reportOwnership) throw failure;
                notes.put("ownerPreserved", false);
                notes.put(
                        "ownershipNote",
                        "ACL ownership reassignment unavailable; creating identity retained");
            }
            destination.setAcl(acl.getAcl());
            if (!destination.getAcl().equals(acl.getAcl()))
                throw new IOException("ACL access mode could not be preserved");
        }
        return java.util.Map.copyOf(notes);
    }
}
