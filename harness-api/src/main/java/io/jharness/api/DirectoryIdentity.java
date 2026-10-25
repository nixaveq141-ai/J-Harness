package io.jharness.api;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;

/** Boundary identity check, not a replacement for an OS directory handle. */
public record DirectoryIdentity(Object fileKey, FileTime created, String anchor) {
    /** Invalid identity bytes are distinct from ordinary filesystem I/O failures. */
    public static final class InvalidAnchorException extends IOException {
        private static final long serialVersionUID = 1L;

        public InvalidAnchorException() {
            super("Invalid directory identity anchor");
        }
    }

    private static final String MARKER = ".directory-identity";

    /** Private metadata roots retain an identity anchor where the OS omits file keys. */
    public static DirectoryIdentity bind(Path directory) throws IOException {
        Path marker = directory.resolve(MARKER);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return read(directory);
        Path staged = Files.createTempFile(directory, ".identity-", ".tmp");
        try (var channel =
                java.nio.channels.FileChannel.open(
                        staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var bytes =
                    java.nio.ByteBuffer.wrap(
                            java.util
                                    .UUID
                                    .randomUUID()
                                    .toString()
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        try {
            // A hard-link publication is atomic and never replaces an existing anchor.
            // ATOMIC_MOVE alone has implementation-dependent replacement semantics.
            try {
                Files.createLink(marker, staged);
            } catch (FileAlreadyExistsException existing) {
                /* Another complete anchor won. */
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("Atomic identity publication unsupported", unsupported);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
        return read(directory);
    }

    /** Public workspace roots never interpret a user-owned marker file as a private anchor. */
    public static DirectoryIdentity readDirectory(Path directory) throws IOException {
        var attributes =
                Files.readAttributes(
                        directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) throw new IOException("Expected directory identity");
        // JDK's Windows provider omits fileKey but supplies the native creation timestamp.
        // Other providers without a key may substitute a mutable mtime: fail explicitly there.
        if (attributes.fileKey() == null && !System.getProperty("os.name").startsWith("Windows"))
            throw new IOException("Stable public directory identity unsupported");
        return new DirectoryIdentity(attributes.fileKey(), attributes.creationTime(), null);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DirectoryIdentity identity)) return false;
        if (!java.util.Objects.equals(fileKey, identity.fileKey)
                || !java.util.Objects.equals(anchor, identity.anchor)) return false;
        return fileKey != null
                || anchor != null
                || java.util.Objects.equals(created, identity.created);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                fileKey, anchor, fileKey != null || anchor != null ? null : created);
    }

    public static DirectoryIdentity read(Path directory) throws IOException {
        var attributes =
                Files.readAttributes(
                        directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path marker = directory.resolve(MARKER);
        String anchor = null;
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            try (var input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(37);
                anchor = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
                if (!anchor.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                    throw new InvalidAnchorException();
            }
        }
        return new DirectoryIdentity(attributes.fileKey(), attributes.creationTime(), anchor);
    }
}
