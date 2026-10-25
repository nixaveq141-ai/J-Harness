package io.jharness.api;

import java.io.IOException;

/**
 * Paths are confined to the selected environment root; traversal, links and protected metadata must
 * be rejected by providers. Versions are nonempty identities, MISSING for absent files; unreadable
 * files raise an error. Null guards only use an existing observation.
 */
public interface FileSystemView {
    ExecPath root();

    String readText(ExecPath path) throws IOException;

    /** Bounded full text; production providers should enforce the limit during reading. */
    default String readText(ExecPath path, int maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes > 1024 * 1024) throw new IllegalArgumentException("maxBytes");
        String text = readText(path);
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new HarnessException("FILE_LIMIT", "Bounded text limit exceeded");
        return text;
    }

    /**
     * Text and version from one content snapshot. This compatibility default requires SHA-256
     * identities of UTF-8 content. Providers using opaque versions must override it with their own
     * atomic snapshot operation; a version from a separate unrelated read is insufficient.
     */
    default TextSnapshot readSnapshot(ExecPath path) throws IOException {
        return checkedSnapshot(path, readText(path));
    }

    /** Same snapshot contract with a caller supplied bounded text limit. */
    default TextSnapshot readSnapshot(ExecPath path, int maxBytes) throws IOException {
        return checkedSnapshot(path, readText(path, maxBytes));
    }

    private TextSnapshot checkedSnapshot(ExecPath path, String text) throws IOException {
        try {
            String version =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    text.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
            if (!version.equals(versionOf(path)))
                throw new HarnessException("FILE_CONFLICT", "Snapshot changed during read");
            return new TextSnapshot(text, version);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record TextSnapshot(String text, String version) {}

    /** Requires a non-null guard object. A null expectedVersion requests an observed version. */
    FileChange writeText(ExecPath path, String content, VersionGuard guard) throws IOException;

    String versionOf(ExecPath path) throws IOException;

    record VersionGuard(String expectedVersion) {}

    record FileChange(
            ExecPath path,
            String beforeVersion,
            String afterVersion,
            String diff,
            java.util.Map<String, Object> metadata) {
        public FileChange {
            metadata = Values.snapshot(metadata);
        }

        public FileChange(ExecPath path, String beforeVersion, String afterVersion, String diff) {
            this(path, beforeVersion, afterVersion, diff, java.util.Map.of());
        }
    }
}
