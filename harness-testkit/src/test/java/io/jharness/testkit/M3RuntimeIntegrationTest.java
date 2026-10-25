package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.plugin.*;
import io.jharness.policy.*;
import io.jharness.process.*;
import io.jharness.session.*;
import io.jharness.tools.*;
import io.jharness.workspace.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M3RuntimeIntegrationTest {
    @TempDir Path temporary;

    @BeforeEach
    void canonicalTemporaryRoot() throws IOException {
        temporary = temporary.toRealPath();
    }

    private static final PluginScope SCOPE = new PluginScope("s", "agent");
    private static final ApprovalService APPROVE =
            (call, context) ->
                    new ApprovalService.ApprovalDecision(
                            true, "Explicit fixture authorization", "user");

    @Test
    void actualCommandIntentForceFailureDoesNotStartProcess() throws Exception {
        try (var f = new Fixture()) {
            Path store = temporary.resolve("force-fault");
            var repo =
                    new JsonlSessionRepository(
                            store,
                            JsonlSessionRepository.TailPolicy.REJECT,
                            channel -> {
                                if (Files.readAllLines(store.resolve("s.jsonl"))
                                        .getLast()
                                        .contains("\"type\":\"tool.intent\""))
                                    throw new IOException("injected force failure");
                                channel.force(true);
                            },
                            code -> {});
            try (var writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
                var call = command("must-not-launch", fixture("block"), "ARGV");
                assertThrows(
                        HarnessException.class,
                        () ->
                                f.pipeline.execute(
                                        call,
                                        new ToolPipelineContext(
                                                SCOPE,
                                                "t",
                                                "step",
                                                f.tools,
                                                writer,
                                                f.bus,
                                                new DefaultPolicyEngine(),
                                                APPROVE,
                                                new DefaultCheckpointPolicy(),
                                                CancellationToken.NONE)));
                assertFalse(Files.exists(f.root.resolve("started")));
                assertTrue(
                        repo.openReader("s").events().stream()
                                .noneMatch(e -> e.type().equals("tool.started")));
            }
        }
    }

    @Test
    void cancellationDuringApprovalRecordsRevocationAndNeverCreatesIntent() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean();
        try (var f = new Fixture();
                var approvals = new InteractiveApprovalService(Duration.ofSeconds(3));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future =
                    executor.submit(
                            () ->
                                    f.execute(
                                            command("waiting", fixture("block"), "ARGV"),
                                            new DefaultPolicyEngine(),
                                            approvals,
                                            cancel::get));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (approvals.prompts().isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
            assertFalse(approvals.prompts().isEmpty());
            cancel.set(true);
            assertEquals(ToolStatus.CANCELLED, future.get(2, TimeUnit.SECONDS).status());
            assertFalse(Files.exists(f.root.resolve("started")));
            assertTrue(
                    f.events().stream()
                            .anyMatch(
                                    e ->
                                            e.type().equals("approval.decided")
                                                    && "CANCELLED"
                                                            .equals(e.payload().get("reason"))));
            assertTrue(f.events().stream().noneMatch(e -> e.type().equals("tool.intent")));
        } finally {
            cancel.set(true);
        }
    }

    @Test
    void timedOutCommandRequiresVerificationBeforeAnotherModelRequestOrTurn() throws Exception {
        try (var f = new Fixture()) {
            var call =
                    new ToolInvocation(
                            "timed",
                            "execute_command",
                            Map.of("argv", fixture("block"), "timeoutMs", 750),
                            SideEffectLevel.SYSTEM_IMPACT);
            FakeLlm llm = new FakeLlm(List.of(reply(call), reply()));
            f.loop(llm, new Budget(5, Duration.ofSeconds(10)), ApiCostGuard.unavailable(), APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals("TIMEOUT", f.events().getLast().payload().get("reason"));
            var completion =
                    f.events().stream()
                            .filter(
                                    event ->
                                            event.type().equals("tool.completed")
                                                    && "timed".equals(event.callId()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("TIMEOUT", completion.payload().get("status"));
            assertEquals(
                    Boolean.TRUE,
                    Values.object(completion.payload().get("value")).get("sideEffectUncertain"));
            assertEquals(1, llm.requests().size());
            f.loop(llm, new Budget(5, Duration.ofSeconds(10)), ApiCostGuard.unavailable(), APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals(1, llm.requests().size());
            assertEquals("UNKNOWN_SIDE_EFFECT", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void refusedDeletionAndResetNeverEnterBodyAndFullApprovalDecisionPersists() throws Exception {
        try (var f = new Fixture()) {
            Files.writeString(f.root.resolve("valuable.txt"), "keep");
            for (ToolInvocation call :
                    List.of(
                            command("delete", List.of("del valuable.txt"), "CMD"),
                            command("reset", List.of("git", "reset", "--hard"), "ARGV"))) {
                var result =
                        f.execute(
                                call,
                                new DefaultPolicyEngine(),
                                ApprovalService.rejectAll(),
                                CancellationToken.NONE);
                assertEquals(ToolStatus.DENIED, result.status());
                assertEquals("PERMISSION_DENIED", result.error().orElseThrow().code());
                assertTrue(
                        f.events().stream()
                                .noneMatch(
                                        e ->
                                                e.callId() != null
                                                        && e.callId().equals(call.callId())
                                                        && Set.of("tool.intent", "tool.started")
                                                                .contains(e.type())));
                SessionEvent prompt =
                        f.events().stream()
                                .filter(
                                        e ->
                                                e.type().equals("approval.requested")
                                                        && call.callId().equals(e.callId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(call.arguments(), Values.object(prompt.payload().get("arguments")));
                assertTrue(
                        f.events().stream()
                                .anyMatch(
                                        e ->
                                                e.type().equals("approval.decided")
                                                        && call.callId().equals(e.callId())
                                                        && Boolean.FALSE.equals(
                                                                e.payload().get("approved"))));
            }
            assertEquals("keep", Files.readString(f.root.resolve("valuable.txt")));
        }
    }

    @Test
    void
            conservativeCostReservationStopsBeforeSecondTransmissionAndUnavailableAccountingFailsClosed()
                    throws Exception {
        try (var f = new Fixture()) {
            FakeLlm llm =
                    new FakeLlm(List.of(reply(command("one", fixture("echo"), "ARGV")), reply()));
            f.loop(
                            llm,
                            new Budget(
                                    5,
                                    Duration.ofSeconds(10),
                                    Optional.of(new BigDecimal("0.010"))),
                            request -> new BigDecimal("0.006"),
                            APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals(1, llm.requests().size());
            assertEquals(TaskStatus.LIMIT_REACHED, f.project().taskStatus());
            assertEquals("COST_LIMIT", f.events().getLast().payload().get("reason"));
            assertEquals("0.006", f.events().getLast().payload().get("reservedApiCostUsd"));
            assertEquals(
                    1, f.events().stream().filter(e -> e.type().equals("budget.reserved")).count());
        }
        try (var f = new Fixture()) {
            FakeLlm llm = new FakeLlm(List.of(reply()));
            f.loop(
                            llm,
                            new Budget(1, Duration.ofSeconds(5), Optional.of(BigDecimal.ONE)),
                            ApiCostGuard.unavailable(),
                            APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals(0, llm.requests().size());
            assertEquals("COST_BUDGET_UNAVAILABLE", f.events().getLast().payload().get("reason"));
        }
    }

    @Test
    void configuredPricesRequireMatchingModelAndExplicitOutputCap() {
        var costs =
                new ConfiguredTokenCostGuard(
                        "fixture",
                        8192,
                        100,
                        new BigDecimal("0.000001"),
                        new BigDecimal("0.000002"));
        assertEquals(
                "COST_BUDGET_UNAVAILABLE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        costs.actualCost(
                                                new ChatRequest(List.of(), List.of(), Map.of()),
                                                null))
                        .code());
        assertEquals(
                new BigDecimal("0.008392"),
                costs.maximumCost(
                        new ChatRequest(
                                List.of(),
                                List.of(),
                                Map.of("model", "fixture", "max_tokens", 100))));
        assertEquals(
                "COST_BUDGET_UNAVAILABLE",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        costs.maximumCost(
                                                new ChatRequest(
                                                        List.of(),
                                                        List.of(),
                                                        Map.of("model", "fixture"))))
                        .code());
        assertThrows(
                HarnessException.class,
                () ->
                        costs.maximumCost(
                                new ChatRequest(
                                        List.of(),
                                        List.of(),
                                        Map.of("model", "different", "max_tokens", 100))));
    }

    @Test
    void externalCancelTraversesRunEndpointAgentAndRealProcessAndRecordsCleanup() throws Exception {
        try (var f = new Fixture();
                var control = new LocalRunControl(temporary.resolve("runs"));
                var run = new ControlledAgentRun(control);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            FakeLlm llm =
                    new FakeLlm(
                            List.of(reply(command("blocking", fixture("block"), "ARGV")), reply()));
            AgentLoop loop =
                    f.loop(
                            llm,
                            new Budget(5, Duration.ofSeconds(10)),
                            ApiCostGuard.unavailable(),
                            APPROVE);
            Future<?> future =
                    executor.submit(
                            () -> run.run(loop, f.writer, f.bus, SCOPE, CancellationToken.NONE));
            awaitFile(f.root.resolve("started"));
            Process client =
                    new ProcessBuilder(
                                    java(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    CancelMain.class.getName(),
                                    "--state-dir",
                                    temporary.resolve("runs").toString(),
                                    "--run",
                                    run.runId())
                            .start();
            try {
                assertTrue(client.waitFor(5, TimeUnit.SECONDS));
                assertEquals(0, client.exitValue());
                future.get(5, TimeUnit.SECONDS);
                assertEquals(TaskStatus.CANCELLED, f.project().taskStatus());
                assertEquals(1, llm.requests().size());
                SessionEvent completed =
                        f.events().stream()
                                .filter(e -> e.type().equals("tool.completed"))
                                .findFirst()
                                .orElseThrow();
                var result = Values.object(completed.payload().get("value"));
                assertEquals(true, result.get("observedClean"));
                assertEquals("partial", result.get("cleanupStatus"));
                assertEquals(List.of(), result.get("residualPids"));
                assertEquals(
                        LocalRunControl.CancelResult.ALREADY_EXITED,
                        LocalRunControl.cancel(temporary.resolve("runs"), run.runId()));
                assertFalse(f.events().toString().contains("token="));
            } finally {
                client.destroyForcibly();
                LocalRunControl.cancel(temporary.resolve("runs"), run.runId());
            }
        }
    }

    @Test
    void elapsedBudgetCancelsRealCommandAndReportsLimitInsteadOfCompletion() throws Exception {
        try (var f = new Fixture()) {
            FakeLlm llm =
                    new FakeLlm(
                            List.of(reply(command("blocked", fixture("block"), "ARGV")), reply()));
            f.loop(llm, new Budget(5, Duration.ofMillis(1000)), ApiCostGuard.unavailable(), APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals(TaskStatus.LIMIT_REACHED, f.project().taskStatus());
            assertEquals("TIME_LIMIT", f.events().getLast().payload().get("reason"));
            assertEquals(1, llm.requests().size());
        }
    }

    @Test
    void actualMavenWrapperFailureReachesAgentThenFileRepairAndRebuildSucceed() throws Exception {
        Duration mavenCommandLimit = ProcessLimits.defaults().timeout();
        int mavenTimeoutMs = Math.toIntExact(mavenCommandLimit.toMillis());
        try (var f = new Fixture(mavenCommandLimit)) {
            Files.writeString(
                    f.root.resolve("pom.xml"),
                    """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                <groupId>fixture</groupId><artifactId>m3</artifactId><version>1</version>
                <properties><maven.compiler.release>21</maven.compiler.release></properties>
                <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version></plugin></plugins></build></project>
                """);
            Path source = f.root.resolve("src/main/java/Broken.java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "class Broken { invalid java }\n");
            String version = f.files.versionOf(new ExecPath("src/main/java/Broken.java"));
            Path repo = Path.of(System.getProperty("basedir")).toRealPath().getParent();
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            if (windows) {
                // Verify the offline prerequisite using the real existing Wrapper distribution.
                // Hosted Windows native startup can exceed the short-process fixture budget;
                // this Maven workload uses the existing production command default instead.
                Path preparationLog = temporary.resolve("wrapper-ready.log");
                var preparation =
                        new ProcessBuilder(
                                        "cmd.exe",
                                        "/d",
                                        "/s",
                                        "/c",
                                        "call \"" + repo.resolve("mvnw.cmd") + "\" -B -o --version")
                                .directory(f.root.toFile())
                                .redirectErrorStream(true)
                                .redirectOutput(preparationLog.toFile());
                preparation.environment().clear();
                preparation.environment().putAll(LocalProcessRunner.buildEnvironment());
                preparation.environment().put("MVNW_VERBOSE", "true");
                Process ready = preparation.start();
                try {
                    assertTrue(
                            ready.waitFor(60, TimeUnit.SECONDS), "Wrapper preparation timed out");
                    byte[] preparationBytes = Files.readAllBytes(preparationLog);
                    int nulBytes = 0;
                    for (byte value : preparationBytes) if (value == 0) nulBytes++;
                    var diagnosticEncoding =
                            nulBytes > preparationBytes.length / 4
                                    ? java.nio.charset.StandardCharsets.UTF_16LE
                                    : java.nio.charset.Charset.forName(
                                            System.getProperty("native.encoding", "UTF-8"));
                    String diagnostic = new String(preparationBytes, diagnosticEncoding);
                    assertEquals(0, ready.exitValue(), diagnostic);
                    assertTrue(diagnostic.contains("Apache Maven 3.9.9"), diagnostic);
                    var homeLine =
                            diagnostic
                                    .lines()
                                    .filter(line -> line.startsWith("Maven home: "))
                                    .findFirst()
                                    .orElseThrow();
                    Path home = Path.of(homeLine.substring("Maven home: ".length()).trim());
                    assertTrue(home.getFileName().toString().startsWith("verified-"));
                    var properties = new java.util.Properties();
                    try (var input =
                            Files.newInputStream(
                                    repo.resolve(".mvn/wrapper/maven-wrapper.properties"))) {
                        properties.load(input);
                    }
                    String checksum = properties.getProperty("distributionSha256Sum");
                    assertEquals(checksum, Files.readString(home.resolve(".complete")));
                    assertEquals(checksum, ProbeSupport.hash(home.resolve(".distribution.zip")));
                } finally {
                    ready.destroyForcibly();
                }
            }
            List<String> command =
                    windows
                            ? List.of("call \"" + repo.resolve("mvnw.cmd") + "\" -B -o compile")
                            : List.of(
                                    "sh '"
                                            + repo.resolve("mvnw").toString().replace("'", "'\\''")
                                            + "' -B -o compile");
            String mode = windows ? "CMD" : "SH";
            FakeLlm llm =
                    new FakeLlm(
                            List.of(
                                    reply(command("build-fail", command, mode, mavenTimeoutMs)),
                                    reply(
                                            new ToolInvocation(
                                                    "repair",
                                                    "edit_file",
                                                    Map.of(
                                                            "path",
                                                            "src/main/java/Broken.java",
                                                            "oldText",
                                                            "invalid java",
                                                            "newText",
                                                            "int value = 1;",
                                                            "expectedVersion",
                                                            version),
                                                    SideEffectLevel.WORKSPACE_WRITE)),
                                    reply(command("build-pass", command, mode, mavenTimeoutMs)),
                                    reply()));
            f.loop(llm, new Budget(5, Duration.ofMinutes(5)), ApiCostGuard.unavailable(), APPROVE)
                    .run(CancellationToken.NONE);
            assertEquals(TaskStatus.COMPLETED, f.project().taskStatus(), f.events().toString());
            var results =
                    f.events().stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && e.callId().startsWith("build-"))
                            .toList();
            assertEquals("FAILED", results.getFirst().payload().get("status"));
            assertEquals("SUCCESS", results.getLast().payload().get("status"));
            assertTrue(llm.requests().get(1).messages().toString().contains("NONZERO_EXIT"));
            assertTrue(Files.exists(f.root.resolve("target/classes/Broken.class")));
            var value = Values.object(results.getLast().payload().get("value"));
            assertEquals("omitted", Values.object(value.get("environment")).get("values"));
            assertEquals(command, value.get("argv"));
            var first = Values.object(results.getFirst().payload().get("value"));
            var reference = Values.object(first.get("stdoutArtifact"));
            String text =
                    new LocalArtifactStore(temporary.resolve("artifacts"), new SecretRedactor())
                            .read("s", (String) reference.get("id"), 0, 8192);
            assertTrue(text.contains("COMPILATION ERROR"));
        }
    }

    static ToolInvocation command(String id, List<String> argv, String mode) {
        return command(id, argv, mode, 20000);
    }

    static ToolInvocation command(String id, List<String> argv, String mode, int timeoutMs) {
        return new ToolInvocation(
                id,
                "execute_command",
                Map.of("argv", argv, "mode", mode, "cwd", ".", "timeoutMs", timeoutMs),
                SideEffectLevel.SYSTEM_IMPACT);
    }

    static ChatResponse reply(ToolInvocation... calls) {
        return new ChatResponse("fixture", List.of(calls), Map.of());
    }

    static String java() {
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
    }

    static List<String> fixture(String mode) {
        return List.of(
                java(),
                "-cp",
                System.getProperty("java.class.path"),
                M3CommandFixture.class.getName(),
                mode);
    }

    static void awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(path));
    }

    private final class Fixture implements AutoCloseable {
        final Path root;
        final LocalWorkspace files;
        final LocalProcessRunner process;
        final JsonlSessionRepository repo;
        final SessionWriter writer;
        final SessionReader reader;
        final DefaultEventBus bus = new DefaultEventBus();
        final ScopedToolRegistry tools = new ScopedToolRegistry();
        final DefaultToolPipeline pipeline =
                new DefaultToolPipeline(new JsonSchemaValidator(), new SecretRedactor());
        final PluginContainer container;

        Fixture() throws Exception {
            this(Duration.ofSeconds(20));
        }

        Fixture(Duration commandLimit) throws Exception {
            root = Files.createDirectory(temporary.resolve(UUID.randomUUID().toString()));
            files = new LocalWorkspace(root);
            process =
                    new LocalProcessRunner(
                            files,
                            new ProcessLimits(commandLimit, 2, 64, 16384),
                            LocalProcessRunner.buildEnvironment(),
                            List.of());
            repo = new JsonlSessionRepository(temporary.resolve("logs-" + UUID.randomUUID()));
            writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
            reader = repo.openReader("s");
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
            HarnessPlugin registry =
                    new HarnessPlugin() {
                        @Override
                        public String id() {
                            return "registry";
                        }

                        @Override
                        public String version() {
                            return "1";
                        }

                        @Override
                        public void start(PluginContext context) {
                            context.register(ToolRegistry.class, "default", tools);
                        }

                        @Override
                        public void stop() {}
                    };
            container =
                    new PluginContainer(
                            SCOPE,
                            new DefaultServiceRegistry(),
                            List.of(
                                    registry,
                                    new WorkspaceToolsPlugin(
                                            files,
                                            new LocalArtifactStore(
                                                    temporary.resolve("artifacts"),
                                                    new SecretRedactor()),
                                            process),
                                    new ProcessToolsPlugin(
                                            process,
                                            new LocalArtifactStore(
                                                    temporary.resolve("artifacts"),
                                                    new SecretRedactor())),
                                    new PolicyPlugin(
                                            new DefaultPolicyEngine(),
                                            ApprovalService.rejectAll())),
                            Set.of("registry", "workspace-local", "process-local", "policy"),
                            Map.of(),
                            List.of());
            container.start();
        }

        List<SessionEvent> events() {
            return reader.events();
        }

        SessionProjection project() {
            return new EventSessionProjection(events());
        }

        ToolResult execute(
                ToolInvocation call,
                PolicyEngine policy,
                ApprovalService approvals,
                CancellationToken cancellation) {
            return pipeline.execute(
                    call,
                    new ToolPipelineContext(
                            SCOPE,
                            "t",
                            "step",
                            tools,
                            writer,
                            bus,
                            policy,
                            approvals,
                            new DefaultCheckpointPolicy(),
                            cancellation));
        }

        AgentLoop loop(
                LlmClient llm, Budget budget, ApiCostGuard costs, ApprovalService approvals) {
            var runtime =
                    new AgentRuntimeServices(
                            SCOPE,
                            reader,
                            EventSessionProjection::new,
                            new DefaultCheckpointPolicy(),
                            approvals,
                            pipeline,
                            new EventRequestJournal(),
                            new SecretRedactor(),
                            (response, projection) ->
                                    new CompletionEvaluator.CompletionDecision(
                                            true, "fixture-evidence"),
                            costs);
            return new DefaultAgentLoop(
                    new AgentLoopDependencies(
                            llm,
                            tools,
                            writer,
                            project(),
                            new DefaultPromptComposer(tools, SCOPE, Map.of()),
                            bus,
                            new DefaultPolicyEngine(),
                            CancellationToken.NONE,
                            budget),
                    runtime);
        }

        @Override
        public void close() {
            container.close();
            writer.close();
            bus.close();
        }
    }
}
