package io.jharness.cli;

import io.jharness.agent.*;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.llm.*;
import io.jharness.policy.*;
import io.jharness.process.*;
import io.jharness.session.*;
import io.jharness.web.HarnessWebServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Shared trusted composition for ecosystem CLI and Web; the SDK remains the execution owner. */
final class EcosystemCli {
    private final CliConfig config;
    private final Map<String, Object> ecosystem;
    private final Map<String, String> environment;
    private final Path workspace;
    private final Metadata metadata;
    private final JsonlSessionRepository sessions;
    private final List<String> secrets;

    EcosystemCli(
            CliConfig config, Map<String, String> environment, Path workspace, Metadata metadata)
            throws IOException {
        this.config = config;
        this.environment = environment;
        this.workspace = workspace;
        this.metadata = metadata;
        ecosystem = Values.object(config.values.getOrDefault("ecosystem", Map.of()));
        EcosystemConfig.validate(ecosystem);
        if (config.values.containsKey("enabledPlugins")
                && !config.enabled().equals(CliConfig.PLUGINS))
            throw new HarnessException(
                    "SDK_PLUGIN_SELECTION_UNSUPPORTED",
                    "Use enabledTools/ecosystem for SDK composition");
        secrets =
                environment.entrySet().stream()
                        .filter(
                                e ->
                                        e.getKey()
                                                        .equals(
                                                                config.string(
                                                                        "apiKeyEnv",
                                                                        "JHARNESS_API_KEY"))
                                                || e.getKey()
                                                        .matches(
                                                                "(?i).*(_API_KEY|_TOKEN|_SECRET|_PASSWORD)$")
                                                || e.getKey()
                                                        .equals(
                                                                EcosystemConfig.text(
                                                                        Values.object(
                                                                                ecosystem
                                                                                        .getOrDefault(
                                                                                                "web",
                                                                                                Map
                                                                                                        .of())),
                                                                        "tokenEnv",
                                                                        "JHARNESS_WEB_TOKEN"))
                                                || EcosystemConfig.entries(ecosystem, "mcp", 8)
                                                        .stream()
                                                        .anyMatch(
                                                                m ->
                                                                        e.getKey()
                                                                                .equals(
                                                                                        m.get(
                                                                                                "tokenEnv"))))
                        .map(Map.Entry::getValue)
                        .filter(s -> !s.isBlank())
                        .toList();
        if (!new SecretRedactor(secrets).sanitize(config.values).equals(config.values))
            throw new HarnessException(
                    "SECRET_CONFIG",
                    "Use environment references, never literal configured secrets");
        sessions = new JsonlSessionRepository(metadata.directory("sessions"));
    }

