package io.jharness.testkit;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Owned temporary Maven fixture; deletion never follows symbolic links. */
public final class TemporaryProject implements AutoCloseable {
    private final Path root;

    public TemporaryProject() throws IOException {
        root = Files.createTempDirectory("jharness-测试 project-").toRealPath();
        Files.writeString(
                root.resolve("pom.xml"),
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>calculator</artifactId><version>1</version>
                  <properties><maven.compiler.release>21</maven.compiler.release></properties>
                </project>
                """);
        Path source = root.resolve("src/main/java/fixture/Calculator.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                "package fixture; public class Calculator { public int add(int a, int b) { return a + b; } }\n");
    }

    public Path root() {
        return root;
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (!file.toAbsolutePath().normalize().startsWith(root))
                            throw new IOException("Fixture cleanup boundary");
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exception)
                            throws IOException {
                        if (exception != null) throw exception;
                        if (!dir.toAbsolutePath().normalize().startsWith(root))
                            throw new IOException("Fixture cleanup boundary");
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
