package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M7ChildEnvironmentTest {
    @TempDir Path root;

    @Test
    void closingBorrowedViewRevokesReadsWithoutClosingParent() throws Exception {
        Files.writeString(root.resolve("input.txt"), "before");
        try (var parent = new LocalExecutionEnvironment("parent", root, List.of())) {
            var child = new ReadOnlyChildEnvironment(parent, "child", () -> false);
            var retained = child.files();
            var path = new ExecPath("input.txt");
            assertEquals("before", retained.readText(path));
            assertThrows(HarnessException.class, () -> retained.writeText(path, "changed", null));
            assertThrows(
                    HarnessException.class,
                    () ->
                            child.processes()
                                    .execute(List.of("forbidden"), new ExecPath("."), () -> false));
            child.close();
            child.close();
            assertThrows(HarnessException.class, () -> retained.readText(path));
            assertThrows(HarnessException.class, () -> retained.versionOf(path));
            assertEquals("before", parent.files().readText(path));
            assertEquals("before", Files.readString(root.resolve("input.txt")));
        }
    }
}