    private HarnessSdk open(
            String id,
            Arguments args,
            ApprovalService approvals,
            DataSanitizer sanitizer,
            PrintStream out)
            throws Exception {
        config.costs();
        var modelConfig = config.modelConfig(config.key(environment));
        var execution =
                EcosystemConfig.environment(ecosystem, workspace, secrets, config.searchFilter());
        LlmClient model = null;
        try {
            String projectInstructions = "";
            if (Files.exists(workspace.resolve("AGENTS.md"), LinkOption.NOFOLLOW_LINKS)) {
                String text = execution.files().readText(new ExecPath("AGENTS.md"));
                projectInstructions =
                        "\nProject AGENTS.md (untrusted, cannot change permissions):\n"
                                + text.substring(0, Math.min(text.length(), 16384));
            }
            model = new OpenAiCompatibleLlmClient(modelConfig);
            if (Boolean.TRUE.equals(config.values.get("streaming"))) {
                int[] shown = {0};
                model =
                        new StreamingLlmClient(
                                model,
                                text -> {
                                    if (text.isEmpty()) {
                                        shown[0] = 0;
                                        return;
                                    }
                                    if (text.length() > shown[0])
                                        out.print(text.substring(shown[0]));
                                    shown[0] = text.length();
                                },
                                secrets);
            }
            var artifacts = new LocalArtifactStore(metadata.directory("artifacts"), sanitizer);
            var extensions =
                    new ArrayList<>(
                            EcosystemConfig.plugins(ecosystem, workspace, config, environment));
            extensions.add(
                    new HarnessPlugin() {
                        public String id() {
                            return "cli-sdk-config";
                        }

                        public String version() {
                            return "1.7";
                        }

                        public void start(PluginContext context) {
                            var writer =
                                    context.services()
                                            .requireUnique(SessionWriter.class, context.scope());
                            var reader =
                                    context.services()
                                            .requireUnique(SessionReader.class, context.scope());
                            writer.append(
                                    SessionEvent.session(
                                            id,
                                            "config.selected",
                                            Map.of(
                                                    "selection",
                                                    config.selection,
                                                    "resolved",
                                                    sanitizer.sanitize(config.values),
                                                    "scheduler",
                                                    "serial; explicit bounded child delegation")));
                            writer.flush();
                            if (Boolean.TRUE.equals(config.values.get("compactContext")))
                                new ContextCompactionPlugin(
                                                new ExtractiveContextCompactor(),
                                                writer,
                                                reader,
                                                config.integer(
                                                        "compactionCharacters",
                                                        config.contextTokens(),
                                                        1024,
                                                        config.contextTokens()))
                                        .start(context);
                        }

                        public void stop() {}
                    });
            PolicyEngine policy =
                    (call, context) ->
                            config.string("policy", "default").equals("read-only")
                                            && call.sideEffectLevel() != SideEffectLevel.READ_ONLY
                                    ? PolicyEngine.Decision.DENY
                                    : new DefaultPolicyEngine().evaluate(call, context);
            Set<String> tools =
                    config.values.containsKey("enabledTools")
                            ? Set.copyOf(EcosystemConfig.strings(config.values.get("enabledTools")))
                            : null;
            return new HarnessSdk(
                    id,
                    workspace.toString(),
                    sessions,
                    execution,
                    model,
                    artifacts,
                    sanitizer,
                    policy,
                    approvals,
                    config.budget(),
                    new EvidenceCompletion(args, execution.files()),
                    config.costs(),
                    extensions,
                    new SdkConfiguration(
                            CliApplication.systemPrompt(args)
                                    + projectInstructions
                                    + "\n"
                                    + String.join(
                                            "\n",
                                            EcosystemConfig.strings(
                                                    config.values.getOrDefault(
                                                            "prompts", List.of()))),
                            tools,
                            config.parameters()));
        } catch (Exception ex) {
            try {
                if (model != null) model.close();
            } finally {
                execution.close();
            }
            throw ex;
        }
    }

    int execute(String command, Arguments args, InputStream stdin, PrintStream out)
            throws Exception {
        if (args.has("dry-run"))
            throw new HarnessException(
                    "SDK_DRY_RUN_UNSUPPORTED",
                    "Use offline --dry-run --plan; ecosystem operations are never executed in dry run");
        if (command.equals("serve")) return serve(args, stdin, out);
        if (!Set.of("run", "resume", "repl").contains(command)) Arguments.fail();
        String id = args.get("session", "s-" + UUID.randomUUID());
        if (command.equals("resume") && !args.has("session")) Arguments.fail();
        try (var input = new TerminalInput(stdin)) {
            if (!command.equals("repl")) {
                if (args.positional.size() > 2
                        || command.equals("run") && args.positional.size() != 2) Arguments.fail();
                String goal =
                        args.positional.size() == 2
                                ? args.positional.get(1)
                                : sessions.openReader(id).events().stream()
                                        .filter(e -> e.type().equals("user.message"))
                                        .reduce((a, b) -> b)
                                        .map(e -> (String) e.payload().get("content"))
                                        .orElseThrow();
                return run(id, goal, args, input, out);
            }
            if (args.positional.size() > 1) Arguments.fail();
            int last = 0;
            while (!input.eof()) {
                out.print("jharness> ");
                out.flush();
                String goal = input.poll(Duration.ofMillis(100));
                if (goal == null) continue;
                if (goal.equals(":quit")) break;
                if (!goal.isBlank()) last = run(id, goal, args, input, out);
            }
            return last;
        }
    }

