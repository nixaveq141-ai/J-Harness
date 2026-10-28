package io.jharness.session;

import io.jharness.api.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Disposable cache publication, bounded before publication. Boundary checks do not remove parent
 * races on providers without SecureDirectoryStream.
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

    static FileChannel open(
            Path root, DirectoryIdentity identity, Path target, Set<? extends OpenOption> options)
            throws IOException {
        return open(root, identity, target, options, () -> {});
    }

    static FileChannel open(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Set<? extends OpenOption> options,
            PublicationHook hook)
            throws IOException {
        if (!target.getParent().equals(root)) throw new IOException("Index target outside root");
        check(root, identity);
        try (var directory = Files.newDirectoryStream(root)) {
            if (directory instanceof SecureDirectoryStream<Path> secure) {
                var attributes =
                        secure.getFileAttributeView(
                                        java.nio.file.attribute.BasicFileAttributeView.class)
                                .readAttributes();
                if (identity.fileKey() == null || !identity.fileKey().equals(attributes.fileKey()))
                    throw new IOException("Opened search directory identity changed");
                try {
                    var file =
                            secure.getFileAttributeView(
                                            target.getFileName(),
                                            java.nio.file.attribute.BasicFileAttributeView.class,
                                            LinkOption.NOFOLLOW_LINKS)
                                    .readAttributes();
                    if (!file.isRegularFile()) throw new IOException("Index file must be regular");
                } catch (NoSuchFileException missing) {
                    if (!options.contains(StandardOpenOption.CREATE)) throw missing;
                }
                hook.beforeMove();
                var channel = secure.newByteChannel(target.getFileName(), options);
                if (channel instanceof FileChannel file) return file;
                channel.close();
                throw new IOException("Secure index channel cannot hold file locks");
            }
        }
        // Compatibility provider: the documented trusted-host boundary remains explicit.
        hook.beforeMove();
        return FileChannel.open(target, options);
    }

    static void publish(Path root, DirectoryIdentity identity, Path target, Object data, int limit)
            throws IOException {
        publish(root, identity, target, data, limit, () -> {});
    }

    @FunctionalInterface
    interface PublicationHook {
        void beforeMove() throws IOException;
    }

    static void publish(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Object data,
            int limit,
            PublicationHook hook)
            throws IOException {
        if (!target.getParent().equals(root)) throw new IOException("Index target outside root");
        String hash = digest(data, limit - 100);
        check(root, identity);
        try (var directory = Files.newDirectoryStream(root)) {
            if (directory instanceof SecureDirectoryStream<Path> secure) {
                publishSecure(
                        root, identity, target.getFileName(), data, limit, hash, secure, hook);
                return;
            }
        }
        // Explicit compatibility path: retains boundary checks, not an ancestor-race guarantee.

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
            hook.beforeMove();
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

    private static void publishSecure(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Object data,
            int limit,
            String hash,
            SecureDirectoryStream<Path> directory,
            PublicationHook hook)
            throws IOException {
        var attributes =
                directory
                        .getFileAttributeView(java.nio.file.attribute.BasicFileAttributeView.class)
                        .readAttributes();
        if (identity.fileKey() == null || !identity.fileKey().equals(attributes.fileKey()))
            throw new IOException("Opened search directory identity changed");
        Path temporary = root.getFileSystem().getPath("index-" + UUID.randomUUID() + ".tmp");
        boolean created = false;
        try {
            try (var channel =
                    directory.newByteChannel(
                            temporary,
                            Set.of(
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS))) {
                created = true;
                if (!(channel instanceof FileChannel durable))
                    throw new IOException("Secure index channel cannot force durability");
                var output = Channels.newOutputStream(channel);
                output.write("{\"data\":".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                JsonEncoding.writeTo(data, limit - 100, 1000000, 32, "SEARCH_INDEX_LIMIT", output);
                output.write(
                        (",\"sha256\":\"" + hash + "\"}\n")
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                durable.force(true);
            }
            check(root, identity);
            hook.beforeMove();
            // Both names are single relative components; replacement never resolves the root again.
            directory.move(temporary, directory, target);
            created = false;
            check(root, identity);
        } finally {
            if (created) directory.deleteFile(temporary);
        }
    }
}
