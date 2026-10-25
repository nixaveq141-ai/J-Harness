package io.jharness.session;

import io.jharness.api.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Disposable cache publication, bounded before publication. Boundary checks do not remove parent
 * races.
 */
final class IndexFiles {
    private IndexFiles() {}

    static String digest(Object data, int limit) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            JsonEncoding.writeTo(
                    data,
                    limit,
                    1000000,
                    32,
                    "SEARCH_INDEX_LIMIT",
                    new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void check(Path root, DirectoryIdentity identity) throws IOException {
        if (!root.toRealPath().equals(root) || !identity.equals(DirectoryIdentity.read(root)))
            throw new IOException("Search directory identity changed");
    }

    static void publish(Path root, DirectoryIdentity identity, Path target, Object data, int limit)
            throws IOException {
        String hash = digest(data, limit - 100);
        check(root, identity);
        Path temp = root.resolve("index-" + UUID.randomUUID() + ".tmp");
        try {
            try (var channel =
                    FileChannel.open(
                            temp,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                var output = Channels.newOutputStream(channel);
                output.write("{\"data\":".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                JsonEncoding.writeTo(data, limit - 100, 1000000, 32, "SEARCH_INDEX_LIMIT", output);
                output.write(
                        (",\"sha256\":\"" + hash + "\"}\n")
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                channel.force(true);
            }
            check(root, identity);
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            check(root, identity);
        } finally {
            // Never delete through a directory whose identity has changed.
            check(root, identity);
            Files.deleteIfExists(temp);
        }
    }
}
