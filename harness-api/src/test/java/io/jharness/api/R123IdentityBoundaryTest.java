package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class R123IdentityBoundaryTest {
    @TempDir Path root;

    @Test
    void stableKeysAndAnchorsDoNotDependOnFallbackModificationTime() {
        var a = new DirectoryIdentity("key", FileTime.fromMillis(1), "anchor");
        var b = new DirectoryIdentity("key", FileTime.fromMillis(2), "anchor");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new DirectoryIdentity("key", FileTime.fromMillis(1), "different"));
        assertEquals(
                new DirectoryIdentity(null, FileTime.fromMillis(1), "anchor"),
                new DirectoryIdentity(null, FileTime.fromMillis(2), "anchor"));
        assertNotEquals(
                new DirectoryIdentity(null, FileTime.fromMillis(1), null),
                new DirectoryIdentity(null, FileTime.fromMillis(2), null));
    }

    @Test
    void concurrentBindingsPublishOneCompleteAnchorAndNeverOverwriteInvalidIdentity()
            throws Exception {
        Path directory = Files.createDirectory(root.resolve("private")).toRealPath();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var tasks = new ArrayList<Future<DirectoryIdentity>>();
            for (int i = 0; i < 8; i++)
                tasks.add(
                        workers.submit(
                                () -> {
                                    gate.await();
                                    return DirectoryIdentity.bind(directory);
                                }));
            gate.countDown();
            var identity = tasks.getFirst().get(3, TimeUnit.SECONDS);
            for (var task : tasks) assertEquals(identity, task.get(3, TimeUnit.SECONDS));
        }
        assertTrue(
                Files.readString(directory.resolve(".directory-identity"))
                        .matches("[a-f0-9-]{36}"));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
        Files.writeString(directory.resolve(".directory-identity"), "bad");
        assertThrows(java.io.IOException.class, () -> DirectoryIdentity.bind(directory));
        assertEquals("bad", Files.readString(directory.resolve(".directory-identity")));
        assertDoesNotThrow(() -> DirectoryIdentity.readDirectory(directory));
    }

    @Test
    void environmentCredentialSuffixHasBoundaryWithoutIgnoringShortSecrets() {
        for (String name : List.of("SSH_ASKPASS", "GIT_ASKPASS", "COMPASS", "BYPASS"))
            assertFalse(SecretRedactor.credentialEnvironmentName(name));
        for (String name :
                List.of("TOKEN", "AWS_SECRET_ACCESS_KEY", "JHARNESS_API_KEY", "MY_TOKEN"))
            assertTrue(SecretRedactor.credentialEnvironmentName(name));
        assertEquals("[REDACTED]", new SecretRedactor(List.of("x")).text("x"));
    }
}
