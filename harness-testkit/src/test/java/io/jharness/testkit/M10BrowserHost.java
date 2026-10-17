package io.jharness.testkit;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual Java HTTP/SDK/filesystem host for keyless M10 browser tests; not shipped. */
public final class M10BrowserHost implements AutoCloseable {
    static final String TOKEN = "m10_browser_fixture_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final HarnessWebServer server;
    final AtomicInteger opened = new AtomicInteger();
    final List<FakeLlm> models = new CopyOnWriteArrayList<>();
    final Path root;

    M10BrowserHost(Path base) throws Exception {
        root = base.toRealPath();
        var profiles = new LinkedHashMap<String, HarnessWebServer.Profile>();
        for (String name :
                List.of(
                        "write", "read", "diff", "cancel", "unknown", "html", "large", "shell",
                        "timeout")) {
            Path workspace = Files.createDirectories(root.resolve(name)).toRealPath();
            Files.writeString(workspace.resolve("input.txt"), "hello from a real workspace\n");
            if (name.equals("diff")) {
                git(workspace, "init");
                git(workspace, "add", "input.txt");
                git(
                        workspace,
                        "-c",
                        "user.name=Fixture",
                        "-c",
                        "user.email=fixture@example.invalid",
                        "-c",
                        "commit.gpgsign=false",
                        "commit",
                        "-qm",
                        "baseline");
                Files.writeString(workspace.resolve("input.txt"), "a real recorded diff\n");
            }
            var sessions = new JsonlSessionRepository(root.resolve(name + "-sessions"));
            profiles.put(
                    name,
                    new HarnessWebServer.Profile(
                            name,
                            sessions,
                            (id, approvals, sanitizer) -> {
                                opened.incrementAndGet();
                                if (name.equals("unknown"))
                                    throw new IllegalStateException("Uncertain fixture open");
                                var calls =
                                        name.equals("shell") || name.equals("timeout")
                                                ? List.of(
                                                        new ToolInvocation(
                                                                "browser-command",
                                                                "execute_command",
                                                                Map.of(
                                                                        "argv",
                                                                        name.equals("shell")
                                                                                ? List.of(
                                                                                        "cmd.exe",
                                                                                        "/c",
                                                                                        "echo fixture")
                                                                                : List.of(
                                                                                        Path.of(
                                                                                                        System
                                                                                                                .getProperty(
                                                                                                                        "java.home"),
                                                                                                        "bin",
                                                                                                        System
                                                                                                                        .getProperty(
                                                                                                                                "os.name")
                                                                                                                        .startsWith(
                                                                                                                                "Windows")
                                                                                                                ? "java.exe"
                                                                                                                : "java")
                                                                                                .toString(),
                                                                                        "-cp",
                                                                                        System
                                                                                                .getProperty(
                                                                                                        "java.class.path"),
                                                                                        M105ProcessFixture
                                                                                                .class
                                                                                                .getName()),
                                                                        "mode",
                                                                        "ARGV",
                                                                        "timeoutMs",
                                                                        200),
                                                                SideEffectLevel.SYSTEM_IMPACT))
                                                : name.equals("write")
                                                        ? List.of(
                                                                new ToolInvocation(
                                                                        "browser-write",
                                                                        "write_file",
                                                                        Map.of(
                                                                                "path",
                                                                                "browser-note.txt",
                                                                                "content",
                                                                                "来自浏览器的修改"),
                                                                        SideEffectLevel
                                                                                .WORKSPACE_WRITE))
                                                        : name.equals("diff")
                                                                ? List.of(
                                                                        new ToolInvocation(
                                                                                "browser-diff",
                                                                                "git_diff",
                                                                                Map.of(),
                                                                                SideEffectLevel
                                                                                        .READ_ONLY))
                                                                : List.of(
                                                                        new ToolInvocation(
                                                                                "browser-read",
                                                                                "read_file",
                                                                                Map.of(
                                                                                        "path",
                                                                                        "input.txt"),
                                                                                SideEffectLevel
                                                                                        .READ_ONLY));
                                String content =
                                        name.equals("html")
                                                ? "<img src=x onerror=\"window.injected=true\">"
                                                : name.equals("large")
                                                        ? "L".repeat(70000)
                                                        : "最终结果：本机测试模型，无付费请求。";
                                var fake =
                                        new FakeLlm(
                                                List.of(
                                                        new ChatResponse(content, calls, Map.of()),
                                                        new ChatResponse(
                                                                "最终结果已记录", List.of(), Map.of())),
                                                200000);
                                models.add(fake);
                                LlmClient model =
                                        name.equals("cancel")
                                                ? new LlmClient() {
                                                    @Override
                                                    public ModelCapabilities capabilities() {
                                                        return fake.capabilities();
                                                    }

                                                    @Override
                                                    public PreparedRequest prepare(
                                                            ChatRequest request) {
                                                        return fake.prepare(request);
                                                    }

                                                    @Override
                                                    public ChatResponse complete(
                                                            ChatRequest request,
                                                            CancellationToken cancellation) {
                                                        while (!cancellation.isCancelled()) {
                                                            try {
                                                                Thread.sleep(30);
                                                            } catch (InterruptedException ex) {
                                                                Thread.currentThread().interrupt();
                                                            }
                                                        }
                                                        cancellation.throwIfCancelled();
                                                        return fake.complete(request, cancellation);
                                                    }

                                                    @Override
                                                    public StreamHandle stream(
                                                            ChatRequest request,
                                                            CancellationToken cancellation) {
                                                        return fake.stream(request, cancellation);
                                                    }
                                                }
                                                : fake;
                                return new HarnessSdk(
                                        id,
                                        name,
                                        sessions,
                                        new LocalExecutionEnvironment(
                                                "local", workspace, List.of()),
                                        model,
                                        new LocalArtifactStore(
                                                root.resolve(name + "-artifacts"), sanitizer),
                                        sanitizer,
                                        new DefaultPolicyEngine(),
                                        approvals,
                                        new Budget(5, Duration.ofSeconds(45)),
                                        (reply, projection) ->
                                                new CompletionEvaluator.CompletionDecision(
                                                        !projection.toolHistory().isEmpty(),
                                                        "recorded tool outcome"),
                                        ApiCostGuard.unavailable(),
                                        List.of());
                            }));
        }
        server =
                new HarnessWebServer(
                        0,
                        TOKEN,
                        profiles,
                        new JsonlSessionRepository(root.resolve("catalog")),
                        "browser-catalog",
                        new SecretRedactor(List.of("fixture-secret-do-not-display")),
                        4,
                        32,
                        Duration.ofSeconds(8));
    }

    private static void git(Path directory, String... args) throws Exception {
        var command = new ArrayList<String>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output =
                new String(
                        process.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0)
            throw new IllegalStateException("Fixture Git failed: " + output);
    }

    @Override
    public void close() {
        server.close();
    }

    public static void main(String[] args) throws Exception {
        try (var host = new M10BrowserHost(Files.createDirectories(Path.of(args[0])))) {
            Files.writeString(Path.of(args[1]), host.server.address().toString());
            System.out.println("M10 keyless browser host ready");
            while (System.in.read() != -1) {
                /* owned by the test runner */
            }
            var evidence =
                    Map.of(
                            "opened",
                            host.opened.get(),
                            "modelRequests",
                            host.models.stream().mapToInt(m -> m.requests().size()).sum(),
                            "paidRequests",
                            0,
                            "writeExists",
                            Files.exists(host.root.resolve("write/browser-note.txt")));
            Files.writeString(
                    Path.of(args[2]),
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence));
        }
    }
}
