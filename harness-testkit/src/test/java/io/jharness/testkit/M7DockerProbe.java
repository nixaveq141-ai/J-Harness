package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Opt-in actual Docker probe; never reported as a unit-test sandbox substitute. */
public final class M7DockerProbe {
    private M7DockerProbe() {}

    public static void main(String[] args) throws Exception {
        ProbeSupport.begin(args);
        Path root = Path.of(args[2]).toAbsolutePath().resolve("run-" + UUID.randomUUID());
        Files.createDirectories(root);
        Path localRoot = root.resolve("local"), dockerRoot = root.resolve("docker");
        Files.createDirectories(localRoot.resolve("sub"));
        Files.createDirectories(dockerRoot.resolve("sub"));
        for (Path dir : List.of(localRoot, dockerRoot))
            Files.writeString(dir.resolve("sub/input.txt"), "before\n");
        ProbeSupport.fixtureWritable(dockerRoot);
        var transport = new NativeCommandTransport();
        Map<String, Object> result = new LinkedHashMap<>();
        ProbeSupport.track(result);
        result.put("milestone", "M7");
        result.put("slice", "execution-environment");
        result.put("image", args[1]);
        result.put("actualDocker", true);
        var container =
                new DockerExecutionEnvironment(
                        args[0], args[1], dockerRoot, Path.of(args[3]), transport);
        String owner = container.id();
        try (container;
                var local = new LocalExecutionEnvironment("probe-local", localRoot, List.of())) {
            // Trusted dependency entry point, never generated code; retain fixed classifications
            // only.
            var nativeLoad =
                    transport.call(
                            List.of(
                                    args[0],
                                    "exec",
                                    owner,
                                    "java",
                                    "-cp",
                                    "/runtime/harness.jar",
                                    EnvironmentWorker.class.getName(),
                                    "--native-check"),
                            new byte[0],
                            Duration.ofSeconds(10),
                            CancellationToken.NONE);
            String nativeError =
                    new String(nativeLoad.stderr(), java.nio.charset.StandardCharsets.UTF_8);
            result.put(
                    "trustedNativeLoad",
                    Map.of(
                            "exitCode",
                            nativeLoad.exitCode(),
                            "uncertain",
                            nativeLoad.uncertain(),
                            "mappingDenied",
                            nativeError.contains("failed to map segment")
                                    || nativeError.contains("Operation not permitted"),
                            "permissionDenied",
                            nativeError.contains("Permission denied")));
            for (ExecutionEnvironment environment : List.of(local, container)) {
                WorkspaceFiles files = (WorkspaceFiles) environment.files();
                var input = new ExecPath("sub/input.txt");
                require(files.readText(input).equals("before\n"), "initial read");
                files.edit(input, "before", "after", 1, null, CancellationToken.NONE);
                require(files.readText(input).equals("after\n"), "guarded edit");
                boolean docker = environment == container;
                var command =
                        new CommandRequest(
                                "probe-command",
                                docker || !System.getProperty("os.name").startsWith("Windows")
                                        ? CommandRequest.Mode.SH
                                        : CommandRequest.Mode.CMD,
                                List.of(
                                        docker
                                                        || !System.getProperty("os.name")
                                                                .startsWith("Windows")
                                                ? "printf shell > shell.txt && cat input.txt && pwd"
                                                : "echo shell>shell.txt && type input.txt && cd"),
                                new ExecPath("sub"),
                                Duration.ofSeconds(15));
                var answer = environment.processes().execute(command, CancellationToken.NONE);
                require(answer.status() == ToolStatus.SUCCESS, "shell status " + answer);
                require(
                        files.readText(new ExecPath("sub/shell.txt")).trim().equals("shell"),
                        "same cwd filesystem");
                result.put(
                        docker ? "dockerTask" : "localTask",
                        Map.of("read", true, "guardedEdit", true, "shellCwd", true));
            }
            result.put("descriptor", container.descriptor());
            var inspect =
                    transport.call(
                            List.of(args[0], "inspect", owner),
                            new byte[0],
                            Duration.ofSeconds(10),
                            CancellationToken.NONE);
            require(inspect.exitCode() == 0 && !inspect.uncertain(), "inspect");
            var actual = new ObjectMapper().readTree(inspect.stdout()).get(0);
            int nativeMounts = 0;
            for (var mount : actual.get("Mounts"))
                if (mount.get("Destination").asText().equals("/runtime/native")) {
                    require(
                            mount.get("Type").asText().equals("bind")
                                    && !mount.get("RW").asBoolean(),
                            "native dependency readonly mount");
                    nativeMounts++;
                }
            require(
                    nativeMounts == 1 && nativeLoad.exitCode() == 0 && !nativeLoad.uncertain(),
                    "trusted native load");
            result.put("trustedNativeReadonlyMount", true);
            var host = actual.get("HostConfig");
            // Only fixed isolation fields; never persist Config.Env or general inspect output.
            var isolationFields = new LinkedHashMap<String, Object>();
            for (String field :
                    List.of(
                            "NetworkMode",
                            "ReadonlyRootfs",
                            "CapDrop",
                            "SecurityOpt",
                            "Memory",
                            "PidsLimit")) isolationFields.put(field, host.get(field));
            result.put("isolationInspection", isolationFields);
            require(host.get("NetworkMode").asText().equals("none"), "network");
            require(host.get("ReadonlyRootfs").asBoolean(), "readonly root");
            require(
                    host.get("CapDrop").isArray()
                            && host.get("CapDrop").size() == 1
                            && host.get("CapDrop").get(0).asText().equals("ALL"),
                    "caps");
            require(noNewPrivileges(host.get("SecurityOpt")), "privileges");
            require(actual.get("Config").get("User").asText().equals("65534:65534"), "nonroot");
            require(
                    host.get("Memory").asLong() == 512L * 1024 * 1024
                            && host.get("PidsLimit").asInt() == 64,
                    "quotas");
            var isolation =
                    container
                            .processes()
                            .execute(
                                    new CommandRequest(
                                            "isolation",
                                            CommandRequest.Mode.SH,
                                            List.of(
                                                    "test ! -S /var/run/docker.sock && test ! -e /host && test \"$(id -u)\" = 65534 && grep -q '^NoNewPrivs:[[:space:]]*1$' /proc/self/status && command -v touch >/dev/null && ! touch /forbidden"),
                                            new ExecPath("."),
                                            Duration.ofSeconds(10)),
                                    CancellationToken.NONE);
            require(
                    isolation.status() == ToolStatus.SUCCESS,
                    "actual isolation checks: " + isolation);
            result.put("actualIsolationChecks", true);
            for (ExecutionEnvironment environment : List.of(local, container)) {
                boolean docker = environment == container;
                var files = (WorkspaceFiles) environment.files();
                var model =
                        new FakeLlm(
                                List.of(
                                        new ChatResponse(
                                                "read",
                                                List.of(
                                                        new ToolInvocation(
                                                                "sdk-read",
                                                                "read_file",
                                                                Map.of("path", "sub/input.txt"),
                                                                SideEffectLevel.READ_ONLY)),
                                                Map.of()),
                                        new ChatResponse(
                                                "edit",
                                                List.of(
                                                        new ToolInvocation(
                                                                "sdk-edit",
                                                                "edit_file",
                                                                Map.of(
                                                                        "path",
                                                                        "sub/input.txt",
                                                                        "oldText",
                                                                        "after",
                                                                        "newText",
                                                                        "sdk"),
                                                                SideEffectLevel.READ_ONLY)),
                                                Map.of()),
                                        new ChatResponse(
                                                "command",
                                                List.of(
                                                        new ToolInvocation(
                                                                "sdk-command",
                                                                "execute_command",
                                                                Map.of(
                                                                        "mode",
                                                                        docker
                                                                                        || !System
                                                                                                .getProperty(
                                                                                                        "os.name")
                                                                                                .startsWith(
                                                                                                        "Windows")
                                                                                ? "SH"
                                                                                : "CMD",
                                                                        "argv",
                                                                        List.of(
                                                                                docker
                                                                                                || !System
                                                                                                        .getProperty(
                                                                                                                "os.name")
                                                                                                        .startsWith(
                                                                                                                "Windows")
                                                                                        ? "printf '%s' 'SDK \"quoted\" value' > sdk-shell.txt"
                                                                                        : "echo SDK \"quoted\" value>sdk-shell.txt"),
                                                                        "cwd",
                                                                        "sub",
                                                                        "timeoutMs",
                                                                        10000),
                                                                SideEffectLevel.READ_ONLY)),
                                                Map.of()),
                                        new ChatResponse("done", List.of(), Map.of())));
                String session = docker ? "docker-sdk" : "local-sdk";
                var repository = new JsonlSessionRepository(root.resolve("sessions"));
                try (var sdk =
                        new HarnessSdk(
                                session,
                                docker ? dockerRoot.toString() : localRoot.toString(),
                                repository,
                                environment,
                                model,
                                new LocalArtifactStore(
                                        root.resolve("artifacts"), new SecretRedactor()),
                                new SecretRedactor(),
                                new DefaultPolicyEngine(),
                                (call, context) ->
                                        new ApprovalService.ApprovalDecision(
                                                true, "fixed probe authorized", "fixture"),
                                new Budget(6, Duration.ofSeconds(60)),
                                (response, projection) -> {
                                    try {
                                        return new CompletionEvaluator.CompletionDecision(
                                                files.readText(new ExecPath("sub/input.txt"))
                                                                .equals("sdk\n")
                                                        && files.readText(
                                                                        new ExecPath(
                                                                                "sub/sdk-shell.txt"))
                                                                .trim()
                                                                .equals("SDK \"quoted\" value"),
                                                "actual file and shell evidence");
                                    } catch (Exception ex) {
                                        return new CompletionEvaluator.CompletionDecision(
                                                false, "missing evidence");
                                    }
                                },
                                ApiCostGuard.unavailable(),
                                List.of())) {
                    var snapshot =
                            sdk.run(
                                    "Read, edit, and execute in the selected environment",
                                    CancellationToken.NONE);
                    require(snapshot.status() == TaskStatus.COMPLETED, "SDK task " + session);
                    require(
                            snapshot.events().stream()
                                            .filter(e -> e.type().equals("tool.intent"))
                                            .count()
                                    == 3,
                            "durable intents");
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
                                    "request reconstruct");
                    }
                    require(requests == 4, "request count");
                    result.put(
                            session,
                            Map.of(
                                    "status",
                                    snapshot.status().name(),
                                    "requestsReconstructed",
                                    requests,
                                    "durableIntents",
                                    3,
                                    "quotedShell",
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
                                "label=io.jharness.owner=" + owner,
                                "--format",
                                "{{.Names}}"),
                        new byte[0],
                        Duration.ofSeconds(10),
                        CancellationToken.NONE);
        require(
                remaining.exitCode() == 0
                        && !remaining.uncertain()
                        && remaining.stdout().length == 0,
                "owned container removed");
        result.put("cleanupVerified", true);
        result.put("result", "SUCCESS");
        result.put("capturedAt", java.time.Instant.now().toString());
        result.put("jarSha256", ProbeSupport.hash(Path.of(args[3])));
        ProbeSupport.publish(Path.of(args[4]), result);
        System.out.println("M7 Docker environment probe SUCCESS");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }

    static boolean noNewPrivileges(com.fasterxml.jackson.databind.JsonNode options) {
        return options != null
                && options.isArray()
                && options.size() == 1
                && options.get(0).isTextual()
                && Set.of("no-new-privileges", "no-new-privileges=true", "no-new-privileges:true")
                        .contains(options.get(0).asText());
    }
}
