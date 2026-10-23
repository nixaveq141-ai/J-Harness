package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M1057ExtensionsTest {
    @TempDir Path base;

    @BeforeEach
    void canonicalTemporaryRoot() throws java.io.IOException {
        base = base.toRealPath();
    }

    @Test
    void stdioResourcesAndTemplatesUseRealChildProtocolAndCleanUp() throws Exception {
        Path directory = Files.createDirectory(base.resolve("peer"));
        var plugin =
                new McpPlugin(
                        "stdio",
                        () -> {
                            try {
                                return new StdioMcpTransport(
                                        List.of(
                                                M3RuntimeIntegrationTest.java(),
                                                "-cp",
                                                System.getProperty("java.class.path"),
                                                M7McpServer.class.getName(),
                                                directory.resolve("trace.jsonl").toString(),
                                                directory.resolve("marker.txt").toString(),
                                                "resources"),
                                        directory,
                                        java.time.Duration.ofSeconds(3));
                            } catch (java.io.IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        },
                        Map.of());
        try (var fixture = new M7EcosystemFixture(base.resolve("host"), plugin)) {
            fixture.start();
            for (String uri : List.of("notes://one", "notes://item/guide")) {
                var result =
                        fixture.execute(
                                "mcp_stdio__read_resource",
                                Map.of("uri", uri),
                                ApprovalService.rejectAll());
                assertEquals(ToolStatus.SUCCESS, result.status());
                assertFalse(result.toString().contains("mcp-secret-token"));
            }
            assertEquals(
                    ToolStatus.SUCCESS,
                    fixture.execute(
                                    "mcp_stdio__resource_templates",
                                    Map.of(),
                                    ApprovalService.rejectAll())
                            .status());
        }
        assertFalse(Files.exists(directory.resolve("marker.txt")));
        long pid = Long.parseLong(Files.readString(directory.resolve("pid.txt")));
        long end = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < end) Thread.sleep(10);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    void linkedSkillRootsCannotExposeSiblingFiles() throws Exception {
        Path outside = Files.createDirectory(base.resolve("outside"));
        skill(outside, "secret", "sibling-sentinel");
        Path link = base.resolve("link");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var process =
                    new ProcessBuilder(
                                    "cmd.exe",
                                    "/c",
                                    "mklink",
                                    "/J",
                                    link.toString(),
                                    outside.toString())
                            .redirectErrorStream(true)
                            .start();
            String output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), output);
        } else Files.createSymbolicLink(link, outside);
        try {
            assertThrows(
                    HarnessException.class,
                    () ->
                            SkillCatalog.scan(
                                    List.of(new SkillCatalog.Root("linked", link)),
                                    Set.of(),
                                    CancellationToken.NONE));
        } finally {
            Files.delete(link);
        }
    }

    private static final class Peer implements McpTransport {
        String mode = "normal";
        boolean closed;
        final List<String> calls = new ArrayList<>();

        public String kind() {
            return "fixture";
        }

        public void close() {
            closed = true;
        }

        public Map<String, Object> exchange(Map<String, Object> message, CancellationToken token) {
            token.throwIfCancelled();
            String method = (String) message.get("method");
            calls.add(method);
            if (method.equals("notifications/initialized")) return Map.of();
            Map<String, Object> result =
                    switch (method) {
                        case "initialize" ->
                                Map.of(
                                        "protocolVersion",
                                        McpPlugin.PROTOCOL_VERSION,
                                        "capabilities",
                                        Map.of("resources", Map.of()));
                        case "resources/list" ->
                                mode.equals("duplicate")
                                        ? Map.of(
                                                "resources",
                                                List.of(
                                                        Map.of("name", "one", "uri", "notes://one"),
                                                        Map.of(
                                                                "name",
                                                                "one",
                                                                "uri",
                                                                "notes://one")))
                                        : mode.equals("cursor")
                                                ? Map.of(
                                                        "resources",
                                                        List.of(),
                                                        "nextCursor",
                                                        "repeat")
                                                : Map.of(
                                                        "resources",
                                                        List.of(
                                                                Map.of(
                                                                        "name",
                                                                        "one",
                                                                        "uri",
                                                                        "notes://one")));
                        case "resources/templates/list" ->
                                Map.of(
                                        "resourceTemplates",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        "lookup",
                                                        "uriTemplate",
                                                        "notes://item/{id}"),
                                                Map.of(
                                                        "name",
                                                        "query",
                                                        "uriTemplate",
                                                        "notes://search{?q}")));
                        case "resources/read" -> {
                            if (mode.equals("disconnect"))
                                throw new HarnessException("MCP_CLOSED", "fixture");
                            String uri = (String) Values.object(message.get("params")).get("uri");
                            yield Map.of(
                                    "contents",
                                    List.of(
                                            mode.equals("blob")
                                                    ? Map.of("uri", uri, "blob", "YWJj")
                                                    : Map.of(
                                                            "uri",
                                                            mode.equals("wrong-uri")
                                                                    ? "notes://other"
                                                                    : uri,
                                                            "mimeType",
                                                            "text/plain",
                                                            "text",
                                                            mode.equals("huge")
                                                                    ? "x".repeat(256 * 1024)
                                                                    : "untrusted mcp-secret-token")));
                        }
                        default -> throw new AssertionError(method);
                    };
            return Map.of("jsonrpc", "2.0", "id", message.get("id"), "result", result);
        }
    }

    @Test
    void resourceOnlyPeerListsTemplatesReadsThroughPipelineAndRevokes() throws Exception {
        var peer = new Peer();
        try (var fixture =
                new M7EcosystemFixture(base, new McpPlugin("notes", () -> peer, Map.of()))) {
            fixture.start();
            assertEquals(
                    3,
                    fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).stream()
                            .filter(t -> t.name().startsWith("mcp_notes__"))
                            .count());
            assertFalse(peer.calls.contains("tools/list"));
            for (String operation : List.of("resources", "resource_templates")) {
                var value =
                        fixture.execute(
                                "mcp_notes__" + operation, Map.of(), ApprovalService.rejectAll());
                assertEquals(ToolStatus.SUCCESS, value.status());
                assertEquals("notes", Values.object(value.value().get("source")).get("server"));
            }
            for (String uri : List.of("notes://one", "notes://item/guide")) {
                var value =
                        fixture.execute(
                                "mcp_notes__read_resource",
                                Map.of("uri", uri),
                                ApprovalService.rejectAll());
                assertEquals(ToolStatus.SUCCESS, value.status());
                assertFalse(value.toString().contains("mcp-secret-token"));
            }
            assertFalse(fixture.history().toString().contains("mcp-secret-token"));
            fixture.container.stop();
            assertTrue(peer.closed);
            assertTrue(fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
        }
    }

    @Test
    void unlistedMalformedUriAndUnsupportedTemplateNeverReachPeer() throws Exception {
        var peer = new Peer();
        try (var fixture =
                new M7EcosystemFixture(base, new McpPlugin("notes", () -> peer, Map.of()))) {
            fixture.start();
            for (String uri :
                    List.of(
                            "notes://missing",
                            "../outside",
                            "https://user:mcp-secret-token@host/path",
                            "https://user:public-password@host/path",
                            "notes://item/a/b",
                            "notes://item/..",
                            "notes://item/%2foutside",
                            "notes://search?q=value")) {
                var result =
                        fixture.execute(
                                "mcp_notes__read_resource",
                                Map.of("uri", uri),
                                ApprovalService.rejectAll());
                assertNotEquals(ToolStatus.SUCCESS, result.status());
                assertFalse(result.toString().contains("mcp-secret-token"));
                if (uri.contains("mcp-secret-token"))
                    assertEquals("SECRET_ARGUMENT", result.error().orElseThrow().code());
                else if (uri.startsWith("https://user:"))
                    assertEquals("MCP_INVALID_RESOURCE_URI", result.error().orElseThrow().code());
            }
            assertFalse(fixture.history().toString().contains("mcp-secret-token"));
            fixture.writer.flush();
            assertFalse(
                    Files.readString(base.resolve("sessions/s.jsonl"))
                            .contains("mcp-secret-token"));
            assertFalse(peer.calls.contains("resources/read"));
            assertEquals(
                    "SCHEMA_MISMATCH",
                    fixture.execute(
                                    "mcp_notes__read_resource",
                                    Map.of("uri", 7),
                                    ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
        }
    }

    @Test
    void resourceLimitsCorruptionDisconnectAndCancellationDoNotSucceed() throws Exception {
        var peer = new Peer();
        try (var fixture =
                new M7EcosystemFixture(base, new McpPlugin("notes", () -> peer, Map.of()))) {
            fixture.start();
            for (String mode : List.of("blob", "wrong-uri", "huge", "disconnect")) {
                peer.mode = mode;
                assertEquals(
                        ToolStatus.FAILED,
                        fixture.execute(
                                        "mcp_notes__read_resource",
                                        Map.of("uri", "notes://one"),
                                        ApprovalService.rejectAll())
                                .status());
            }
            long before = peer.calls.stream().filter("resources/read"::equals).count();
            assertNotEquals(
                    ToolStatus.SUCCESS,
                    fixture.execute(
                                    "mcp_notes__read_resource",
                                    Map.of("uri", "notes://one"),
                                    ApprovalService.rejectAll(),
                                    () -> true,
                                    M7EcosystemFixture.SCOPE)
                            .status());
            assertEquals(before, peer.calls.stream().filter("resources/read"::equals).count());
        }
    }

    @Test
    void duplicateDiscoveryAndCursorLoopsRollbackRegistrations() throws Exception {
        for (String mode : List.of("duplicate", "cursor")) {
            var peer = new Peer();
            peer.mode = mode;
            try (var fixture =
                    new M7EcosystemFixture(
                            base.resolve(mode), new McpPlugin("notes", () -> peer, Map.of()))) {
                assertThrows(HarnessException.class, fixture::start);
                assertTrue(fixture.tools.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
                assertTrue(peer.closed);
            }
        }
    }

    @Test
    void partialMcpRegistrationFailureRevokesEarlierSuccessfulRegistration() throws Exception {
        var real = new io.jharness.plugin.ScopedToolRegistry();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        ToolRegistry registry =
                new ToolRegistry() {
                    public Registration register(AgentTool tool, PluginScope scope) {
                        if (tool.name().startsWith("mcp_notes__")
                                && attempts.incrementAndGet() == 2)
                            throw new HarnessException("REGISTER_FAILED", "fixture");
                        return real.register(tool, scope);
                    }

                    public Optional<AgentTool> resolve(String name, PluginScope scope) {
                        return real.resolve(name, scope);
                    }

                    public List<AgentTool> visibleTools(PluginScope scope) {
                        return real.visibleTools(scope);
                    }
                };
        var peer = new Peer();
        try (var fixture =
                new M7EcosystemFixture(
                        base, new McpPlugin("notes", () -> peer, Map.of()), registry)) {
            assertThrows(HarnessException.class, fixture::start);
            assertEquals(2, attempts.get());
            assertTrue(real.visibleTools(M7EcosystemFixture.SCOPE).isEmpty());
            assertTrue(peer.closed);
        }
    }

    private Path skill(Path root, String folder, String text) throws Exception {
        Path file = root.resolve(folder).resolve("SKILL.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    @Test
    void explicitProjectAndUserRootsFrontmatterAndFrozenLoading() throws Exception {
        Path project = base.resolve("project"), user = base.resolve("user");
        Path file =
                skill(
                        project,
                        "guide",
                        "---\nname: guide\ndescription: 'Project guide mcp-secret-token'\n---\nOriginal mcp-secret-token");
        skill(user, "review", "---\nname: review\ndescription: User guide\n---\nUser body");
        var entries =
                SkillCatalog.scan(
                        List.of(
                                new SkillCatalog.Root("project", project),
                                new SkillCatalog.Root("user", user)),
                        Set.of(),
                        CancellationToken.NONE);
        assertEquals(2, entries.size());
        assertEquals("Project guide mcp-secret-token", entries.getFirst().description());
        assertEquals(64, entries.getFirst().sha256().length());
        Files.writeString(file, "Changed after scan");
        try (var fixture =
                new M7EcosystemFixture(base.resolve("runtime"), SkillCatalog.plugin(entries))) {
            fixture.start();
            assertFalse(
                    fixture.tools
                            .require("skill_guide__load", M7EcosystemFixture.SCOPE)
                            .description()
                            .contains("mcp-secret-token"));
            var result =
                    fixture.execute("skill_guide__load", Map.of(), ApprovalService.rejectAll());
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertTrue(result.value().get("text").toString().contains("Original"));
            assertFalse(result.toString().contains("mcp-secret-token"));
            assertEquals(
                    "UNKNOWN_TOOL",
                    fixture.execute(
                                    "skill_guide__run_anything",
                                    Map.of(),
                                    M7EcosystemFixture.approve())
                            .error()
                            .orElseThrow()
                            .code());
        }
    }

    @Test
    void duplicateNamesConfiguredCollisionsCommandsAndMalformedFrontmatterAreRejected()
            throws Exception {
        Path root = base.resolve("skills");
        Path file = skill(root, "one", "---\nname: shared\ndescription: text\n---\nbody");
        var roots = List.of(new SkillCatalog.Root("project", root));
        assertEquals(
                "SKILL_NAME_COLLISION",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        SkillCatalog.scan(
                                                roots, Set.of("shared"), CancellationToken.NONE))
                        .code());
        Path two = skill(root, "two", Files.readString(file));
        assertEquals(
                "SKILL_NAME_COLLISION",
                assertThrows(
                                HarnessException.class,
                                () -> SkillCatalog.scan(roots, Set.of(), CancellationToken.NONE))
                        .code());
        Files.delete(two);
        for (String text :
                List.of(
                        "---\nname: help\n---\nbody",
                        "---\nname: bad/name\n---\nbody",
                        "---\nname: one\nname: two\n---\nbody",
                        "---\nname: missing-end")) {
            Files.writeString(file, text);
            assertThrows(
                    HarnessException.class,
                    () -> SkillCatalog.scan(roots, Set.of(), CancellationToken.NONE));
        }
    }

    @Test
    void rootTraversalMetadataLimitsAndCancelledScansAreRejected() throws Exception {
        Path root = base.resolve("skills");
        Path file = skill(root, "one", "x");
        var roots = List.of(new SkillCatalog.Root("project", root));
        assertThrows(
                HarnessException.class,
                () ->
                        SkillCatalog.scan(
                                List.of(new SkillCatalog.Root("escape", root.resolve(".."))),
                                Set.of(),
                                CancellationToken.NONE));
        Path metadata = base.resolve(".jharness");
        Files.createDirectories(metadata);
        assertThrows(
                HarnessException.class,
                () ->
                        SkillCatalog.scan(
                                List.of(new SkillCatalog.Root("metadata", metadata)),
                                Set.of(),
                                CancellationToken.NONE));
        assertThrows(HarnessException.class, () -> SkillCatalog.scan(roots, Set.of(), () -> true));
        Files.writeString(file, "x".repeat(65537));
        assertEquals(
                "SKILL_FILE_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> SkillCatalog.scan(roots, Set.of(), CancellationToken.NONE))
                        .code());
        Files.writeString(file, "x");
        for (int i = 0; i < 65; i++) Files.createDirectory(root.resolve("dir" + i));
        assertEquals(
                "SKILL_SCAN_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> SkillCatalog.scan(roots, Set.of(), CancellationToken.NONE))
                        .code());
    }
}
