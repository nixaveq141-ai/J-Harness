package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Actual local/Docker delegation; fake models, real workspace and source-linked child logs. */
public final class M7SubagentDockerProbe {
    private M7SubagentDockerProbe() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[2]).toAbsolutePath().resolve("subagent-" + UUID.randomUUID());
        Path localRoot = root.resolve("local"), dockerRoot = root.resolve("docker");
        for (Path workspace : List.of(localRoot, dockerRoot)) {
            Files.createDirectories(workspace);
            Files.writeString(workspace.resolve("input.txt"), "before");
        }
        var transport = new NativeCommandTransport();
        var result = new LinkedHashMap<String, Object>();
        result.put("milestone", "M7");
        result.put("slice", "read-only-subagent");
        result.put("actualDocker", true);
        result.put("image", args[1]);
        var docker =
                new DockerExecutionEnvironment(
                        args[0], args[1], dockerRoot, Path.of(args[3]), transport);
        String owner = docker.id();
        try (docker;
                var local = new LocalExecutionEnvironment("subagent-local", localRoot, List.of())) {
            for (ExecutionEnvironment environment : List.of(local, docker)) {
                boolean container = environment == docker;
                var files = (WorkspaceFiles) environment.files();
                var path = new ExecPath("input.txt");
                var independent = files.isolatedView();
                require(independent.readText(path).equals("before"), "isolated read");
                boolean conflict = false;
                try {
                    files.write(path, "bad", true, null, CancellationToken.NONE);
                } catch (HarnessException ex) {
                    conflict = ex.code().equals("FILE_CONFLICT");
                }
                require(conflict, "child reads cannot grant parent write version");
                var child =
                        new FakeLlm(
                                List.of(call("read_file", Map.of("path", "input.txt")), done()),
                                32768);
                var parent =
                        new FakeLlm(
                                List.of(
                                        call(
                                                "delegate_task",
                                                Map.of(
                                                        "profile",
                                                        "inspect",
                                                        "goal",
                                                        "Read input.txt")),
                                        call("read_file", Map.of("path", "input.txt")),
                                        call(
                                                "edit_file",
                                                Map.of(
                                                        "path",
                                                        "input.txt",
                                                        "oldText",
                                                        "before",
                                                        "newText",
                                                        "after")),
                                        done()),
                                32768);
                var profile =
                        new SubagentPlugin.Profile(
                                "Report factual file contents; read-only",
                                Set.of("read_file"),
                                new Budget(2, Duration.ofSeconds(30)),
                                () -> child,
                                ApiCostGuard.unavailable(),
                                (response, projection) ->
                                        new CompletionEvaluator.CompletionDecision(
                                                projection.toolHistory().stream()
                                                        .anyMatch(
                                                                event ->
                                                                        event.type()
                                                                                        .equals(
                                                                                                "tool.completed")
                                                                                && "SUCCESS"
                                                                                        .equals(
                                                                                                event.payload()
                                                                                                        .get(
                                                                                                                "status"))
                                                                                && "read_file"
                                                                                        .equals(
                                                                                                event.payload()
                                                                                                        .get(
                                                                                                                "tool"))
                                                                                && "input.txt"
                                                                                        .equals(
                                                                                                Values
                                                                                                        .object(
                                                                                                                event.payload()
                                                                                                                        .get(
                                                                                                                                "value"))
                                                                                                        .get(
                                                                                                                "path"))),
                                                "actual input.txt read required"));
                var plugin = new SubagentPlugin(Map.of("inspect", profile), 2, 4);
                String session = container ? "subagent-docker" : "subagent-local";
                var repository = new JsonlSessionRepository(root.resolve("sessions"));
                var artifacts =
                        new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor());
                try (var sdk =
                        new HarnessSdk(
                                session,
                                (container ? dockerRoot : localRoot).toString(),
                                repository,
                                environment,
                                parent,
                                artifacts,
                                new SecretRedactor(),
                                new DefaultPolicyEngine(),
                                (call, context) ->
                                        new ApprovalService.ApprovalDecision(
                                                true, "fixed delegation probe approved", "fixture"),
                                new Budget(6, Duration.ofSeconds(60)),
                                (response, projection) -> {
                                    try {
                                        return new CompletionEvaluator.CompletionDecision(
                                                files.readText(path).equals("after"),
                                                "actual parent file edit");
                                    } catch (Exception ex) {
                                        return new CompletionEvaluator.CompletionDecision(
                                                false, "missing file evidence");
                                    }
                                },
                                ApiCostGuard.unavailable(),
                                List.of(plugin))) {
                    var snapshot =
                            sdk.run(
                                    "Delegate reading; independently read and perform parent edit",
                                    CancellationToken.NONE);
                    require(snapshot.status() == TaskStatus.COMPLETED, "parent task status");
                    var parentTools = parent.requests().getFirst().tools();
                    require(
                            parent.requests().size() >= 2
                                    && parent.requests().stream()
                                            .allMatch(
                                                    request -> request.tools().equals(parentTools)),
                            "parent registry preserved before and after delegation");
                    if (container) {
                        var edit =
                                snapshot.events().stream()
                                        .filter(
                                                e ->
                                                        e.type().equals("tool.completed")
                                                                && "edit_file"
                                                                        .equals(
                                                                                e.payload()
                                                                                        .get(
                                                                                                "tool")))
                                        .findFirst()
                                        .orElseThrow();
                        var metadata =
                                Values.object(
                                        Values.object(edit.payload().get("value")).get("metadata"));
                        require(
                                Boolean.TRUE.equals(metadata.get("permissionsPreserved")),
                                "POSIX mode preserved");
                        require(
                                Boolean.FALSE.equals(metadata.get("ownerPreserved"))
                                        && metadata.get("ownershipNote") instanceof String,
                                "unavailable POSIX ownership explicitly reported");
                    }
                    var assignment =
                            snapshot.events().stream()
                                    .filter(event -> event.type().equals("subagent.started"))
                                    .findFirst()
                                    .orElseThrow();
                    String childId = (String) assignment.payload().get("childSessionId");
                    var childEvents = repository.openReader(childId).events();
                    require(
                            SessionRecovery.inspect(snapshot.events()).ready()
                                    && SessionRecovery.inspect(childEvents).ready(),
                            "settled logs");
                    require(
                            childEvents.stream()
                                    .anyMatch(
                                            event ->
                                                    event.type().equals("subagent.origin")
                                                            && assignment
                                                                    .eventId()
                                                                    .equals(
                                                                            event.payload()
                                                                                    .get(
                                                                                            "parentEventId"))),
                            "origin source link");
                    require(
                            reconstruct(snapshot.events(), parent) == 4
                                    && reconstruct(childEvents, child) == 2,
                            "parent and child request reconstruction");
                    require(
                            child.requests().getFirst().tools().size() == 1,
                            "isolated read-only registry");
                    var completed =
                            snapshot.events().stream()
                                    .filter(event -> event.type().equals("subagent.completed"))
                                    .findFirst()
                                    .orElseThrow();
                    var artifact = Values.object(completed.payload().get("artifact"));
                    require(
                            artifacts
                                    .read(session, (String) artifact.get("id"), 0, 65536)
                                    .contains(childId),
                            "parent scoped result artifact");
                    result.put(
                            session,
                            Map.of(
                                    "status",
                                    snapshot.status().name(),
                                    "parentRequestsReconstructed",
                                    4,
                                    "childRequestsReconstructed",
                                    2,
                                    "independentVersionGuards",
                                    true,
                                    "parentEditVerified",
                                    true,
                                    "originAndArtifactVerified",
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
        System.out.println("M7 actual Docker Subagent/SDK probe SUCCESS");
    }

    private static int reconstruct(List<SessionEvent> events, FakeLlm model) {
        int sent = 0;
        for (int index = 0; index < events.size(); index++)
            if (events.get(index).type().equals("model.requested"))
                require(
                        model.requests()
                                .get(sent++)
                                .equals(
                                        new EventRequestJournal()
                                                .reconstruct(
                                                        events.subList(0, index + 1),
                                                        events.get(index).eventId())),
                        "request prefix reconstruction");
        return sent;
    }

    private static ChatResponse call(String name, Map<String, Object> arguments) {
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

    private static ChatResponse done() {
        return new ChatResponse("untrusted fixture report", List.of(), Map.of());
    }

    private static void require(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }
}
