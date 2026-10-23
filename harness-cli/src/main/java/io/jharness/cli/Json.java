package io.jharness.cli;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import io.jharness.api.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class Json {
    private Json() {}

    static final ObjectMapper MAPPER =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(32)
                                                    .maxStringLength(1024 * 1024)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static Object parse(String text) {
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (IOException ex) {
            throw new HarnessException("INVALID_JSON", "Invalid bounded JSON");
        }
    }

    static Map<String, Object> read(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException("CONFIG_PATH_DENIED", "Expected regular file");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit)
                throw new HarnessException("CONFIG_LIMIT", "Input file exceeds limit");
            return Values.object(parse(new String(bytes, StandardCharsets.UTF_8)));
        }
    }

    static String text(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException ex) {
            throw new HarnessException("JSON_ENCODING", "Unable to encode report");
        }
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void write(Path path, byte[] bytes) throws IOException {
        if (bytes.length > 8 * 1024 * 1024)
            throw new HarnessException("REPORT_LIMIT", "Output exceeds bounded report size");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new HarnessException("METADATA_PATH_DENIED", "Output must be regular file");
        Path temp = path.resolveSibling(".write-" + UUID.randomUUID());
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            temp,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) AtomicFileMetadata.copy(path, temp);
            Files.move(
                    temp,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
