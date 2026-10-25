package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class R123CliBoundaryTest {
    @TempDir Path root;

    @Test
    void emptyPathOptionsAndInvalidUtf8HaveStableInputErrors() throws Exception {
        for (String flag : List.of("workspace", "config", "session", "state-dir", "expect-file"))
            assertEquals(
                    "CLI_USAGE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> new Arguments(new String[] {"--" + flag + "="}))
                            .code());
        assertEquals("", new Arguments(new String[] {"--text="}).get("text", "missing"));
        Path invalid = root.resolve("bad.json");
        Files.write(invalid, new byte[] {(byte) 0xc3, 0x28});
        assertEquals(
                "INVALID_JSON",
                assertThrows(HarnessException.class, () -> Json.read(invalid, 10)).code());
    }

    @Test
    void configurationCanBeRepairedBeforeCrossFieldValidation() throws Exception {
        root = root.toRealPath();
        var small = new CliConfig(Map.of("contextTokens", 2048));
        assertEquals(2048, small.maxOutputTokens());
        assertEquals(
                "COST_BUDGET_UNAVAILABLE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new CliConfig(
                                                        Map.of(
                                                                "maxCost",
                                                                "1",
                                                                "inputPerMillion",
                                                                "0",
                                                                "outputPerMillion",
                                                                "0"))
                                                .costs())
                        .code());
        Path config = root.resolve("config.json");
        Files.writeString(config, "{\"contextTokens\":2048,\"maxOutputTokens\":4096}");
        var output = new ByteArrayOutputStream();
        int exit =
                new CliApplication(Map.of())
                        .execute(
                                new String[] {
                                    "--workspace",
                                    root.toString(),
                                    "--config",
                                    config.toString(),
                                    "config",
                                    "set",
                                    "maxOutputTokens",
                                    "1024"
                                },
                                InputStream.nullInputStream(),
                                new PrintStream(output),
                                new PrintStream(output));
        assertEquals(0, exit, output.toString());
        assertEquals(1024, CliConfig.load(config).maxOutputTokens());
    }

    @Test
    void credentialWorkerClassifiesMalformedInputAndOutputFailureWithoutUsingNativeVault() {
        var out = new PrintStream(OutputStream.nullOutputStream());
        for (String invalid : List.of("{bad", "[7]"))
            assertEquals(
                    6,
                    NativeCredentials.execute(
                            new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8)),
                            out,
                            () -> {
                                throw new AssertionError("Malformed input reached vault");
                            }));
        var broken =
                new PrintStream(
                        new OutputStream() {
                            public void write(int value) throws IOException {
                                throw new IOException("fixture");
                            }
                        });
        String request = "[{\"action\":\"read\",\"target\":\"J-Harness/fixture\",\"value\":\"\"}]";
        assertEquals(
                7,
                NativeCredentials.execute(
                        new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)),
                        broken,
                        () -> (action, target, value) -> "synthetic-response"));
    }
}
