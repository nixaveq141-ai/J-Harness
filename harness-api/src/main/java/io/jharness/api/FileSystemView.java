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

    /** Text and SHA-256 version from the same UTF-8 content snapshot. */
    default TextSnapshot readSnapshot(ExecPath path) throws IOException {
        String text = readText(path);
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
