package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R1CredentialCleanupTest {
    @Test
    void invalidArgumentsAndAbsentFixtureCannotClaimClean() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkbenchCredentialCleanup.main(new String[0]));
        assertThrows(
                Exception.class,
                () ->
                        WorkbenchCredentialCleanup.main(
                                new String[] {
                                    ".build-cache/M10-settings-absent-" + UUID.randomUUID()
                                }));
    }

    @Test
    void malformedModelsAndUnknownReferencesAreRejectedBeforeVaultAccess() throws Exception {
        Path base = Files.createDirectories(Path.of(".build-cache")).toRealPath();
        Path fixture = Files.createDirectory(base.resolve("M10-settings-" + UUID.randomUUID()));
        Path metadata = Files.createDirectories(fixture.resolve(".jharness/settings"));
        Path file = metadata.resolve("workbench.json");
        try {
            for (String invalid :
                    List.of(
                            "{}",
                            "{\"models\":[]}",
                            "{\"models\":{}}",
                            "{\"models\":[7]}",
                            "{\"models\":[{\"credentialRef\":\"unknown\"}]}")) {
                Files.writeString(file, invalid);
                assertThrows(
                        Exception.class,
                        () -> WorkbenchCredentialCleanup.main(new String[] {fixture.toString()}));
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(metadata);
            Files.delete(metadata.getParent());
            Files.delete(fixture);
        }
    }

    @Test
    void linkedMetadataCannotReadOrDeleteSiblingFixtureCredentials() throws Exception {
        Path base = Files.createDirectories(Path.of(".build-cache")).toRealPath();
        Path fixture = Files.createDirectory(base.resolve("M10-settings-" + UUID.randomUUID()));
        Path sibling =
                Files.createDirectory(base.resolve("R1-cleanup-sibling-" + UUID.randomUUID()));
        Path settings = Files.createDirectory(sibling.resolve("settings"));
        Path secretFile = settings.resolve("workbench.json");
        String sentinel = "{\"models\":[{\"credentialRef\":\"" + "a".repeat(64) + "\"}]}";
        Files.writeString(secretFile, sentinel);
        Path link = fixture.resolve(".jharness");
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                var command =
                        new ProcessBuilder(
                                        "cmd.exe",
                                        "/c",
                                        "mklink",
                                        "/J",
                                        link.toString(),
                                        sibling.toString())
                                .redirectErrorStream(true)
                                .start();
                assertTrue(command.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, command.exitValue());
            } else Files.createSymbolicLink(link, sibling);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WorkbenchCredentialCleanup.main(new String[] {fixture.toString()}));
            assertEquals(sentinel, Files.readString(secretFile));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(secretFile);
            Files.delete(settings);
            Files.delete(sibling);
            Files.delete(fixture);
        }
    }
}
