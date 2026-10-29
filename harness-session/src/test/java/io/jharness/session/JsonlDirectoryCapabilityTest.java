package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlDirectoryCapabilityTest {
    @TempDir Path base;

    @Test
    void replacedRootRejectsReadLockForkAndCatalogWithoutTouchingReplacement() throws Exception {
        Path root = Files.createDirectory(base.resolve("root"));
        var repository = new JsonlSessionRepository(root);
        String event;
        try (var writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            event = writer.append(SessionEvent.session("s", "known", Map.of("value", "original")));
        }
        Path moved = base.resolve("original");
        Files.move(root, moved);
        Files.createDirectory(root);
        Files.writeString(root.resolve("s.jsonl"), "outside canary");
        Files.writeString(root.resolve("s.lock"), "outside lock");
        assertThrows(HarnessException.class, () -> repository.openReader("s").events());
        assertThrows(
                HarnessException.class,
                () -> repository.openWriter("new", WriteOwnership.EXCLUSIVE));
        assertThrows(HarnessException.class, repository::list);
        assertThrows(HarnessException.class, () -> repository.fork("s", event));
        assertEquals("outside canary", Files.readString(root.resolve("s.jsonl")));
        assertEquals("outside lock", Files.readString(root.resolve("s.lock")));
        assertFalse(Files.exists(root.resolve("new.lock")));
        assertEquals(1, new JsonlSessionRepository(moved).openReader("s").events().size());
    }

    @Test
    void openedWriterKeepsOriginalFileAndOwnershipAcrossDirectoryRename() throws Exception {
        Path root = Files.createDirectory(base.resolve("root"));
        var repository = new JsonlSessionRepository(root);
        Path moved = base.resolve("moved");
        boolean renamed = false;
        try (var writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            try {
                Files.move(root, moved);
                renamed = true;
            } catch (FileSystemException refused) {
                assertTrue(System.getProperty("os.name").startsWith("Windows"));
            }
            if (renamed) {
                Files.createDirectory(root);
                Files.writeString(root.resolve("s.jsonl"), "outside");
            }
            writer.append(SessionEvent.session("s", "known", Map.of("value", "bound")));
            writer.flush();
            if (renamed) assertEquals("outside", Files.readString(root.resolve("s.jsonl")));
        }
        assertEquals(
                1,
                new JsonlSessionRepository(renamed ? moved : root).openReader("s").events().size());
    }
}
