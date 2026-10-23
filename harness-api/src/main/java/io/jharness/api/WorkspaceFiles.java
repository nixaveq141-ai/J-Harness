package io.jharness.api;

import java.io.IOException;
import java.util.*;

/**
 * Bounded environment operations. Local and remote providers share these result fields: list:
 * entries (path/type), truncated; search: paths, truncated; grep: matches, truncated; readPage:
 * path/text/version/startLine/nextLine/truncated. Page lines are one-based (max 2000), list depth
 * 1..16 and list/search limit 1..5000; grep limit/paths max 1000. Existing-file overwrite still
 * requires an explicit matching version or a current observation; null versions use only the
 * provider's isolated observation cache, never unconditional overwrite. Diff scope is exactly
 * working or staged.
 */
public interface WorkspaceFiles extends FileSystemView {
    /**
     * Separate observation/version cache over the same workspace; no file copying or new rights.
     */
    default WorkspaceFiles isolatedView() throws IOException {
        throw new HarnessException(
                "UNSUPPORTED_WORKSPACE_VIEW", "Independent workspace views unavailable");
    }

    default Map<String, Object> applyPatch(
            String patchText, Map<String, String> expectedVersions, CancellationToken cancellation)
            throws IOException {
        throw new HarnessException("UNSUPPORTED", "Patch provider unavailable");
    }

    Map<String, Object> list(ExecPath path, int depth, int limit, CancellationToken cancellation)
            throws IOException;

    Map<String, Object> readPage(
            ExecPath path, int startLine, int endLine, CancellationToken cancellation)
            throws IOException;

    Map<String, Object> search(String glob, int limit, CancellationToken cancellation)
            throws IOException;

    Map<String, Object> grep(
            String literal, List<ExecPath> paths, int limit, CancellationToken cancellation)
            throws IOException;

    FileChange write(
            ExecPath path,
            String content,
            boolean overwrite,
            String expectedVersion,
            CancellationToken cancellation)
            throws IOException;

    FileChange edit(
            ExecPath path,
            String oldText,
            String newText,
            int expectedOccurrences,
            String expectedVersion,
            CancellationToken cancellation)
            throws IOException;

    String gitDiff(String scope, CancellationToken cancellation) throws IOException;
}
