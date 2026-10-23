package io.jharness.api;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;

/** Boundary identity check, not a replacement for an OS directory handle. */
public record DirectoryIdentity(Object fileKey, FileTime created, String anchor) {
    private static final String MARKER = ".directory-identity";

    /** Private metadata roots retain an identity anchor where the OS omits file keys. */
    public static DirectoryIdentity bind(Path directory) throws IOException {
        Path marker = directory.resolve(MARKER);
        try (var channel =
                java.nio.channels.FileChannel.open(
                        marker,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
            var bytes =
                    java.nio.ByteBuffer.wrap(
                            java.util
                                    .UUID
                                    .randomUUID()
                                    .toString()
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        } catch (FileAlreadyExistsException existing) {
            // A pre-existing anchor is read and validated below, never overwritten.
        }
        return read(directory);
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
                    throw new IOException("Invalid directory identity anchor");
            }
        }
        return new DirectoryIdentity(attributes.fileKey(), attributes.creationTime(), anchor);
    }
}
