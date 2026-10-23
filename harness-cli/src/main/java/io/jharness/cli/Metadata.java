package io.jharness.cli;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

final class Metadata {
    private final Path root;
    private Object workspaceKey;
    private boolean bound;
    private DirectoryIdentity metadataIdentity;

    private synchronized void verifyWorkspace() throws IOException {
        check(root.getParent());
        if (metadataIdentity != null) {
            try {
                if (!metadataIdentity.equals(DirectoryIdentity.read(root)))
                    throw new IOException("Metadata identity changed");
            } catch (IOException ex) {
                throw new HarnessException(
                        "METADATA_PATH_DENIED", "Metadata root identity changed", ex);
            }
        }
        Object current = DirectoryIdentity.read(root.getParent());
        if (bound && !java.util.Objects.equals(workspaceKey, current))
            throw new HarnessException(
                    "METADATA_PATH_DENIED", "Workspace directory identity changed");
        workspaceKey = current;
        bound = true;
    }

    Metadata(Path workspace) {
        root = workspace.resolve(".jharness");
    }

    Path directory(String name) throws IOException {
        if (name == null || !name.isEmpty() && !name.matches("[A-Za-z0-9_-]{1,64}"))
            throw new HarnessException("METADATA_PATH_DENIED", "Expected one metadata segment");
        verifyWorkspace();
        check(root);
        Files.createDirectories(root);
        check(root);
        metadataIdentity = DirectoryIdentity.bind(root);
        Path path = root.resolve(name);
        check(path);
        Files.createDirectories(path);
        check(path);
        verifyWorkspace();
        return path;
    }

    Path config() throws IOException {
        verifyWorkspace();
        check(root);
        return root.resolve("config.json");
    }

    Path workspace() {
        return root.getParent();
    }

    private static void check(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        var attrs =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isDirectory()
                || attrs.isSymbolicLink()
                || attrs.isOther()
                || !path.toRealPath().equals(path.toAbsolutePath().normalize()))
            throw new HarnessException(
                    "METADATA_PATH_DENIED",
                    "Metadata directory cannot be link/reparse/regular file");
    }
}
