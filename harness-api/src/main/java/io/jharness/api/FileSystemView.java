package io.jharness.api;

import java.io.IOException;

public interface FileSystemView {
    ExecPath root();

    String readText(ExecPath path) throws IOException;

    FileChange writeText(ExecPath path, String content, VersionGuard guard) throws IOException;

    String versionOf(ExecPath path) throws IOException;

    record VersionGuard(String expectedVersion) {}

    record FileChange(ExecPath path, String beforeVersion, String afterVersion, String diff) {}
}
