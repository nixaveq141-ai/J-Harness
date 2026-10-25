package io.jharness.session;

import io.jharness.api.*;
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
    private final Object rootKey;
    private final DataSanitizer sanitizer;

    public LocalArtifactStore(Path root, DataSanitizer sanitizer) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        rootKey = DirectoryIdentity.bind(this.root);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    private void checkRoot() throws IOException {
        DirectoryIdentity currentIdentity;
        try {
            currentIdentity = DirectoryIdentity.read(root);
        } catch (DirectoryIdentity.InvalidAnchorException invalid) {
            throw new HarnessException(
                    "ARTIFACT_DENIED", "Invalid artifact root identity", invalid);
        }
        var attributes =
                Files.readAttributes(
                        root,
                        java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()
                || !Objects.equals(rootKey, currentIdentity)
                || !root.toRealPath().equals(root))
            throw new HarnessException("ARTIFACT_DENIED", "Artifact root identity changed");
    }

    private Path directory(String session, boolean create) throws IOException {
        checkRoot();
        if (session == null || !session.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid artifact scope");
        Path path = root.resolve(session);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(path)
                        || !path.toRealPath().equals(path.toAbsolutePath().normalize())))
            throw new HarnessException("ARTIFACT_DENIED", "Artifact directory is a link");
        if (create) Files.createDirectories(path);
        else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException("ARTIFACT_NOT_FOUND", "Unknown artifact scope");
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().equals(path.toAbsolutePath().normalize()))
            throw new HarnessException("ARTIFACT_DENIED", "Artifact directory identity changed");
        checkRoot();
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
        try (FileChannel channel =
                FileChannel.open(
                        file,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
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
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException("ARTIFACT_NOT_FOUND", "Artifact not in this scope");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(LIMIT + 1);
        }
        if (bytes.length > LIMIT || !hash(bytes).equals(id.substring(37)))
            throw new HarnessException("ARTIFACT_CORRUPT", "Artifact integrity check failed");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Copy verified immutable artifacts before publishing a fork. No parent fallback. */
    public void copyScope(String source, String target) throws IOException {
        validateScope(source);
        validateScope(target);
        checkRoot();
        Path reservation = root.resolve(".fork-publish-" + target + ".lock");
        FileChannel claim;
        try {
            claim =
                    FileChannel.open(
                            reservation,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
        } catch (FileAlreadyExistsException busy) {
            throw new HarnessException(
                    "ARTIFACT_SCOPE_EXISTS", "Fork destination is reserved", busy);
        }
        Throwable primary = null;
        try (claim) {
            copyReservedScope(source, target);
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(reservation);
            } catch (IOException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private void copyReservedScope(String source, String target) throws IOException {
        Path targetPath = root.resolve(target);
        if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException("ARTIFACT_SCOPE_EXISTS", "Fork scope already exists");
        Path sourcePath = root.resolve(source);
        if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) return;
        sourcePath = directory(source, false);
        Path staged = Files.createTempDirectory(root, ".fork-artifacts-");
        try {
            long total = 0;
            int count = 0;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(sourcePath, "*.txt")) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                        throw new HarnessException("ARTIFACT_DENIED", "Fork entry must be regular");
                    String name = file.getFileName().toString();
                    if (!name.matches("[0-9a-f-]{36}-[0-9a-f]{64}\\.txt"))
                        throw new HarnessException("ARTIFACT_DENIED", "Invalid fork artifact name");
                    byte[] bytes;
                    try (InputStream input =
                            Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                        bytes = input.readNBytes(LIMIT + 1);
                    }
                    if (++count > 1000 || (total += bytes.length) > 32 * 1024 * 1024)
                        throw new HarnessException("ARTIFACT_LIMIT", "Fork artifacts exceed bound");
                    if (bytes.length > LIMIT || !hash(bytes).equals(name.substring(37, 101)))
                        throw new HarnessException(
                                "ARTIFACT_CORRUPT", "Fork artifact integrity failed");
                    try (FileChannel channel =
                            FileChannel.open(
                                    staged.resolve(name),
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS)) {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) channel.write(buffer);
                        channel.force(true);
                    }
                }
            }
            checkRoot();
            if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS))
                throw new HarnessException("ARTIFACT_SCOPE_EXISTS", "Fork scope already exists");
            try {
                Files.move(staged, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException conflict) {
                throw new HarnessException(
                        "ARTIFACT_SCOPE_EXISTS", "Fork scope already exists", conflict);
            }
        } finally {
            if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) removeDirectory(staged);
        }
    }

    /** Compensation only for a newly copied, exclusively owned fork destination. */
    public void rollbackCopy(String target) throws IOException {
        validateScope(target);
        Path path = root.resolve(target);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            removeDirectory(directory(target, false));
    }

    private static void validateScope(String scope) {
        if (scope == null || !scope.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new HarnessException("INVALID_SESSION_ID", "Invalid artifact scope");
    }

    private static void removeDirectory(Path path) throws IOException {
        try (var entries = Files.newDirectoryStream(path)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    throw new HarnessException(
                            "ARTIFACT_DENIED", "Unexpected fork compensation entry");
                Files.delete(entry);
            }
        }
        Files.delete(path);
    }
}
