package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Packaged CLI -> Web -> SDK -> local/Docker acceptance, with parallel reads and approved write.
 */
public final class M8SchedulingDockerProbe {
    private M8SchedulingDockerProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("DOCKER IMAGE BASE JAR OUTPUT");
        Path base = Path.of(args[2]).toAbsolutePath().resolve("scheduling-" + UUID.randomUUID());
        Path jar = Path.of(args[3]).toRealPath();
        var cases = new LinkedHashMap<String, Object>();
        cases.put(
                "local",
                M7WebDockerProbe.run(args[0], args[1], base.resolve("local"), jar, false, true));
        cases.put(
                "docker",
                M7WebDockerProbe.run(args[0], args[1], base.resolve("docker"), jar, true, true));
        var report = new LinkedHashMap<String, Object>();
        report.put("milestone", "M8.2");
        report.put("slice", "packaged-cli-web-sdk-readonly-scheduling");
        report.put("actualDocker", true);
        report.put("cases", cases);
        report.put("image", args[1]);
        report.put("result", "SUCCESS");
        report.put("cleanupVerified", true);
        report.put("capturedAt", Instant.now().toString());
        report.put("evidenceDirectory", base.toString());
        report.put(
                "jarSha256",
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(jar))));
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(Path.of(args[4]).toFile(), report);
        System.out.println("M8.2 packaged CLI/Web/SDK/local/Docker scheduling probe SUCCESS");
    }
}
