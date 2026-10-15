package io.jharness.cli;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

final class Metadata {
    private final Path root;

    Metadata(Path workspace) {
        root = workspace.resolve(".jharness");
    }

    Path directory(String name) throws IOException {
        check(root);
        Files.createDirectories(root);
        check(root);
        Path path = root.resolve(name);
        check(path);
        Files.createDirectories(path);
        check(path);
        return path;
    }

    Path config() throws IOException {
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
