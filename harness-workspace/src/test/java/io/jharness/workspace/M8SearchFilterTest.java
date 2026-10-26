package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M8SearchFilterTest {
    @TempDir Path root;

    private void file(String name) throws Exception {
        Files.createDirectories(root.resolve(name).getParent());
        Files.writeString(root.resolve(name), "needle " + name);
    }

    private List<String> paths(WorkspaceFiles files) throws Exception {
        return ((List<?>) files.search("**/*.txt", 100, CancellationToken.NONE).get("paths"))
                .stream().map(String.class::cast).sorted().toList();
    }

    @Test
    void precedenceAppliesToListSearchGrepAndIndependentChildViews() throws Exception {
        for (String path :
                List.of(
                        "visible.txt",
                        "hidden.txt",
                        "cache/keep.txt",
                        "cache/drop.txt",
                        "target/out.txt",
                        "nested/target/out.txt")) file(path);
        Files.writeString(root.resolve(".gitignore"), "hidden.txt\ncache/\n");
        var config =
                new WorkspaceSearchFilter(
                        true,
                        true,
                        List.of(
                                "!hidden.txt",
                                "!cache/",
                                "cache/*",
                                "!cache/keep.txt",
                                "!target/"));
        var files = new LocalWorkspace(root, config);
        assertEquals(
                List.of(
                        "cache/keep.txt",
                        "hidden.txt",
                        "nested/target/out.txt",
                        "target/out.txt",
                        "visible.txt"),
                paths(files));
        assertEquals(paths(files), paths(files.isolatedView()));
        assertEquals(
                List.of(Map.of("path", "cache/keep.txt", "type", "file")),
                files.list(new ExecPath("cache"), 1, 100, CancellationToken.NONE).get("entries"));
        var matches =
                (List<?>)
                        files.grep(
                                        "needle",
                                        List.of(
                                                new ExecPath("cache/drop.txt"),
                                                new ExecPath("cache/keep.txt")),
                                        10,
                                        CancellationToken.NONE)
                                .get("matches");
        assertEquals(1, matches.size());
        assertEquals("cache/keep.txt", Values.object(matches.getFirst()).get("path"));
        assertTrue(files.readText(new ExecPath("cache/drop.txt")).contains("needle"));
        var pruned =
                new LocalWorkspace(
                        root, new WorkspaceSearchFilter(true, true, List.of("!cache/keep.txt")));
        assertFalse(paths(pruned).contains("cache/keep.txt"));
        assertEquals(
                List.of(),
                pruned.list(new ExecPath("cache"), 1, 10, CancellationToken.NONE).get("entries"));
        assertEquals(
                List.of(),
                pruned.grep(
                                "needle",
                                List.of(new ExecPath("cache/keep.txt")),
                                10,
                                CancellationToken.NONE)
                        .get("matches"));
    }

    @Test
    void anchorsDirectoryGlobsAndDisabledLayersHaveExplicitSemantics() throws Exception {
        for (String p :
                List.of(
                        "root.txt",
                        "nested/root.txt",
                        "nested/secret1.txt",
                        "中文 空格/keep.txt",
                        "target/out.txt",
                        "nested/cache/hidden.txt",
                        "logs")) file(p);
        Files.writeString(root.resolve(".gitignore"), "*.txt\n");
        var config =
                new WorkspaceSearchFilter(
                        false, false, List.of("/root.txt", "**/secret?.txt", "cache/", "logs/"));
        var files = new LocalWorkspace(root, config);
        assertEquals(List.of("nested/root.txt", "target/out.txt", "中文 空格/keep.txt"), paths(files));
        assertTrue(
                ((List<?>) files.search("logs", 10, CancellationToken.NONE).get("paths"))
                        .contains("logs"));
        Files.writeString(root.resolve(".gitignore"), "[unsupported]\n");
        assertEquals(3, paths(files).size());
        assertEquals(
                "INVALID_SEARCH_FILTER",
                assertThrows(HarnessException.class, () -> paths(new LocalWorkspace(root))).code());
    }

    @Test
    void ruleLimitsAndUnsupportedConfigurationFailWithoutTruncation() throws Exception {
        for (Object raw :
                List.of(
                        Map.of("extra", true),
                        Map.of("rules", "*.txt"),
                        Map.of("useGitignore", "false"),
                        Map.of("rules", List.of("../x")),
                        Map.of("rules", List.of("C:/x")),
                        Map.of("rules", List.of("a\\b")),
                        Map.of("rules", List.of("[a]")),
                        Map.of("rules", List.of("!!x")),
                        Map.of("rules", List.of(" trailing "))))
            assertEquals(
                    "INVALID_SEARCH_FILTER",
                    assertThrows(HarnessException.class, () -> WorkspaceSearchFilter.from(raw))
                            .code());
        assertEquals(
                "SEARCH_FILTER_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new WorkspaceSearchFilter(
                                                true, true, Collections.nCopies(129, "x")))
                        .code());
        assertEquals(
                "SEARCH_FILTER_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new WorkspaceSearchFilter(
                                                true, true, List.of("x".repeat(257))))
                        .code());
        assertEquals(
                "SEARCH_FILTER_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new WorkspaceSearchFilter(
                                                true,
                                                true,
                                                Collections.nCopies(100, "x".repeat(200))))
                        .code());
        Files.writeString(root.resolve(".gitignore"), "x\n".repeat(129));
        assertEquals(
                "SEARCH_FILTER_LIMIT",
                assertThrows(HarnessException.class, () -> paths(new LocalWorkspace(root))).code());
        Files.writeString(root.resolve(".gitignore"), "# comment\n\n*.log\n[private-rule-value]\n");
        var invalid = assertThrows(HarnessException.class, () -> paths(new LocalWorkspace(root)));
        assertEquals("INVALID_SEARCH_FILTER", invalid.code());
        assertTrue(invalid.getMessage().startsWith(".gitignore line 4:"));
        assertTrue(invalid.getMessage().contains("relative paths"));
        assertFalse(invalid.getMessage().contains("private-rule-value"));
        var configured =
                assertThrows(
                        HarnessException.class,
                        () ->
                                WorkspaceSearchFilter.from(
                                        Map.of("rules", List.of("# comment", "", "[private]"))));
        assertTrue(configured.getMessage().startsWith("Search filter rule 3:"));
        Files.writeString(root.resolve(".gitignore"), "# comment\n\n".repeat(129) + "*.log\n");
        assertDoesNotThrow(() -> paths(new LocalWorkspace(root)));
    }

    @Test
    void negationCannotExposeMetadataExternalPathsOrLinks() throws Exception {
        file(".git/secret.txt");
        file(".jharness/secret.txt");
        file("visible.txt");
        var filter =
                new WorkspaceSearchFilter(
                        false, false, List.of("!.git/", "!.jharness/", "!bridge/"));
        var files = new LocalWorkspace(root, filter);
        Path external = Files.createTempDirectory("jharness-m8-outside-");
        Path bridge = root.resolve("bridge");
        try {
            Files.writeString(external.resolve("secret.txt"), "private");
            if (System.getProperty("os.name").startsWith("Windows")) {
                var process =
                        new ProcessBuilder(
                                        "cmd",
                                        "/c",
                                        "mklink",
                                        "/J",
                                        bridge.toString(),
                                        external.toString())
                                .redirectErrorStream(true)
                                .start();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                assertEquals(
                        0,
                        process.exitValue(),
                        new String(process.getInputStream().readAllBytes()));
            } else Files.createSymbolicLink(bridge, external);
            assertEquals(List.of("visible.txt"), paths(files));
            for (String denied :
                    List.of(
                            ".git/secret.txt",
                            ".jharness/secret.txt",
                            "bridge/secret.txt",
                            "../outside.txt")) {
                assertEquals(
                        "PATH_DENIED",
                        assertThrows(
                                        HarnessException.class,
                                        () -> files.readText(new ExecPath(denied)))
                                .code());
                assertEquals(
                        "PATH_DENIED",
                        assertThrows(
                                        HarnessException.class,
                                        () ->
                                                files.grep(
                                                        "private",
                                                        List.of(new ExecPath(denied)),
                                                        10,
                                                        CancellationToken.NONE))
                                .code());
            }
            assertEquals("private", Files.readString(external.resolve("secret.txt")));
        } finally {
            Files.deleteIfExists(bridge);
            Files.deleteIfExists(external.resolve("secret.txt"));
            Files.deleteIfExists(external);
        }
    }
}
