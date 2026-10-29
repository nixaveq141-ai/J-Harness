package io.jharness.session;

import io.jharness.api.*;
import io.jharness.workspace.DirectoryCapability;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Scope-bound persisted text artifacts; references are id+digest, not host paths. */
public final class LocalArtifactStore implements ArtifactStore {
    private static final int LIMIT = 4 * 1024 * 1024;
    private final Path root;
    private final DirectoryIdentity rootKey;
    private final DataSanitizer sanitizer;
    private volatile Snapshot snapshot;

    /** Only one verified content copy is retained. Every read rechecks scope and all file bytes. */
    private record Snapshot(String session, String id, byte[] bytes, String text) {}

    public LocalArtifactStore(Path root, DataSanitizer sanitizer) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        rootKey = IndexFiles.bind(this.root);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    private void checkRoot() throws IOException {
        try {
            IndexFiles.check(root, rootKey);
        } catch (IOException invalid) {
            throw new HarnessException(
                    "ARTIFACT_DENIED", "Artifact root identity changed", invalid);
        }
    }

    private DirectoryCapability capability(Path directory, boolean write) throws IOException {
        checkRoot();
        return DirectoryCapability.open(root, rootKey.fileKey(), directory, write);
    }

    private Path directory(String session, boolean create) throws IOException {
        checkRoot();
        if (session == null || !session.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid artifact scope");
        Path path = root.resolve(session);
        try (var held = capability(root, create)) {
            if (create && !held.exists(Path.of(session))) {
                try {
                    held.createDirectory(Path.of(session));
                } catch (FileAlreadyExistsException winner) {
                    /* Validate the winner below. */
                }
            }
            if (!held.exists(Path.of(session)))
                throw new HarnessException("ARTIFACT_NOT_FOUND", "Unknown artifact scope");
            if (!held.attributes(Path.of(session)).isDirectory())
                throw new HarnessException(
                        "ARTIFACT_DENIED", "Artifact scope must be an ordinary directory");
        }
        // The caller reopens every component without following links under the bound root.
        return path;
    }

    @Override
    public Artifact put(String session, String content) throws IOException {
        JsonEncoding.utf8Length(content, LIMIT, "ARTIFACT_LIMIT");
        var sanitized = sanitizer.sanitize(Map.of("content", content));
        if (sanitized == null || !(sanitized.get("content") instanceof String))
            throw new HarnessException(
                    "INVALID_SANITIZER_RESULT", "Sanitizer must return text content");
        String clean = (String) sanitized.get("content");
        byte[] bytes = JsonEncoding.utf8(clean, LIMIT, "ARTIFACT_LIMIT");
        if (bytes.length > LIMIT)
            throw new HarnessException("ARTIFACT_LIMIT", "Artifact exceeds bound");
        String digest = hash(bytes), id = UUID.randomUUID() + "-" + digest;
        Path file = directory(session, true).resolve(id + ".txt");
        try (var held = capability(file.getParent(), false);
                FileChannel channel =
                        held.channel(
                                file.getFileName(),
                                Set.of(
                                        StandardOpenOption.CREATE_NEW,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        return new Artifact(id, bytes.length, digest);
    }

    @Override
    public String read(String session, String id, int offset, int limit) throws IOException {
        if (id == null
                || !id.matches("[0-9a-f-]{36}-[0-9a-f]{64}")
                || offset < 0
                || limit < 1
                || limit > 256 * 1024)
            throw new HarnessException("ARTIFACT_DENIED", "Invalid reference or page");
        String text = readText(session, id);
        int start = Math.min(offset, text.length()),
                end = (int) Math.min(text.length(), (long) offset + limit);
        if (start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start)))
            throw new HarnessException("ARTIFACT_DENIED", "Offset splits UTF-16 character");
        if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1)))
            end--;
        if (end == start && start < text.length())
            throw new HarnessException(
                    "ARTIFACT_DENIED", "Page cannot hold a full Unicode character");
        return text.substring(start, end);
    }

    @Override
    public String readText(String session, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f-]{36}-[0-9a-f]{64}"))
            throw new HarnessException("ARTIFACT_DENIED", "Invalid reference");
        Path file = directory(session, false).resolve(id + ".txt");
        try (var held = capability(file.getParent(), false)) {
            if (!held.attributes(file.getFileName()).isRegularFile())
                throw new HarnessException("ARTIFACT_NOT_FOUND", "Artifact not in this scope");
        } catch (NoSuchFileException missing) {
            throw new HarnessException("ARTIFACT_NOT_FOUND", "Artifact not in this scope", missing);
        }
        Snapshot cached = snapshot;
        if (cached != null && cached.session.equals(session) && cached.id.equals(id)) {
            // Validate against the immutable, already hashed copy with a bounded scratch buffer.
            // Neither unchanged timestamps nor a remembered reference imply integrity.
            try (InputStream input = input(file)) {
                byte[] block = new byte[65536];
                int offset = 0, read;
                while ((read = input.read(block)) != -1) {
                    if (read > cached.bytes.length - offset)
                        throw new HarnessException(
                                "ARTIFACT_CORRUPT", "Artifact integrity check failed");
                    for (int i = 0; i < read; i++)
                        if (block[i] != cached.bytes[offset + i])
                            throw new HarnessException(
                                    "ARTIFACT_CORRUPT", "Artifact integrity check failed");
                    offset += read;
                }
                if (offset != cached.bytes.length)
                    throw new HarnessException(
                            "ARTIFACT_CORRUPT", "Artifact integrity check failed");
            }
            return cached.text;
        }
        byte[] bytes;
        try (InputStream input = input(file)) {
            bytes = input.readNBytes(LIMIT + 1);
        }
        if (bytes.length > LIMIT || !hash(bytes).equals(id.substring(37)))
            throw new HarnessException("ARTIFACT_CORRUPT", "Artifact integrity check failed");
        String text = new String(bytes, StandardCharsets.UTF_8);
        snapshot = new Snapshot(session, id, bytes, text);
        return text;
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private InputStream input(Path file) throws IOException {
        checkRoot();
        return java.nio.channels.Channels.newInputStream(
                DirectoryCapability.openFile(
                        root,
                        rootKey.fileKey(),
                        file,
                        Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
    }

    /** Copy verified immutable artifacts before publishing a fork. No parent fallback. */
    public void copyScope(String source, String target) throws IOException {
        validateScope(source);
        validateScope(target);
        Path reservation = Path.of(".fork-publish-" + target + ".lock");
        try (var held = capability(root, true)) {
            FileChannel claim;
            try {
                claim =
                        held.channel(
                                reservation,
                                Set.of(
                                        StandardOpenOption.CREATE_NEW,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS));
            } catch (FileAlreadyExistsException busy) {
                throw new HarnessException(
                        "ARTIFACT_SCOPE_EXISTS", "Fork destination is reserved", busy);
            }
            Throwable primary = null;
            try (claim) {
                copyReservedScope(source, target, held);
            } catch (IOException | RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try {
                    held.delete(reservation, false);
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    private void copyReservedScope(String source, String target, DirectoryCapability owner)
            throws IOException {
        Path targetName = Path.of(target), sourceName = Path.of(source);
        if (owner.exists(targetName))
            throw new HarnessException("ARTIFACT_SCOPE_EXISTS", "Fork scope already exists");
        if (!owner.exists(sourceName)) return;
        Path stagedName = Path.of(".fork-artifacts-" + UUID.randomUUID());
        owner.createDirectory(stagedName);
        boolean published = false;
        try {
            try (var input = capability(root.resolve(source), false);
                    var output = capability(root.resolve(stagedName), false);
                    var entries = input.entries()) {
                long total = 0;
                int count = 0;
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (!name.endsWith(".txt")) continue;
                    if (!name.matches("[0-9a-f-]{36}-[0-9a-f]{64}\\.txt")
                            || !input.attributes(entry.getFileName()).isRegularFile())
                        throw new HarnessException(
                                "ARTIFACT_DENIED", "Invalid fork artifact entry");
                    byte[] bytes;
                    try (var channel =
                                    input.channel(
                                            entry.getFileName(),
                                            Set.of(
                                                    StandardOpenOption.READ,
                                                    LinkOption.NOFOLLOW_LINKS));
                            var stream = java.nio.channels.Channels.newInputStream(channel)) {
                        bytes = stream.readNBytes(LIMIT + 1);
                    }
                    if (++count > 1000 || (total += bytes.length) > 32 * 1024 * 1024)
                        throw new HarnessException("ARTIFACT_LIMIT", "Fork artifacts exceed bound");
                    if (bytes.length > LIMIT || !hash(bytes).equals(name.substring(37, 101)))
                        throw new HarnessException(
                                "ARTIFACT_CORRUPT", "Fork artifact integrity failed");
                    try (var channel =
                            output.channel(
                                    entry.getFileName(),
                                    Set.of(
                                            StandardOpenOption.CREATE_NEW,
                                            StandardOpenOption.WRITE,
                                            LinkOption.NOFOLLOW_LINKS))) {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) channel.write(buffer);
                        channel.force(true);
                    }
                }
            }
            try {
                owner.moveDirectory(stagedName, targetName);
                published = true;
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException conflict) {
                throw new HarnessException(
                        "ARTIFACT_SCOPE_EXISTS", "Fork scope already exists", conflict);
            }
        } finally {
            if (!published) removeDirectory(stagedName, owner);
        }
    }

    /** Compensation only for an exclusively owned newly copied destination. */
    public void rollbackCopy(String target) throws IOException {
        validateScope(target);
        try (var owner = capability(root, true)) {
            if (owner.exists(Path.of(target))) removeDirectory(Path.of(target), owner);
        }
    }

    private static void validateScope(String scope) {
        if (scope == null || !scope.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid artifact scope");
    }

    private void removeDirectory(Path name, DirectoryCapability owner) throws IOException {
        try (var held = capability(root.resolve(name), true);
                var entries = held.entries()) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().startsWith(".jharness-pin-")) continue;
                if (!held.attributes(entry.getFileName()).isRegularFile())
                    throw new HarnessException(
                            "ARTIFACT_DENIED", "Unexpected fork compensation entry");
                held.delete(entry.getFileName(), false);
            }
        }
        owner.delete(name, true);
    }
}
