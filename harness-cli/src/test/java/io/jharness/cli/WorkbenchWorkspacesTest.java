package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.JsonlSessionRepository;
import io.jharness.web.HarnessWebServer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchWorkspacesTest {
    @TempDir Path temp;

    private WorkbenchWorkspaces manager(Path initial) throws Exception {
        return new WorkbenchWorkspaces(
                new Metadata(initial.toRealPath()),
                new SecretRedactor(),
                root ->
                        new HarnessWebServer.Profile(
                                root.toString(),
                                new JsonlSessionRepository(
                                        new Metadata(root).directory("sessions")),
                                (id, approvals, sanitizer) -> {
                                    throw new AssertionError("Registration cannot construct SDK");
                                }));
    }

    @Test
    void registrationPersistsCanonicalRootsWithoutLoadingTargetConfig() throws Exception {
        Path a = Files.createDirectories(temp.resolve("a")).toRealPath(),
                b = Files.createDirectories(temp.resolve("b")).toRealPath();
        Files.createDirectories(b.resolve(".jharness"));
        Files.writeString(
                b.resolve(".jharness/config.json"), "this deliberately is not valid config");
        var manager = manager(a);
        var row = manager.register(b.toString(), "Second workspace");
        assertEquals(b.toRealPath().toString(), row.getValue().workspaceBinding());
        assertEquals(2, ((List<?>) manager(a).read().get("workspaces")).size());
        assertEquals(
                "WORKSPACE_EXISTS",
                assertThrows(
                                HarnessException.class,
                                () -> manager.register(b.toString(), "duplicate"))
                        .code());
        assertEquals(
                "this deliberately is not valid config",
                Files.readString(b.resolve(".jharness/config.json")));
        assertEquals(2, manager(a).profiles().size());
    }

    @Test
    void relativeTraversalMetadataSecretsLinksAndWrongOwnerAreRejected() throws Exception {
        Path a = Files.createDirectories(temp.resolve("a")),
                target = Files.createDirectories(temp.resolve("target")).toRealPath();
        var manager = manager(a);
        for (String path :
                List.of(
                        "relative",
                        target.resolve("..").toString(),
                        a.resolve(".jharness").toString(),
                        Files.createDirectories(temp.resolve(".ssh")).toString(),
                        "\\\\server\\share\\folder"))
            assertEquals(
                    "WORKSPACE_PATH_DENIED",
                    assertThrows(HarnessException.class, () -> manager.register(path, "rejected"))
                            .code());
        assertEquals(
                "WORKSPACE_OWNER_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new WorkbenchWorkspaces.RootCheck(
                                                        target, "different-owner-fixture")
                                                .verify())
                        .code());
        Path link = temp.toRealPath().resolve("alias");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var process =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    link.toString(),
                                    target.toString())
                            .redirectErrorStream(true)
                            .start();
            process.getInputStream().readAllBytes();
            assertEquals(0, process.waitFor());
        } else Files.createSymbolicLink(link, target);
        try {
            assertThrows(
                    HarnessException.class, () -> manager.register(link.toString(), "linked root"));
        } finally {
            Files.delete(link);
        }
        assertTrue(Files.isDirectory(target));
        assertEquals(1, manager.profiles().size());
    }

    @Test
    void workspaceOwnedByAnotherHostCannotBeRegistered() throws Exception {
        Path a = Files.createDirectories(temp.resolve("a")),
                target = Files.createDirectories(temp.resolve("target"));
        var sessions =
                new JsonlSessionRepository(new Metadata(target.toRealPath()).directory("sessions"));
        try (var owner = sessions.openWriter("web-workspace-owner", WriteOwnership.EXCLUSIVE)) {
            owner.flush();
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () -> manager(a).register(target.toString(), "busy"))
                            .code());
        }
        assertEquals(1, manager(a).profiles().size());
    }
}
