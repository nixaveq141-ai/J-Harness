package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Opt-in actual container Skills/SDK parity; the model remains a local scripted fixture. */
public final class M7SkillsDockerProbe {
    private M7SkillsDockerProbe() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[2]).toAbsolutePath().resolve("skills-" + UUID.randomUUID());
        Path localRoot = root.resolve("local"), dockerRoot = root.resolve("docker");
        String localScript = "echo skill-approved>marker.txt",
                dockerScript = "printf '%s' 'skill-approved' > marker.txt";
        for (Path workspace : List.of(localRoot, dockerRoot)) {
            Files.createDirectories(workspace.resolve("skills/demo"));
            Files.writeString(
                    workspace.resolve("skills/demo/SKILL.md"),
                    "# Probe\nRead the guide and execute only the approved script.\n");
            Files.writeString(
                    workspace.resolve("skills/demo/guide.txt"), "fixed resource evidence");
            Files.writeString(
                    workspace.resolve("skills/demo/run.txt"),
                    workspace.equals(localRoot) ? localScript : dockerScript);
        }
        var transport = new NativeCommandTransport();
        var result = new LinkedHashMap<String, Object>();
        result.put("milestone", "M7");
        result.put("slice", "skills-sdk");
        result.put("actualDocker", true);
        result.put("image", args[1]);
        var docker =
                new DockerExecutionEnvironment(
                        args[0], args[1], dockerRoot, Path.of(args[3]), transport);
        String owner = docker.id();
        try (docker;
                var local = new LocalExecutionEnvironment("skills-local", localRoot, List.of())) {
            for (ExecutionEnvironment environment : List.of(local, docker)) {
                boolean container = environment == docker;
                var mode = container ? CommandRequest.Mode.SH : CommandRequest.Mode.CMD;
                String script = container ? dockerScript : localScript;
                var arguments =
                        Map.<String, Object>of(
                                "path",
                                "skills/demo/run.txt",
                                "cwd",
                                "skills/demo",
                                "mode",
                                mode.name(),
                                "revision",
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256")
                                                        .digest(
                                                                script.getBytes(
                                                                        StandardCharsets.UTF_8))));
                var plugin =
                        new SkillsPlugin(
                                new SkillsPlugin.Spec(
                                        "demo",
                                        new ExecPath("skills/demo"),
                                        Map.of("guide", new ExecPath("guide.txt")),
                                        Map.of(
                                                "check",
                                                new SkillsPlugin.Script(
                                                        new ExecPath("run.txt"),
                                                        mode,
                                                        Duration.ofSeconds(10)))));
                var model =
                        new FakeLlm(
                                List.of(
                                        reply("skill_demo__load", Map.of()),
                                        reply("skill_demo__resource", Map.of("resource", "guide")),
                                        reply("skill_demo__run_check", arguments),
                                        new ChatResponse("done", List.of(), Map.of())),
                                32768);
                String session = container ? "skills-docker" : "skills-local";
                var repository = new JsonlSessionRepository(root.resolve("sessions"));
                try (var sdk =
                        new HarnessSdk(
                                session,
                                (container ? dockerRoot : localRoot).toString(),
                                repository,
                                environment,
                                model,
                                new LocalArtifactStore(
                                        root.resolve("artifacts"), new SecretRedactor()),
                                new SecretRedactor(),
                                new DefaultPolicyEngine(),
                                (call, context) ->
                                        new ApprovalService.ApprovalDecision(
                                                true, "fixed Skills probe approved", "fixture"),
                                new Budget(5, Duration.ofSeconds(60)),
                                (response, projection) -> {
                                    try {
                                        return new CompletionEvaluator.CompletionDecision(
                                                environment
                                                        .files()
                                                        .readText(
                                                                new ExecPath(
                                                                        "skills/demo/marker.txt"))
                                                        .trim()
                                                        .equals("skill-approved"),
                                                "environment file evidence");
                                    } catch (Exception ex) {
                                        return new CompletionEvaluator.CompletionDecision(
                                                false, "missing evidence");
                                    }
                                },
                                ApiCostGuard.unavailable(),
                                List.of(plugin))) {
                    var snapshot =
                            sdk.run(
                                    "Load skill, read resource, execute approved script",
                                    CancellationToken.NONE);
                    require(
                            snapshot.status() == TaskStatus.COMPLETED,
                            "Skills SDK task " + session);
                    int requests = 0;
                    for (int index = 0; index < snapshot.events().size(); index++) {
                        var event = snapshot.events().get(index);
                        if (event.type().equals("model.requested"))
                            require(
                                    model.requests()
                                            .get(requests++)
                                            .equals(
                                                    new EventRequestJournal()
                                                            .reconstruct(
                                                                    snapshot.events()
                                                                            .subList(0, index + 1),
                                                                    event.eventId())),
                                    "request reconstruction");
                    }
                    require(requests == 4, "request count");
                    result.put(
                            session,
                            Map.of(
                                    "status",
                                    snapshot.status().name(),
                                    "requestsReconstructed",
                                    requests,
                                    "actualScriptFileEvidence",
                                    true));
                }
            }
        }
        var remaining =
                transport.call(
                        List.of(
                                args[0],
                                "ps",
                                "--all",
                                "--filter",
                                "name=" + owner,
                                "--format",
                                "{{.Names}}"),
                        new byte[0],
                        Duration.ofSeconds(10),
                        CancellationToken.NONE);
        require(
                remaining.exitCode() == 0 && remaining.stdout().length == 0,
                "owned container cleanup");
        result.put("cleanupVerified", true);
        result.put("result", "SUCCESS");
        result.put("capturedAt", java.time.Instant.now().toString());
        result.put(
                "jarSha256",
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(Path.of(args[3])))));
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(Path.of(args[4]).toFile(), result);
        System.out.println("M7 actual Docker Skills/SDK probe SUCCESS");
    }

    private static ChatResponse reply(String name, Map<String, Object> arguments) {
        return new ChatResponse(
                "fixture",
                List.of(
                        new ToolInvocation(
                                UUID.randomUUID().toString(),
                                name,
                                arguments,
                                SideEffectLevel.READ_ONLY)),
                Map.of());
    }

    private static void require(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }
}
