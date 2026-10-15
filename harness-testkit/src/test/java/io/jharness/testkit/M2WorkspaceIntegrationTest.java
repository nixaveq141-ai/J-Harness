package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import io.jharness.tools.*;
import io.jharness.workspace.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2WorkspaceIntegrationTest {
    @TempDir Path temporary;
    private static final PluginScope SCOPE = new PluginScope("s", "agent");

    @Test
    void actualFileReadEditLoopUsesDurableIntentAndEveryDiskRequestReconstructs() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.writeString(f.root.resolve("Main.java"), "class Main { int value = 1; }\n");
            Files.writeString(f.root.resolve("other.txt"), "unchanged");
            FakeLlm llm =
                    new FakeLlm(
                            List.of(
                                    reply(
                                            "read",
                                            call("read_file", "read", Map.of("path", "Main.java"))),
                                    reply(
                                            "edit",
                                            call(
                                                    "edit_file",
                                                    "edit",
                                                    Map.of(
                                                            "path",
                                                            "Main.java",
                                                            "oldText",
                                                            "value = 1",
                                                            "newText",
                                                            "value = 2"))),
                                    reply("done")));
            AgentRuntimeServices runtime =
                    f.runtime(
                            (response, projection) -> {
                                boolean verified =
                                        Files.readString(f.root.resolve("Main.java"))
                                                .contains("value = 2");
                                return new CompletionEvaluator.CompletionDecision(
                                        verified, "file-evidence");
                            });
            AgentLoopDependencies core = f.core(llm);
            new DefaultAgentLoopFactory(runtime).create(core).run(CancellationToken.NONE);
            assertEquals(TaskStatus.COMPLETED, f.project().taskStatus());
            assertEquals("unchanged", Files.readString(f.root.resolve("other.txt")));
            List<SessionEvent> events = f.events();
            int sent = 0;
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).type().equals("model.requested"))
                    assertEquals(
                            llm.requests().get(sent++),
                            new EventRequestJournal()
                                    .reconstruct(
                                            events.subList(0, i + 1), events.get(i).eventId()));
            assertEquals(3, sent);
            SessionEvent intent =
                    events.stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.intent")
                                                    && "edit".equals(e.callId()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    "value = 2", Values.object(intent.payload().get("arguments")).get("newText"));
            SessionEvent result =
                    events.stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "edit".equals(e.callId()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("SUCCESS", result.payload().get("status"));
            assertTrue(Values.object(result.payload().get("display")).containsKey("afterVersion"));
            f.writer.flush();
            assertEquals(events, new JsonlSessionRepository(f.store).openReader("s").events());
        }
    }

    @Test
    void rejectedOutsideReadIsAnEventAndUserConcurrentChangesAreNotOverwritten() throws Exception {
        try (Fixture f = new Fixture()) {
            Path outside = temporary.resolve("secret.txt");
            Files.writeString(outside, "outside-secret");
            ToolResult denied =
                    f.run(call("read_file", "outside", Map.of("path", "../secret.txt")));
            assertEquals(ToolStatus.DENIED, denied.status());
            assertEquals("PATH_DENIED", denied.error().orElseThrow().code());
            assertFalse(f.events().toString().contains("outside-secret"));
            Files.writeString(f.root.resolve("a"), "same");
            f.run(call("read_file", "read", Map.of("path", "a")));
            Files.writeString(f.root.resolve("a"), "same plus user");
            ToolResult conflict =
                    f.run(
                            call(
                                    "edit_file",
                                    "edit",
                                    Map.of("path", "a", "oldText", "same", "newText", "replace")));
            assertEquals(ToolStatus.FAILED, conflict.status());
            assertEquals("FILE_CONFLICT", conflict.error().orElseThrow().code());
            assertEquals("same plus user", Files.readString(f.root.resolve("a")));
        }
    }

    @Test
    void actualJsonlForceFailureBeforeWriteLeavesTargetUntouchedAndNeverReachesCommit()
            throws Exception {
        Path root = Files.createDirectory(temporary.resolve("workspace")),
                store = temporary.resolve("store");
        List<String> diagnostics = new ArrayList<>();
        JsonlSessionRepository repository =
                new JsonlSessionRepository(
                        store,
                        JsonlSessionRepository.TailPolicy.REJECT,
                        channel -> {
                            String last = Files.readAllLines(store.resolve("s.jsonl")).getLast();
                            if (last.contains("\"type\":\"tool.intent\""))
                                throw new IOException("injected force failure");
                            channel.force(true);
                        },
                        diagnostics::add);
        boolean[] commit = {false};
        LocalWorkspace workspace =
                new LocalWorkspace(root, Duration.ofSeconds(5), path -> commit[0] = true);
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        ScopedToolRegistry tools = new ScopedToolRegistry();
        HarnessPlugin provider = registryPlugin(tools);
        try (DefaultEventBus bus = new DefaultEventBus();
                SessionWriter writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE);
                PluginContainer container =
                        new PluginContainer(
                                SCOPE,
                                registry,
                                List.of(
                                        provider,
                                        new WorkspaceToolsPlugin(
                                                workspace,
                                                new LocalArtifactStore(
                                                        store.resolve("artifacts"),
                                                        new SecretRedactor()))),
                                Set.of("registry", "workspace-local"),
                                Map.of(),
                                List.of())) {
            container.start();
            ToolPipelineContext context =
                    new ToolPipelineContext(
                            SCOPE,
                            "t",
                            "p",
                            tools,
                            writer,
                            bus,
                            (call, c) -> PolicyEngine.Decision.ALLOW,
                            ApprovalService.rejectAll(),
                            new DefaultCheckpointPolicy(),
                            CancellationToken.NONE);
            assertThrows(
                    HarnessException.class,
                    () ->
                            new DefaultToolPipeline(new JsonSchemaValidator(), new SecretRedactor())
                                    .execute(
                                            call(
                                                    "write_file",
                                                    "write",
                                                    Map.of(
                                                            "path",
                                                            "effect.txt",
                                                            "content",
                                                            "must-not-happen")),
                                            context));
            assertFalse(commit[0]);
            assertFalse(Files.exists(root.resolve("effect.txt")));
            assertTrue(diagnostics.contains("CHECKPOINT_FAILED"));
        }
    }

    @Test
    void crashAfterPhysicalWriteButBeforeResultIsUncertainAndCannotAutomaticallyRunAgain()
            throws Exception {
        Path root = Files.createDirectory(temporary.resolve("workspace")),
                store = temporary.resolve("store");
        String java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString();
        Process process =
                new ProcessBuilder(
                                java,
                                "-cp",
                                System.getProperty("java.class.path"),
                                CrashWriteProbe.class.getName(),
                                store.toString(),
                                root.toString())
                        .redirectErrorStream(true)
                        .start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(17, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        assertEquals("committed", Files.readString(root.resolve("effect.txt")));
        JsonlSessionRepository repository = new JsonlSessionRepository(store);
        SessionReader reader = repository.openReader("s");
        assertEquals(
                List.of("crash-call"),
                new EventSessionProjection(reader.events()).uncertainCalls());
        FakeLlm llm = new FakeLlm(List.of(reply("do not resend")));
        try (DefaultEventBus bus = new DefaultEventBus();
                SessionWriter writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            AgentRuntimeServices runtime =
                    new AgentRuntimeServices(
                            SCOPE,
                            reader,
                            EventSessionProjection::new,
                            new DefaultCheckpointPolicy(),
                            ApprovalService.rejectAll(),
                            new DefaultToolPipeline(
                                    new JsonSchemaValidator(), new SecretRedactor()),
                            new EventRequestJournal(),
                            new SecretRedactor(),
                            (response, projection) ->
                                    new CompletionEvaluator.CompletionDecision(
                                            false, "unverified"));
            new DefaultAgentLoopFactory(runtime)
                    .create(
                            new AgentLoopDependencies(
                                    llm,
                                    new ScopedToolRegistry(),
                                    writer,
                                    new EventSessionProjection(reader.events()),
                                    projection ->
                                            new ChatRequest(
                                                    projection.messages(), List.of(), Map.of()),
                                    bus,
                                    (call, c) -> PolicyEngine.Decision.DENY,
                                    CancellationToken.NONE,
                                    new Budget(2, Duration.ofSeconds(3))))
                    .run(CancellationToken.NONE);
            assertTrue(llm.requests().isEmpty());
            assertEquals("UNKNOWN_SIDE_EFFECT", reader.events().getLast().payload().get("reason"));
            assertEquals("committed", Files.readString(root.resolve("effect.txt")));
        }
    }

    @Test
    void oversizedDiffSpillsToPersistedReferenceAndCanBeReadBackAfterReopen() throws Exception {
        try (Fixture f = new Fixture()) {
            String content = "line of generated content\n".repeat(600);
            ToolResult result =
                    f.run(
                            call(
                                    "write_file",
                                    "write",
                                    Map.of("path", "large.txt", "content", content)));
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertEquals(true, result.value().get("outputTruncated"));
            String id = (String) result.value().get("artifactRef");
            String page =
                    new LocalArtifactStore(f.store.resolve("artifacts"), new SecretRedactor())
                            .read("s", id, 0, 8192);
            assertTrue(page.startsWith("--- a/large.txt"));
            ToolResult readBack =
                    f.run(call("read_artifact", "artifact", Map.of("id", id, "limit", 8192)));
            assertEquals(ToolStatus.SUCCESS, readBack.status());
            assertTrue(readBack.value().toString().contains("large.txt"));
            assertTrue(Files.readString(f.root.resolve("large.txt")).equals(content));
        }
    }

    @Test
    void gitDiffUsesActualGitAndPluginStopRevokesFilesystemTools() throws Exception {
        try (Fixture f = new Fixture()) {
            git(f.root, "init");
            Files.writeString(f.root.resolve("a.txt"), "before\n");
            git(f.root, "add", ".");
            git(
                    f.root,
                    "-c",
                    "user.name=JHarness fixture",
                    "-c",
                    "user.email=fixture@example.invalid",
                    "-c",
                    "core.hooksPath=disabled-hooks",
                    "commit",
                    "-m",
                    "fixture");
            f.run(call("read_file", "read", Map.of("path", "a.txt")));
            ToolResult write =
                    f.run(
                            call(
                                    "edit_file",
                                    "edit",
                                    Map.of(
                                            "path", "a.txt", "oldText", "before", "newText",
                                            "after")));
            assertEquals(ToolStatus.SUCCESS, write.status());
            ToolResult diff = f.run(call("git_diff", "diff", Map.of()));
            assertEquals("git", diff.value().get("source"));
            assertTrue(((String) diff.value().get("diff")).contains("+after"));
            f.container.stop();
            assertTrue(f.tools.resolve("write_file", SCOPE).isEmpty());
            assertTrue(f.registry.find(FileSystemView.class, "default", SCOPE).isEmpty());
        }
    }

    private static void git(Path root, String... arguments) throws Exception {
        List<String> argv = new ArrayList<>(List.of("git", "-C", root.toString()));
        argv.addAll(List.of(arguments));
        Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private static ToolInvocation call(String name, String id, Map<String, Object> arguments) {
        return new ToolInvocation(
                id,
                name,
                arguments,
                SideEffectLevel.READ_ONLY); // Provider declaration overrides this model claim.
    }

    private static ChatResponse reply(String text, ToolInvocation... calls) {
        return new ChatResponse(text, List.of(calls), Map.of());
    }

    private static HarnessPlugin registryPlugin(ToolRegistry registry) {
        return new HarnessPlugin() {
            @Override
            public String id() {
                return "registry";
            }

            @Override
            public String version() {
                return "1.2";
            }

            @Override
            public void start(PluginContext context) {
                context.register(ToolRegistry.class, "default", registry);
            }

            @Override
            public void stop() {}
        };
    }

    @FunctionalInterface
    private interface Evidence {
        CompletionEvaluator.CompletionDecision evaluate(
                ChatResponse response, SessionProjection projection) throws IOException;
    }

    private final class Fixture implements AutoCloseable {
        final Path root, store;
        final JsonlSessionRepository repo;
        final SessionWriter writer;
        final SessionReader reader;
        final DefaultEventBus bus = new DefaultEventBus();
        final ScopedToolRegistry tools = new ScopedToolRegistry();
        final DefaultServiceRegistry registry = new DefaultServiceRegistry();
        final DefaultToolPipeline pipeline =
                new DefaultToolPipeline(new JsonSchemaValidator(), new SecretRedactor());
        final PluginContainer container;

        Fixture() throws IOException {
            root = Files.createDirectory(temporary.resolve("workspace"));
            store = temporary.resolve("store");
            repo = new JsonlSessionRepository(store);
            writer =
                    new SanitizingSessionWriter(
                            repo.openWriter("s", WriteOwnership.EXCLUSIVE), new SecretRedactor());
            reader = repo.openReader("s");
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
            writer.append(
                    SessionEvent.session(
                            "s",
                            "user.message",
                            Map.of("role", "user", "content", "Edit Main.java precisely")));
            List<HarnessPlugin> plugins =
                    List.of(
                            registryPlugin(tools),
                            new JsonlSessionPlugin(
                                    repo,
                                    new LocalArtifactStore(
                                            store.resolve("artifacts"), new SecretRedactor())),
                            new WorkspaceToolsPlugin(
                                    new LocalWorkspace(root),
                                    new LocalArtifactStore(
                                            store.resolve("artifacts"), new SecretRedactor())));
            container =
                    new PluginContainer(
                            SCOPE,
                            registry,
                            plugins,
                            Set.of("registry", "session-jsonl", "workspace-local"),
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

        ToolResult run(ToolInvocation call) {
            return pipeline.execute(
                    call,
                    new ToolPipelineContext(
                            SCOPE,
                            "t",
                            "p",
                            tools,
                            writer,
                            bus,
                            (request, context) -> PolicyEngine.Decision.ALLOW,
                            ApprovalService.rejectAll(),
                            new DefaultCheckpointPolicy(),
                            CancellationToken.NONE));
        }

        AgentRuntimeServices runtime(Evidence evidence) {
            return new AgentRuntimeServices(
                    SCOPE,
                    reader,
                    EventSessionProjection::new,
                    new DefaultCheckpointPolicy(),
                    ApprovalService.rejectAll(),
                    pipeline,
                    new EventRequestJournal(),
                    new SecretRedactor(),
                    (response, projection) -> {
                        try {
                            return evidence.evaluate(response, projection);
                        } catch (IOException ex) {
                            throw new HarnessException("EVIDENCE_FAILED", "Cannot verify goal");
                        }
                    });
        }

        AgentLoopDependencies core(LlmClient llm) {
            return new AgentLoopDependencies(
                    llm,
                    tools,
                    writer,
                    project(),
                    new DefaultPromptComposer(tools, SCOPE, Map.of()),
                    bus,
                    (call, context) -> PolicyEngine.Decision.ALLOW,
                    CancellationToken.NONE,
                    new Budget(5, Duration.ofSeconds(10)));
        }

        @Override
        public void close() {
            container.close();
            writer.close();
            bus.close();
        }
    }
}
