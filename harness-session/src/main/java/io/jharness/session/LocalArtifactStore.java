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
    private final DataSanitizer sanitizer;

    public LocalArtifactStore(Path root, DataSanitizer sanitizer) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    private Path directory(String session, boolean create) throws IOException {
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
        return path;
    }

    @Override
    public Artifact put(String session, String content) throws IOException {
        String clean = (String) sanitizer.sanitize(Map.of("content", content)).get("content");
        byte[] bytes = clean.getBytes(StandardCharsets.UTF_8);
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
        return text.substring(
                Math.min(offset, text.length()),
                (int) Math.min(text.length(), (long) offset + limit));
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
        Path sourcePath = root.resolve(source);
        if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) return;
        sourcePath = directory(source, false);
        Path targetPath = directory(target, true);
        long total = 0;
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(sourcePath, "*.txt")) {
            for (Path file : files) {
                if (++count > 1000 || (total += Files.size(file)) > 32 * 1024 * 1024)
                    throw new HarnessException("ARTIFACT_LIMIT", "Fork artifacts exceed bound");
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - 4);
                read(source, id, 0, 1);
                byte[] bytes;
                try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(LIMIT + 1);
                }
                if (bytes.length > LIMIT || !hash(bytes).equals(id.substring(37)))
                    throw new HarnessException(
                            "ARTIFACT_CORRUPT", "Fork artifact integrity failed");
                try (FileChannel channel =
                        FileChannel.open(
                                targetPath.resolve(name),
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
            }
        }
    }
}
