package io.jharness.session;

import io.jharness.api.*;
import io.jharness.workspace.DirectoryCapability;
import io.jharness.workspace.WindowsDirectoryPin;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Bounded derived caches, using relative directory operations or pinned Windows ancestors. */
final class IndexFiles {
    private IndexFiles() {}

    private static boolean nativeEnabled() {
        return true;
    }

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

    static DirectoryIdentity bind(Path root) throws IOException {
        return DirectoryCapability.bindPrivate(root);
    }

    private static Closeable pin(Path root, DirectoryIdentity identity) throws IOException {
        if (WindowsDirectoryPin.nativeKey(identity.fileKey()))
            return WindowsDirectoryPin.open(root, identity.fileKey());
        return () -> {};
    }

    static void check(Path root, DirectoryIdentity identity) throws IOException {
        DirectoryCapability.checkPrivate(root, identity);
    }

    static FileChannel open(
            Path root, DirectoryIdentity identity, Path target, Set<? extends OpenOption> options)
            throws IOException {
        return open(root, identity, target, options, () -> {});
    }

    @SuppressWarnings("try") // The guard acts through its lifetime.
    static FileChannel open(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Set<? extends OpenOption> options,
            PublicationHook hook)
            throws IOException {
        FileChannel[] owned = {null};
        try (var guard = pin(root, identity)) {
            return openHeld(root, identity, target, options, hook, owned);
        } catch (IOException | RuntimeException | Error failure) {
            if (owned[0] != null)
                try {
                    owned[0].close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            throw failure;
        }
    }

    private static FileChannel openHeld(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Set<? extends OpenOption> options,
            PublicationHook hook,
            FileChannel[] owned)
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
                if (channel instanceof FileChannel file) {
                    owned[0] = file;
                    return file;
                }
                channel.close();
                throw new IOException("Secure index channel cannot hold file locks");
            }
        }
        // Windows ancestors remain pinned by the caller; POSIX uses an opened directory below.
        if (WindowsDirectoryPin.supported()) {
            hook.beforeMove();
            owned[0] = FileChannel.open(target, options);
            return owned[0];
        }
        if (!nativeEnabled()) {
            hook.beforeMove();
            owned[0] = FileChannel.open(target, options);
            return owned[0];
        }
        try (var held = DirectoryCapability.open(root, identity.fileKey(), root, false)) {
            hook.beforeMove();
            owned[0] = held.channel(target.getFileName(), options);
            return owned[0];
        }
    }

    static void publish(Path root, DirectoryIdentity identity, Path target, Object data, int limit)
            throws IOException {
        publish(root, identity, target, data, limit, () -> {});
    }

    @FunctionalInterface
    interface PublicationHook {
        void beforeMove() throws IOException;
    }

    @SuppressWarnings("try") // Keep ancestor handles until publication and cleanup finish.
    static void publish(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Object data,
            int limit,
            PublicationHook hook)
            throws IOException {
        try (var guard = pin(root, identity)) {
            if (guard instanceof WindowsDirectoryPin windows) windows.allowChildRenames();
            publishHeld(root, identity, target, data, limit, hook, guard);
        }
    }

    private static void publishHeld(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Object data,
            int limit,
            PublicationHook hook,
            Closeable guard)
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
        if (nativeEnabled() && !WindowsDirectoryPin.supported()) {
            try (var held = DirectoryCapability.open(root, identity.fileKey(), root, false)) {
                publishNative(root, identity, target.getFileName(), data, limit, hash, held, hook);
                return;
            }
        }

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
            if (guard instanceof WindowsDirectoryPin windows) windows.publishCache(temp, target);
            else
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

    private static void publishNative(
            Path root,
            DirectoryIdentity identity,
            Path target,
            Object data,
            int limit,
            String hash,
            DirectoryCapability held,
            PublicationHook hook)
            throws IOException {
        Path temporary = Path.of("index-" + UUID.randomUUID() + ".tmp");
        boolean created = false;
        try {
            try (var channel =
                    held.channel(
                            temporary,
                            Set.of(
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS))) {
                created = true;
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
            held.move(temporary, target, true);
            created = false;
            check(root, identity);
        } finally {
            if (created) held.delete(temporary, false);
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
