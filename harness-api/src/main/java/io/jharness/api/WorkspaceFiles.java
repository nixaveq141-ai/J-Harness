package io.jharness.api;

import java.io.IOException;
import java.util.*;

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
