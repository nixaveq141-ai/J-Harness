package io.jharness.ecosystem;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.zip.*;

/** Owned, read-only native dependencies from the selected trusted worker artifact. */
final class DockerNativeLibraries implements Closeable {
    private final Path root;

    private DockerNativeLibraries(Path root) {
        this.root = root;
    }

    Path path() {
        return root;
    }

    static DockerNativeLibraries prepare(Path jar) throws IOException {
        ZipFile archive;
        try {
            archive = new ZipFile(jar.toFile());
        } catch (ZipException legacyFixture) {
            return null;
        }
        try (archive) {
            var platforms = List.of("linux-x86-64", "linux-aarch64");
            for (String platform : platforms)
                if (archive.getEntry("com/sun/jna/" + platform + "/libjnidispatch.so") == null)
                    return null;
            var owned = new DockerNativeLibraries(Files.createTempDirectory("jharness-native-"));
            try {
                if (owned.root.toString().contains(","))
                    throw new IOException("Invalid native mount path");
                for (String platform : platforms) {
                    Path directory = Files.createDirectory(owned.root.resolve(platform));
                    try (var input =
                            archive.getInputStream(
                                    archive.getEntry(
                                            "com/sun/jna/" + platform + "/libjnidispatch.so"))) {
                        byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1);
                        if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024)
                            throw new IOException("Native dependency size invalid");
                        Path file = directory.resolve("libjnidispatch.so");
                        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
                        mode(file, "r--r--r--");
                    }
                    mode(directory, "r-xr-xr-x");
                }
                mode(owned.root, "r-xr-xr-x");
                return owned;
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    owned.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    private static void mode(Path path, String mode) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        mode(root, "rwx------");
        for (String platform : List.of("linux-x86-64", "linux-aarch64")) {
            Path directory = root.resolve(platform);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            mode(directory, "rwx------");
            Files.deleteIfExists(directory.resolve("libjnidispatch.so"));
            Files.delete(directory);
        }
        Files.delete(root);
    }
}
