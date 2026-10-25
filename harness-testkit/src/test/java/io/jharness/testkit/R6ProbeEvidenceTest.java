package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R6ProbeEvidenceTest {
    @TempDir Path root;

    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            ProbeSupport.begin(args);
            if (args[0].equals("fail")) throw new AssertionError("controlled probe failure");
            Files.writeString(Path.of(args[3]), "changed during execution");
            ProbeSupport.publish(Path.of(args[4]), Map.of("result", "SUCCESS"));
        }
    }

    @Test
    void failedProbeAndChangedJarRevokeOldSuccessWithoutClaimingCleanup() throws Exception {
        for (String mode : List.of("fail", "change")) {
            Path jar = root.resolve(mode + ".jar"), report = root.resolve(mode + ".json");
            Files.writeString(jar, "initial");
            Files.writeString(report, "{\"result\":\"SUCCESS\"}");
            var process =
                    new ProcessBuilder(
                                    Path.of(
                                                    System.getProperty("java.home"),
                                                    "bin",
                                                    System.getProperty("os.name")
                                                                    .startsWith("Windows")
                                                            ? "java.exe"
                                                            : "java")
                                            .toString(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    FaultProbe.class.getName(),
                                    mode,
                                    "image",
                                    root.toString(),
                                    jar.toString(),
                                    report.toString())
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                assertNotEquals(0, process.exitValue());
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
            var value =
                    new ObjectMapper()
                            .readValue(
                                    Files.readAllBytes(report),
                                    new TypeReference<Map<String, Object>>() {});
            assertEquals("FAILED", value.get("result"));
            assertEquals(false, value.get("cleanupVerified"));
            if (mode.equals("change"))
                assertNotEquals(value.get("jarBeforeSha256"), value.get("jarAfterSha256"));
        }
    }
}
