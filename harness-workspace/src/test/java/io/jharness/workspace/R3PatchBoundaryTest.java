package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import org.junit.jupiter.api.Test;

class R3PatchBoundaryTest {
    @Test
    void hunkCountsDisambiguateHeaderLikeContentAndRealADirectory() {
        var patches = UnifiedPatch.parse("--- a/foo\n+++ a/foo\n@@ -1 +1 @@\n--- old\n+++ new\n");
        assertEquals("a/foo", patches.getFirst().path());
        assertEquals("++ new\n", UnifiedPatch.apply(patches.getFirst(), "-- old\n"));
    }

    @Test
    void gitCreateSupportsOnlyRegularNonExecutableMetadata() {
        var patch =
                UnifiedPatch.parse(
                                "diff --git a/foo b/foo\nnew file mode 100644\n--- /dev/null\n+++ b/foo\n@@ -0,0 +1 @@\n+new\n")
                        .getFirst();
        assertTrue(patch.create());
        assertEquals("foo", patch.path());
        assertEquals("new\n", UnifiedPatch.apply(patch, ""));
        assertThrows(
                HarnessException.class,
                () ->
                        UnifiedPatch.parse(
                                "diff --git a/foo b/foo\nnew file mode 100755\n--- /dev/null\n+++ b/foo\n@@ -0,0 +1 @@\n+new\n"));
    }

    @Test
    void malformedCountsDeletionAndNoNewlineAreExplicitlyRejected() {
        assertThrows(
                HarnessException.class,
                () -> UnifiedPatch.parse("--- f\n+++ f\n@@ -1,2 +1 @@\n-old\n+new\n"));
        assertThrows(
                HarnessException.class,
                () -> UnifiedPatch.parse("--- f\n+++ /dev/null\n@@ -1 +0,0 @@\n-old\n"));
        var patch = UnifiedPatch.parse("--- f\n+++ f\n@@ -1 +1 @@\n-old\n+new\n").getFirst();
        assertEquals(
                "UNSUPPORTED_PATCH",
                assertThrows(HarnessException.class, () -> UnifiedPatch.apply(patch, "old"))
                        .code());
    }
}