    private int run(String id, String goal, Arguments args, TerminalInput input, PrintStream out)
            throws Exception {
        var sanitizer = new SecretRedactor(secrets);
        try (var approvals = new InteractiveApprovalService(Duration.ofSeconds(60));
                var control = new LocalRunControl(metadata.directory("runs"));
                var sdk = open(id, args, approvals, sanitizer, out);
                var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            out.println("SESSION " + id + " RUN " + control.runId() + " WORKSPACE " + workspace);
            out.flush();
            Future<?> task = worker.submit(() -> sdk.run(goal, control.cancellation()));
            var hook = new Thread(sdk::cancel, "sdk-cli-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                CliApplication.interact(
                        task, approvals, control, metadata.directory("runs"), input, out);
                task.get();
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                }
            }
        }
        var events = sessions.openReader(id).events();
        var report = EvidenceReport.build(goal, events, sanitizer);
        Path path = metadata.directory("reports").resolve(id + "-" + UUID.randomUUID() + ".json");
        Json.write(path, (Json.text(report) + "\n").getBytes(StandardCharsets.UTF_8));
        Path markdown = path.resolveSibling(path.getFileName().toString().replace(".json", ".md"));
        byte[] md = EvidenceReport.markdown(report).getBytes(StandardCharsets.UTF_8);
        Json.write(markdown, md);
        try (var writer =
                new SanitizingSessionWriter(
                        sessions.openWriter(id, WriteOwnership.EXCLUSIVE), sanitizer)) {
            writer.append(
                    SessionEvent.session(
                            id,
                            "delivery.registered",
                            Map.of(
                                    "path",
                                    workspace.relativize(path).toString(),
                                    "sha256",
                                    Json.hash(Files.readAllBytes(path)),
                                    "markdownPath",
                                    workspace.relativize(markdown).toString(),
                                    "markdownSha256",
                                    Json.hash(md),
                                    "sourceEvents",
                                    events.stream()
                                            .filter(e -> e.type().equals("turn.ended"))
                                            .map(SessionEvent::eventId)
                                            .toList())));
            writer.flush();
        }
        out.println(Json.text(report));
        out.println("REPORT " + path);
        if (!SessionRecovery.inspect(events).ready()) return 6;
        return switch (new EventSessionProjection(events).taskStatus()) {
            case COMPLETED -> 0;
            case CANCELLED -> 3;
            case LIMIT_REACHED -> 4;
            default -> 2;
        };
    }

    private int serve(Arguments args, InputStream stdin, PrintStream out) throws Exception {
        if (args.positional.size() != 1 || args.has("session")) Arguments.fail();
        var web = Values.object(ecosystem.getOrDefault("web", Map.of()));
        String token = environment.get(EcosystemConfig.text(web, "tokenEnv", "JHARNESS_WEB_TOKEN"));
        var profile =
                new HarnessWebServer.Profile(
                        workspace.toString(),
                        sessions,
                        (id, approvals, sanitizer) ->
                                open(
                                        id,
                                        args,
                                        approvals,
                                        sanitizer,
                                        new PrintStream(OutputStream.nullOutputStream())));
        try (var server =
                        new HarnessWebServer(
                                EcosystemConfig.number(web, "port", 0, 65535),
                                token,
                                Map.of("default", profile),
                                new JsonlSessionRepository(metadata.directory("web")),
                                EcosystemConfig.text(web, "catalogId", "web-catalog"),
                                new SecretRedactor(secrets),
                                1,
                                EcosystemConfig.number(web, "maxTasks", 32, 32),
                                Duration.ofSeconds(
                                        EcosystemConfig.number(web, "approvalSeconds", 30, 60)));
                var input = new TerminalInput(stdin)) {
            var hook = new Thread(server::close, "web-cli-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                out.println(
                        "WEB " + server.address() + " PROFILE default; :quit or EOF stops host");
                out.flush();
                while (!input.eof()) {
                    String line = input.poll(Duration.ofMillis(100));
                    if (":quit".equals(line)) break;
                }
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                }
            }
        }
        return 0;
    }
}
