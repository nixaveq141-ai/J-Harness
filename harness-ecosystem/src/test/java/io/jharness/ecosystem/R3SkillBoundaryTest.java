package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R3SkillBoundaryTest {
    @TempDir Path root;

    @Test
    void frontmatterSingleQuoteIsControlledFailureAndPairedQuotesWork() throws Exception {
        root = root.toRealPath();
        var skill = Files.createDirectory(root.resolve("demo"));
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: '\n---\nbody\n");
        assertEquals(
                "SKILL_FRONTMATTER_INVALID",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SkillCatalog.scan(
                                                List.of(new SkillCatalog.Root("project", root)),
                                                Set.of(),
                                                CancellationToken.NONE))
                        .code());
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: 'paired'\n---\nbody\n");
        assertEquals(
                "paired",
                SkillCatalog.scan(
                                List.of(new SkillCatalog.Root("project", root)),
                                Set.of(),
                                CancellationToken.NONE)
                        .getFirst()
                        .id());
    }

    @Test
    void boundedTextRejectsBeforeUnboundedLoadAndRemoteRepliesValidate() throws Exception {
        Files.writeString(root.resolve("large"), "x".repeat(65537));
        var files = new LocalWorkspace(root.toRealPath());
        assertEquals(
                "FILE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> files.readText(new ExecPath("large"), 65536))
                        .code());
        var remote =
                new RemoteWorkspace(
                        (request, token) -> Map.of("value", Map.of("text", "missing version")));
        assertEquals(
                "WORKER_PROTOCOL_ERROR",
                assertThrows(HarnessException.class, () -> remote.readText(new ExecPath("file")))
                        .code());
        assertEquals(
                "WORKER_INVALID_REQUEST",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        EnvironmentWorker.dispatch(
                                                files,
                                                Map.of(
                                                        "op",
                                                        "page",
                                                        "start",
                                                        new java.math.BigDecimal(
                                                                "1.00000000000000001"))))
                        .code());
    }
}
