package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R3ExtensionProtocolTest {
    @TempDir Path root;

    @Test
    void sdkUsesInjectedPolicyAndApprovalExactlyOnce() throws Exception {
        Path workspace = Files.createDirectories(root.resolve("workspace")).toRealPath();
        var approved = new AtomicInteger();
        PolicyEngine policy = (call, context) -> PolicyEngine.Decision.ASK;
        ApprovalService approval =
                (call, context) -> {
                    approved.incrementAndGet();
                    return new ApprovalService.ApprovalDecision(
                            true, "explicit fixture approval", "operator");
                };
        HarnessPlugin inspect =
                new HarnessPlugin() {
                    public String id() {
                        return "injection-inspect";
                    }

                    public String version() {
                        return "1.0";
                    }

                    public void start(PluginContext context) {
                        assertSame(
                                policy,
                                context.services()
                                        .requireUnique(PolicyEngine.class, context.scope()));
                        assertSame(
                                approval,
                                context.services()
                                        .requireUnique(ApprovalService.class, context.scope()));
                    }

                    public void stop() {}
                };
        var clean = new SecretRedactor();
        var model =
                new FakeLlm(
                        List.of(
                                new ChatResponse(
                                        "",
                                        List.of(
                                                new ToolInvocation(
                                                        "write",
                                                        "write_file",
                                                        Map.of(
                                                                "path",
                                                                "created",
                                                                "content",
                                                                "approved"),
                                                        SideEffectLevel.WORKSPACE_WRITE)),
                                        Map.of()),
                                new ChatResponse("done", List.of(), Map.of())));
        try (var sdk =
                new HarnessSdk(
                        "injected",
                        "workspace",
                        new io.jharness.session.JsonlSessionRepository(root.resolve("sessions")),
                        new LocalExecutionEnvironment("local", workspace, List.of()),
                        model,
                        new io.jharness.session.LocalArtifactStore(
                                root.resolve("artifacts"), clean),
                        clean,
                        policy,
                        approval,
                        new Budget(3, Duration.ofSeconds(10)),
                        (response, projection) ->
                                new CompletionEvaluator.CompletionDecision(
                                        Files.exists(workspace.resolve("created")), "actual file"),
                        ApiCostGuard.unavailable(),
                        List.of(inspect))) {
            assertEquals(
                    TaskStatus.COMPLETED,
                    sdk.run("create file after exact approval", CancellationToken.NONE).status());
            assertEquals(1, approved.get());
            assertEquals("approved", Files.readString(workspace.resolve("created")));
        }
    }

    @Test
    void terminalSemanticFailureHasNoApprovalIntentOrBody() throws Exception {
        var approvals = new AtomicInteger();
        HarnessPlugin empty =
                new HarnessPlugin() {
                    public String id() {
                        return "fixture-extra";
                    }

                    public String version() {
                        return "1.0";
                    }

                    public void start(PluginContext context) {}

                    public void stop() {}
                };
        try (var fixture = new M7EcosystemFixture(root, empty)) {
            fixture.start();
            var result =
                    fixture.execute(
                            "terminal_start",
                            Map.of("mode", "SH", "argv", List.of("echo", "extra")),
                            (call, context) -> {
                                approvals.incrementAndGet();
                                return new ApprovalService.ApprovalDecision(
                                        true, "fixture", "operator");
                            });
            assertEquals(ToolStatus.FAILED, result.status());
            assertEquals("SCHEMA_MISMATCH", result.error().orElseThrow().code());
            assertEquals(0, approvals.get());
            assertTrue(
                    fixture.history().stream()
                            .noneMatch(
                                    e -> Set.of("tool.intent", "tool.started").contains(e.type())));
            var completed =
                    fixture.history().stream()
                            .filter(e -> e.type().equals("tool.completed"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(false, completed.payload().get("bodyEntered"));
        }
    }

    @Test
    void executionThenRpcErrorIsUnknownAndNeverRetried() throws Exception {
        var calls = new AtomicInteger();
        Path marker = root.resolve("executed");
        McpTransport transport =
                new McpTransport() {
                    public String kind() {
                        return "fixture";
                    }

                    public void close() {}

                    public Map<String, Object> exchange(
                            Map<String, Object> request, CancellationToken token) {
                        String method = (String) request.get("method");
                        if (!request.containsKey("id")) return Map.of();
                        Object id = request.get("id");
                        if (method.equals("initialize"))
                            return Map.of(
                                    "jsonrpc",
                                    "2.0",
                                    "id",
                                    id,
                                    "result",
                                    Map.of(
                                            "protocolVersion",
                                            McpPlugin.PROTOCOL_VERSION,
                                            "capabilities",
                                            Map.of("tools", Map.of())));
                        if (method.equals("tools/list"))
                            return Map.of(
                                    "jsonrpc",
                                    "2.0",
                                    "id",
                                    id,
                                    "result",
                                    Map.of(
                                            "tools",
                                            List.of(
                                                    Map.of(
                                                            "name",
                                                            "write",
                                                            "inputSchema",
                                                            Map.of("type", "object")))));
                        calls.incrementAndGet();
                        try {
                            Files.writeString(marker, "actual effect");
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                        return Map.of(
                                "jsonrpc",
                                "2.0",
                                "id",
                                id,
                                "error",
                                Map.of("code", -32603, "message", "after execution"));
                    }
                };
        try (var fixture =
                new M7EcosystemFixture(root, new McpPlugin("peer", () -> transport, Map.of()))) {
            fixture.start();
            var result = fixture.execute("mcp_peer__write", Map.of(), M7EcosystemFixture.approve());
            assertEquals(ToolStatus.UNKNOWN, result.status());
            assertEquals("actual effect", Files.readString(marker));
            assertEquals(1, calls.get());
            assertTrue(fixture.history().stream().anyMatch(e -> e.type().equals("tool.intent")));
            assertEquals(
                    ToolStatus.UNKNOWN,
                    fixture.execute("mcp_peer__write", Map.of(), M7EcosystemFixture.approve())
                            .status());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void invalidSkillScriptsFailDuringLoadingWithoutMarker() throws Exception {
        for (String text : List.of(" ", "x".repeat(32769))) {
            Path base = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
            Path skill = Files.createDirectories(base.resolve("workspace/skills/demo"));
            Files.writeString(skill.resolve("SKILL.md"), "untrusted instructions");
            Files.writeString(skill.resolve("run.sh"), text);
            var plugin =
                    new SkillsPlugin(
                            new SkillsPlugin.Spec(
                                    "demo",
                                    new ExecPath("skills/demo"),
                                    Map.of(),
                                    Map.of(
                                            "run",
                                            new SkillsPlugin.Script(
                                                    new ExecPath("run.sh"),
                                                    CommandRequest.Mode.SH,
                                                    Duration.ofSeconds(1)))));
            try (var fixture = new M7EcosystemFixture(base, plugin)) {
                assertEquals(
                        "INVALID_SKILL_SCRIPT",
                        assertThrows(HarnessException.class, fixture::start).code());
                // Loading-time validation: these malformed script bodies do not write a marker.
                assertTrue(fixture.history().isEmpty());
            }
        }
    }
}
