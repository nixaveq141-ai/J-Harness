package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M9DistributionDoctorTest {
    @TempDir Path root;

    @Test
    void packagedRuntimeDoesNotRequireMavenInTargetWorkspace() throws Exception {
        Path config = root.resolve("config.json");
        Files.writeString(
                config,
                """
                {"baseUrl":"http://127.0.0.1:1","model":"fixture","apiKeyEnv":"M9_TEST_KEY"}
                """);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code =
                new CliApplication(
                                Map.of(
                                        "JAVA_HOME", System.getProperty("java.home"),
                                        "M9_TEST_KEY", "local-fixture-only",
                                        "PATH", root.toString()))
                        .execute(
                                new String[] {
                                    "doctor",
                                    "--workspace",
                                    root.toString(),
                                    "--config",
                                    config.toString()
                                },
                                InputStream.nullInputStream(),
                                new PrintStream(output, true, StandardCharsets.UTF_8),
                                new PrintStream(errors, true, StandardCharsets.UTF_8));
        String json = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, errors.toString(StandardCharsets.UTF_8) + json);
        assertTrue(json.contains("\"mavenWrapperConfigured\":false"));
        assertTrue(json.contains("\"mavenExecutableFound\":false"));
        assertTrue(json.contains("\"ready\":true"));
        assertFalse(json.contains("local-fixture-only"));
    }
}
