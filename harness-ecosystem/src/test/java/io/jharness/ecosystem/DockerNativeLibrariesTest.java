package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerNativeLibrariesTest {
    @TempDir Path base;

    @Test
    void trustedArtifactBytesRemainReadOnlyAndOwnedCleanupIsRepeatable() throws Exception {
        Path jar = base.resolve("worker.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String platform : List.of("linux-x86-64", "linux-aarch64")) {
                zip.putNextEntry(new ZipEntry("com/sun/jna/" + platform + "/libjnidispatch.so"));
                zip.write(platform.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }
        var libraries = DockerNativeLibraries.prepare(jar);
        assertNotNull(libraries);
        Path root = libraries.path();
        try (libraries) {
            for (String platform : List.of("linux-x86-64", "linux-aarch64")) {
                Path file = root.resolve(platform).resolve("libjnidispatch.so");
                assertEquals(platform, Files.readString(file));
                if (Files.getFileStore(file).supportsFileAttributeView("posix"))
                    assertTrue(
                            Files.getPosixFilePermissions(file).stream()
                                    .noneMatch(p -> p.name().endsWith("WRITE")));
            }
        }
        assertFalse(Files.exists(root));
        libraries.close();
    }

    @Test
    void legacyWorkerWithoutNativePayloadDoesNotCreateAMount() throws Exception {
        Path jar = base.resolve("legacy.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("legacy"));
            zip.closeEntry();
        }
        assertNull(DockerNativeLibraries.prepare(jar));
    }
}
