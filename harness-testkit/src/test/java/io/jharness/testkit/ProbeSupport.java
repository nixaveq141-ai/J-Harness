package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Opt-in probes retain owned evidence on failure and bind execution to a stable JAR snapshot. */
final class ProbeSupport {
    private static Path jar, output;
    private static String before;
    private static Map<String, ?> progress = Map.of();

    private ProbeSupport() {}

    static String hash(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) >= 0; ) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void begin(String[] args) throws IOException {
        if (args.length != 5)
            throw new IllegalArgumentException("Expected DOCKER IMAGE BASE JAR OUTPUT");
        output = Path.of(args[4]).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(output)) throw new IOException("Linked probe output");
        Files.createDirectories(output.getParent());
        write(output, Map.of("result", "INCOMPLETE"));
        jar = Path.of(args[3]).toRealPath();
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Probe JAR missing");
        before = hash(jar);
        write(output, Map.of("result", "INCOMPLETE", "jarSha256", before));
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, failure) -> {
                    var report = new LinkedHashMap<String, Object>();
                    report.put("result", "FAILED");
                    report.put("failureType", failure.getClass().getSimpleName());
                    if (failure instanceof io.jharness.api.HarnessException known
                            && Set.of(
                                            "ENVIRONMENT_FAILED",
                                            "ENVIRONMENT_UNAVAILABLE",
                                            "ENVIRONMENT_CLOSED",
                                            "ENVIRONMENT_PROTOCOL",
                                            "ENVIRONMENT_IO",
                                            "PATH_DENIED",
                                            "FILE_CONFLICT",
                                            "ATOMIC_MOVE_UNSUPPORTED")
                                    .contains(known.code()))
                        report.put("failureCode", known.code());
                    if (Set.of(
                                    "inspect",
                                    "network",
                                    "readonly root",
                                    "caps",
                                    "privileges",
                                    "nonroot",
                                    "quotas")
                            .contains(Objects.toString(failure.getMessage(), "")))
                        report.put("failureCheck", failure.getMessage());
                    report.put("completedEvidence", progress);
                    report.put("cleanupVerified", false);
                    report.put(
                            "cleanupBoundary",
                            "Failure does not prove release of every external resource; inspect owned evidence");
                    report.put("jarBeforeSha256", before);
                    try {
                        report.put("jarAfterSha256", hash(jar));
                        write(output, report);
                    } catch (Exception recording) {
                        failure.addSuppressed(recording);
                    }
                    System.err.println("Probe FAILED; inspect owned evidence report");
                });
    }

    static void fixtureWritable(Path directory) throws IOException {
        Path root = directory.toRealPath();
        if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(root))
                    throw new IOException("Linked probe fixture");
                Files.setPosixFilePermissions(
                        path,
                        java.nio.file.attribute.PosixFilePermissions.fromString(
                                Files.isDirectory(path) ? "rwxrwxrwx" : "rw-rw-rw-"));
            }
        }
    }

    static void track(Map<String, ?> current) {
        progress = current;
    }

    static void publish(Path target, Map<String, Object> value) throws IOException {
        String after = hash(jar);
        if (!before.equals(after)) throw new IOException("Probe JAR changed during execution");
        var report = new LinkedHashMap<>(value);
        report.put("jarSha256", before);
        report.put("jarUnchanged", true);
        report.put(
                "cleanupBoundary",
                "Managed resources checked by this probe; evidence directories intentionally retained");
        write(target, report);
    }

    private static void write(Path target, Map<String, ?> value) throws IOException {
        byte[] bytes = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Path temporary =
                Files.createTempFile(target.toAbsolutePath().getParent(), ".probe-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
