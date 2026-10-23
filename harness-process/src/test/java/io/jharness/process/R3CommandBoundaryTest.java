package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R3CommandBoundaryTest {
    @TempDir Path root;

    @Test
    void invalidNativeCharsetHasExplicitDiagnosticWithoutFallback() {
        String previous = System.getProperty("native.encoding");
        try {
            System.setProperty("native.encoding", "not-a-real-charset");
            assertEquals(
                    "INVALID_PROCESS_ENCODING",
                    assertThrows(HarnessException.class, ProcessLimits::defaults).code());
        } finally {
            if (previous == null) System.clearProperty("native.encoding");
            else System.setProperty("native.encoding", previous);
        }
    }

    @Test
    void semanticCommandErrorsCannotReachRunner() throws Exception {
        var entered = new AtomicInteger();
        ProcessRunner runner =
                (argv, cwd, token) -> {
                    entered.incrementAndGet();
                    throw new AssertionError();
                };
        var scope = new PluginScope("s", "a");
        var tool = new ProcessToolsPlugin.CommandTool(runner, scope, null);
        for (var arguments :
                List.of(
                        Map.<String, Object>of("mode", "SH", "argv", List.of("echo", "extra")),
                        Map.<String, Object>of("argv", List.of("bad\u0000argument")),
                        Map.<String, Object>of("argv", List.of("x".repeat(32769))))) {
            var call =
                    new ToolInvocation(
                            "c", "execute_command", arguments, SideEffectLevel.SYSTEM_IMPACT);
            assertEquals(
                    "SCHEMA_MISMATCH",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            tool.execute(
                                                    call,
                                                    new ToolContext(scope, CancellationToken.NONE)))
                            .code());
        }
        assertEquals(0, entered.get());
    }

    @Test
    void previewRejectsWrongVersionTypeAndUtf8OversizeWithoutMutation() throws Exception {
        Files.writeString(root.resolve("file"), "original");
        var preview =
                new LocalPlanPreviewer(new LocalWorkspace(root.toRealPath()), new SecretRedactor());
        var badVersion =
                new ToolInvocation(
                        "c",
                        "write_file",
                        Map.of(
                                "path",
                                "file",
                                "content",
                                "new",
                                "overwrite",
                                true,
                                "expectedVersion",
                                1),
                        SideEffectLevel.WORKSPACE_WRITE);
        assertEquals(
                "SCHEMA_MISMATCH",
                assertThrows(
                                HarnessException.class,
                                () -> preview.preview(badVersion, CancellationToken.NONE))
                        .code());
        var huge =
                new ToolInvocation(
                        "c",
                        "write_file",
                        Map.of("path", "file", "content", "中".repeat(400000), "overwrite", true),
                        SideEffectLevel.WORKSPACE_WRITE);
        assertEquals(
                "FILE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> preview.preview(huge, CancellationToken.NONE))
                        .code());
        assertEquals("original", Files.readString(root.resolve("file")));
    }
}
