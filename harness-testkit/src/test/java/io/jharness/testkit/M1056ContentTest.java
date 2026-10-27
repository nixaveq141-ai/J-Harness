package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.LocalArtifactStore;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class M1056ContentTest {
    @TempDir Path root;
    static final String SECRET = "synthetic-m1056-secret-content";
    Path work;
    LocalWorkspace files;
    LocalArtifactStore store;
    ContentInspection service;

    @BeforeEach
    void setup() throws Exception {
        work = Files.createDirectory(root.resolve("workspace"));
        files = new LocalWorkspace(work);
        var redactor = new SecretRedactor(List.of(SECRET));
        store = new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor());
        service =
                new WorkspaceContentInspection(
                        files, store, redactor, new DefaultPolicyEngine(), () -> {});
    }

    @Test
    void cachedPagesKeepExactLinesUnicodeAndRejectChangedSanitizedVersions() {
        var cache = new ContentPages.Cache();
        String text = ("a\n".repeat(8191) + "\uD83D\uDE80\n").repeat(4);
        String digest = ContentPages.sha(text);
        for (int offset : new int[] {0, 1, 16382, 16384, 16385, 32769, text.length()}) {
            var page = cache.page(text, offset, digest);
            assertEquals(ContentPages.page(text, offset, digest), page);
            assertEquals(
                    1L + text.substring(0, offset).chars().filter(c -> c == '\n').count(),
                    page.get("startLine"));
            assertEquals(digest, page.get("contentSha256"));
        }
        assertEquals(
                "INVALID_RANGE",
                assertThrows(HarnessException.class, () -> cache.page(text, 16383, digest)).code());
        assertEquals(
                "CONTENT_CHANGED",
                assertThrows(
                                HarnessException.class,
                                () -> cache.page(text.replace('a', 'b'), 0, digest))
                        .code());
        assertEquals("new", cache.page("new", 0, "").get("text"));
        assertEquals(digest, cache.page(text, 0, "").get("contentSha256"));
    }

    @Test
    void warmArtifactSnapshotStillRejectsAllByteChangesDeletionAndWrongScope() throws Exception {
        String text = "a".repeat(70000) + "\uD83D\uDE80tail";
        var artifact = store.put("own", text);
        Path path = root.resolve("artifacts/own").resolve(artifact.id() + ".txt");
        byte[] original = Files.readAllBytes(path);
        var time = Files.getLastModifiedTime(path);
        assertEquals(text, store.readText("own", artifact.id()));
        byte[] changed = original.clone();
        changed[66000] = 'b';
        for (byte[] invalid :
                List.of(
                        changed,
                        Arrays.copyOf(original, original.length - 1),
                        Arrays.copyOf(original, original.length + 1))) {
            Files.write(path, invalid);
            Files.setLastModifiedTime(path, time);
            assertEquals(
                    "ARTIFACT_CORRUPT",
                    assertThrows(HarnessException.class, () -> store.readText("own", artifact.id()))
                            .code());
            Files.write(path, original);
            Files.setLastModifiedTime(path, time);
            assertEquals(text, store.readText("own", artifact.id()));
        }
        store.put("other", "other");
        assertEquals(
                "ARTIFACT_NOT_FOUND",
                assertThrows(HarnessException.class, () -> store.readText("other", artifact.id()))
                        .code());
        Files.delete(path);
        assertEquals(
                "ARTIFACT_NOT_FOUND",
                assertThrows(HarnessException.class, () -> store.readText("own", artifact.id()))
                        .code());
    }

    @Test
    void warmContentSnapshotReauthorizesAndResanitizesEveryPage() throws Exception {
        var current =
                new java.util.concurrent.atomic.AtomicReference<DataSanitizer>(
                        new SecretRedactor());
        var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var inspection =
                new WorkspaceContentInspection(
                        files,
                        store,
                        data -> current.get().sanitize(data),
                        (call, context) ->
                                allowed.get()
                                        ? PolicyEngine.Decision.ALLOW
                                        : PolicyEngine.Decision.DENY,
                        () -> {},
                        "own");
        String text = "visible\n".repeat(3000) + SECRET;
        var artifact = store.put("own", text);
        var first = inspection.artifact("own", artifact.id(), 0, "", CancellationToken.NONE);
        current.set(new SecretRedactor(List.of(SECRET)));
        assertEquals(
                "CONTENT_CHANGED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        inspection.artifact(
                                                "own",
                                                artifact.id(),
                                                16384,
                                                first.get("contentSha256").toString(),
                                                CancellationToken.NONE))
                        .code());
        var second = inspection.artifact("own", artifact.id(), 16384, "", CancellationToken.NONE);
        assertFalse(second.get("text").toString().contains(SECRET));
        assertTrue(second.get("text").toString().contains("[REDACTED]"));
        allowed.set(false);
        assertEquals(
                "INSPECTION_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        inspection.artifact(
                                                "own",
                                                artifact.id(),
                                                0,
                                                "",
                                                CancellationToken.NONE))
                        .code());
    }

    @Test
    void scopedInspectionNeverReadsAnotherExistingSessionArtifact() throws Exception {
        var own = store.put("own", "own text");
        var other = store.put("other", "other text");
        var scoped =
                new WorkspaceContentInspection(
                        files,
                        store,
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        () -> {},
                        "own");
        assertEquals(
                "own text",
                scoped.artifact("own", own.id(), 0, "", CancellationToken.NONE).get("text"));
        assertEquals(
                "ARTIFACT_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        scoped.artifact(
                                                "other", other.id(), 0, "", CancellationToken.NONE))
                        .code());
    }

    @Test
    void streamingSanitizersComposeWithFullSecretWindowsAndFailClosedByDefault() {
        String other = "unused-route-private-secret";
        var chain =
                DataSanitizer.chain(
                        new SecretRedactor(List.of(SECRET)), new SecretRedactor(List.of(other)));
        String input = "visible " + "x".repeat(200) + other.substring(0, 10);
        assertTrue(chain.streamingText(input).startsWith("visible"));
        assertFalse(chain.streamingText(input).contains(other.substring(0, 10)));
        String streamed =
                chain.streamingText(
                        "visible " + "x".repeat(200) + other + SECRET + "tail".repeat(30));
        assertFalse(streamed.contains(other));
        assertFalse(streamed.contains(SECRET));
        DataSanitizer unknown = data -> data;
        assertEquals("", unknown.streamingText("untrusted partial"));
        assertEquals("", DataSanitizer.chain(unknown, new SecretRedactor()).streamingText(input));
    }

    @Test
    void completeSanitizationPrecedesFilePagingAndUnicodeBoundary() throws Exception {
        String text = "x".repeat(16370) + SECRET + "\n" + "事实".repeat(9000);
        Files.writeString(work.resolve("source.txt"), text);
        var first = service.file("source.txt", 0, 0, "", CancellationToken.NONE);
        String version = (String) first.get("fileVersion"),
                clean = text.replace(SECRET, "[REDACTED]");
        var next =
                service.file(
                        "source.txt",
                        ((Number) first.get("nextOffset")).intValue(),
                        0,
                        version,
                        CancellationToken.NONE);
        assertEquals(clean.substring(0, 32768), first.get("text").toString() + next.get("text"));
        assertEquals(ContentPages.sha(clean), first.get("contentSha256"));
        assertFalse(first.toString().contains(SECRET));
        assertFalse(next.toString().contains(SECRET));
        var page = ContentPages.page("x".repeat(16383) + "\uD83D\uDE80" + "tail", 0, "");
        assertEquals(16383, page.get("nextOffset"));
        assertThrows(HarnessException.class, () -> ContentPages.page("a\uD83D\uDE80", 2, ""));
    }

    @Test
    void rawVersionChangesEvenWhenSanitizedContentIsIdentical() throws Exception {
        Files.writeString(work.resolve("source.txt"), SECRET);
        var first = service.file("source.txt", 0, 0, "", CancellationToken.NONE);
        var version = (String) first.get("fileVersion");
        Files.writeString(work.resolve("source.txt"), "[REDACTED]");
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        service.file(
                                                "source.txt",
                                                0,
                                                0,
                                                version,
                                                CancellationToken.NONE))
                        .code());
        assertEquals(
                first.get("contentSha256"),
                service.file("source.txt", 0, 0, "", CancellationToken.NONE).get("contentSha256"));
    }

    @Test
    void previewDoesNotGrantModelVersionObservation() throws Exception {
        Files.writeString(work.resolve("source.txt"), "old");
        service.file("source.txt", 0, 0, "", CancellationToken.NONE);
        assertEquals(
                "FILE_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        files.write(
                                                new ExecPath("source.txt"),
                                                "new",
                                                true,
                                                null,
                                                CancellationToken.NONE))
                        .code());
        assertEquals("old", Files.readString(work.resolve("source.txt")));
    }

    @Test
    void directoriesAndExplicitReadsKeepPathMetadataAndBinaryBounds() throws Exception {
        Files.createDirectory(work.resolve("src"));
        Files.writeString(work.resolve("src/A.java"), "class A {}");
        Files.writeString(work.resolve(".env.private"), SECRET);
        Files.createDirectory(work.resolve(".aws"));
        Files.writeString(work.resolve(".aws/secret"), "protected sentinel");
        Files.createDirectories(work.resolve(".git"));
        Files.writeString(work.resolve(".git/config"), "protected git sentinel");
        Files.createDirectories(work.resolve(".jharness"));
        Files.writeString(work.resolve(".jharness/config"), "protected metadata sentinel");
        Files.writeString(root.resolve("outside.txt"), "external sentinel");
        String listing = service.list(".", CancellationToken.NONE).toString();
        assertTrue(listing.contains("src"));
        assertFalse(listing.contains(".env"));
        assertFalse(listing.contains(".aws"));
        for (String path :
                List.of(
                        "../outside.txt",
                        ".git/config",
                        ".jharness/config",
                        ".aws/secret",
                        ".env.private",
                        work.toString() + "/src/A.java"))
            assertEquals(
                    (path.startsWith("../") || path.startsWith(work.toString()))
                            ? "INSPECTION_DENIED"
                            : "PATH_DENIED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> service.file(path, 0, 0, "", CancellationToken.NONE),
                                    path)
                            .code());
        Files.write(work.resolve("binary.txt"), new byte[] {1, 0, 2});
        assertEquals(
                "BINARY_FILE",
                assertThrows(
                                HarnessException.class,
                                () -> service.file("binary.txt", 0, 0, "", CancellationToken.NONE))
                        .code());
        Files.writeString(work.resolve("large.txt"), "x".repeat(1048577));
        assertEquals(
                "FILE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> service.file("large.txt", 0, 0, "", CancellationToken.NONE))
                        .code());
    }

    @Test
    void linkTraversalCannotFallBackToSiblingRoot() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "sibling-sentinel");
        Path link = work.resolve("link");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var process =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    link.toString(),
                                    outside.toString())
                            .redirectErrorStream(true)
                            .start();
            String output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), output);
        } else Files.createSymbolicLink(link, outside);
        try {
            assertThrows(
                    HarnessException.class,
                    () -> service.file("link/secret.txt", 0, 0, "", CancellationToken.NONE));
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void artifactScopeIntegrityAndFullRedactionBeforePages() throws Exception {
        String content = "a".repeat(16370) + SECRET + "z".repeat(20000);
        var artifact = store.put("first", content);
        var first = service.artifact("first", artifact.id(), 0, "", CancellationToken.NONE);
        assertEquals(
                ContentPages.sha(content.replace(SECRET, "[REDACTED]")),
                first.get("contentSha256"));
        assertNotEquals(artifact.sha256(), first.get("contentSha256"));
        assertFalse(first.toString().contains(SECRET));
        var other = store.put("second", "actual second session text");
        var scoped =
                new WorkspaceContentInspection(
                        files,
                        store,
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        () -> {},
                        "first");
        assertEquals(
                "ARTIFACT_DENIED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        scoped.artifact(
                                                "second",
                                                other.id(),
                                                0,
                                                "",
                                                CancellationToken.NONE))
                        .code());
        Files.writeString(
                root.resolve("artifacts/first").resolve(artifact.id() + ".txt"), "corrupted");
        assertEquals(
                "ARTIFACT_CORRUPT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        service.artifact(
                                                "first",
                                                artifact.id(),
                                                0,
                                                "",
                                                CancellationToken.NONE))
                        .code());
    }

    @Test
    void explicitLineOffsetsAndStalePageAreBounded() throws Exception {
        Files.writeString(work.resolve("lines.txt"), "first\nsecond\nthird\n");
        var page = service.file("lines.txt", 0, 2, "", CancellationToken.NONE);
        assertEquals("second\nthird\n", page.get("text"));
        assertEquals(2L, page.get("startLine"));
        assertEquals(
                "INVALID_RANGE",
                assertThrows(
                                HarnessException.class,
                                () -> service.file("lines.txt", -1, 0, "", CancellationToken.NONE))
                        .code());
        assertEquals(
                "INVALID_RANGE",
                assertThrows(
                                HarnessException.class,
                                () -> service.file("lines.txt", 1, 2, "", CancellationToken.NONE))
                        .code());
        assertEquals(
                "CONTENT_CHANGED",
                assertThrows(
                                HarnessException.class,
                                () -> ContentPages.page("new", 0, "0".repeat(64)))
                        .code());
    }

    @Test
    void policyCancellationAndRootChecksPrecedeInspection() throws Exception {
        Files.writeString(work.resolve("source.txt"), "x");
        var denied =
                new WorkspaceContentInspection(
                        files,
                        store,
                        new SecretRedactor(),
                        (call, context) -> PolicyEngine.Decision.ASK,
                        () -> {});
        assertEquals(
                "INSPECTION_DENIED",
                assertThrows(
                                HarnessException.class,
                                () -> denied.file("source.txt", 0, 0, "", CancellationToken.NONE))
                        .code());
        assertEquals(
                "CANCELLED",
                assertThrows(HarnessException.class, () -> service.list(".", () -> true)).code());
        var replaced =
                new WorkspaceContentInspection(
                        files,
                        store,
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        () -> {
                            throw new HarnessException("WORKSPACE_OWNER_MISMATCH", "replaced");
                        });
        assertEquals(
                "WORKSPACE_OWNER_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () -> replaced.list(".", CancellationToken.NONE))
                        .code());
    }
}
