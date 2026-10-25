package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.JsonlSessionRepository;
import io.jharness.web.HarnessWebServer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2RegistryTest {
    @TempDir Path temp;

    @Test
    void jsonReplacementKeepsExistingOwnerWithoutReassigningSameOwner() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("r2-owner")).toRealPath();
        Path file = directory.resolve("state.json");
        try {
            Json.write(file, "{\"before\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var owner = Files.getOwner(file);
            var posix =
                    Files.getFileAttributeView(
                            file, java.nio.file.attribute.PosixFileAttributeView.class);
            if (posix != null)
                posix.setPermissions(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            var group = posix == null ? null : posix.readAttributes().group();
            var acl =
                    Files.getFileAttributeView(
                            file, java.nio.file.attribute.AclFileAttributeView.class);
            var entries = acl == null ? List.of() : acl.getAcl();
            Json.write(file, "{\"after\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("{\"after\":true}", Files.readString(file));
            assertEquals(owner, Files.getOwner(file));
            if (acl != null) assertEquals(entries, acl.getAcl());
            if (posix != null) {
                assertEquals(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                        Files.getPosixFilePermissions(file));
                assertEquals(group, posix.readAttributes().group());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    @Test
    void replacedDirectoryIdentityRevokesMetadataAndWorkspaceChecks() throws Exception {
        Path root = Files.createDirectory(temp.resolve("bound")).toRealPath();
        var metadata = new Metadata(root);
        metadata.directory("sessions");
        var identity = new WorkbenchWorkspaces.RootCheck(root);
        Files.move(root, root.resolveSibling("previous"), StandardCopyOption.ATOMIC_MOVE);
        Files.createDirectory(root);
        assertEquals(
                "METADATA_PATH_DENIED",
                assertThrows(HarnessException.class, () -> metadata.directory("reports")).code());
        assertEquals(
                "WORKSPACE_PATH_DENIED",
                assertThrows(HarnessException.class, identity::verify).code());
        assertFalse(Files.exists(root.resolve(".jharness")));
    }

    private WorkbenchWorkspaces manager(Path root) throws Exception {
        return new WorkbenchWorkspaces(
                new Metadata(root),
                new SecretRedactor(),
                path ->
                        new HarnessWebServer.Profile(
                                path.toString(),
                                new JsonlSessionRepository(
                                        new Metadata(path).directory("sessions")),
                                (id, approvals, sanitizer) -> {
                                    throw new AssertionError("No execution during registry load");
                                }));
    }

    @Test
    void malformedRegistryPathReportsStableCodeWithoutRewritingFile() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace")).toRealPath();
        Path file = new Metadata(root).directory("settings").resolve("workspaces.json");
        Files.writeString(
                file,
                Json.text(
                        Map.of(
                                "version",
                                1,
                                "workspaces",
                                List.of(Map.of("id", "bad", "name", "Bad root", "path", 123)))));
        byte[] original = Files.readAllBytes(file);
        assertEquals(
                "INVALID_WORKSPACE_REGISTRY",
                assertThrows(HarnessException.class, () -> manager(root)).code());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void missingRegisteredRootIsVisibleAsUnavailableAndRestoredRootCanReopen() throws Exception {
        Path root = Files.createDirectory(temp.resolve("workspace")).toRealPath();
        Path missing = temp.toRealPath().resolve("missing");
        Path file = new Metadata(root).directory("settings").resolve("workspaces.json");
        Files.writeString(
                file,
                Json.text(
                        Map.of(
                                "version",
                                1,
                                "workspaces",
                                List.of(
                                        Map.of(
                                                "id",
                                                "old",
                                                "name",
                                                "Missing root",
                                                "path",
                                                missing.toString())))));
        var registry = manager(root);
        assertEquals(Map.of("old", "WORKSPACE_NOT_FOUND"), registry.read().get("unavailable"));
        assertEquals(1, registry.profiles().size());
        assertEquals(2, ((List<?>) registry.read().get("workspaces")).size());
        Files.createDirectory(missing);
        assertEquals(2, manager(root).profiles().size());
    }

    @Test
    void metadataRejectsNonSegmentsButKeepsExplicitRootAccess() throws Exception {
        try (var zip =
                FileSystems.newFileSystem(
                        java.net.URI.create("jar:" + temp.resolve("root.zip").toUri()),
                        Map.of("create", "true"))) {
            assertEquals(
                    "WORKSPACE_PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            new WorkbenchWorkspaces(
                                                    new Metadata(zip.getPath("/")),
                                                    new SecretRedactor(),
                                                    path -> {
                                                        throw new AssertionError(
                                                                "Root must reject before factory or owner I/O");
                                                    }))
                            .code());
        }
        var metadata = new Metadata(temp.toRealPath());
        for (String segment : List.of("../outside", "a/b", "..", "a\\b"))
            assertEquals(
                    "METADATA_PATH_DENIED",
                    assertThrows(HarnessException.class, () -> metadata.directory(segment)).code());
        assertEquals(temp.toRealPath().resolve(".jharness"), metadata.directory(""));
    }
}
