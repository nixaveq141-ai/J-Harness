package io.jharness.process;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;

public final class ProcessToolsPlugin implements HarnessPlugin {
    private final LocalProcessRunner runner;
    private final ArtifactStore artifacts;

    public ProcessToolsPlugin(LocalProcessRunner runner) {
        this(runner, null);
    }

    public ProcessToolsPlugin(LocalProcessRunner runner, ArtifactStore artifacts) {
        this.runner = Objects.requireNonNull(runner);
        this.artifacts = artifacts;
    }

    @Override
    public String id() {
        return "process-local";
    }

    @Override
    public String version() {
        return "1.3";
    }

    @Override
    public void start(PluginContext context) {
        context.register(ProcessRunner.class, "default", runner);
        context.own(runner);
        ToolRegistry registry =
                context.services().requireUnique(ToolRegistry.class, context.scope());
        context.own(
                registry.register(
                        new CommandTool(runner, context.scope(), artifacts), context.scope()));
    }

    @Override
    public void stop() {}

    public static final class CommandTool implements AgentTool {
        private final ProcessRunner runner;
        private final PluginScope scope;
        private final ArtifactStore artifacts;

        public CommandTool(ProcessRunner runner, PluginScope scope) {
            this(runner, scope, null);
        }

        public CommandTool(ProcessRunner runner, PluginScope scope, ArtifactStore artifacts) {
            this.runner = Objects.requireNonNull(runner);
            this.scope = Objects.requireNonNull(scope);
            this.artifacts = artifacts;
        }

        @Override
        public String name() {
            return "execute_command";
        }

        @Override
        public String description() {
            return "Run bounded argv or an explicitly approved Shell in the selected execution environment. Supported Shell modes depend on the provider. Shell effects cannot be previewed.";
        }

        @Override
        public SideEffectLevel sideEffectLevel() {
            return SideEffectLevel.SYSTEM_IMPACT;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type",
                    "object",
                    "required",
                    List.of("argv"),
                    "additionalProperties",
                    false,
                    "properties",
                    Map.of(
                            "argv",
                            Map.of(
                                    "type",
                                    "array",
                                    "items",
                                    Map.of("type", "string"),
                                    "minItems",
                                    1,
                                    "maxItems",
                                    256),
                            "mode",
                            Map.of(
                                    "type",
                                    "string",
                                    "enum",
                                    List.of("ARGV", "CMD", "POWERSHELL", "SH")),
                            "cwd",
                            Map.of("type", "string"),
                            "timeoutMs",
                            Map.of("type", "integer", "minimum", 1, "maximum", 3600000)));
        }

        @Override
        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        @Override
        public void validateArguments(ToolInvocation call) {
            new JsonSchema().validate(inputSchema(), call.arguments());
            CommandInputs.request(call);
        }

        @Override
        public ToolResult execute(ToolInvocation call, ToolContext context)
                throws java.io.IOException {
            if (!scope.equals(context.scope()))
                throw new HarnessException("SCOPE_DENIED", "Command tool scope mismatch");
            validateArguments(call);
            Map<String, Object> args = call.arguments();
            List<String> argv =
                    ((List<?>) args.get("argv")).stream().map(String.class::cast).toList();
            try {
                ToolResult result =
                        runner.execute(
                                new CommandRequest(
                                        call.callId(),
                                        CommandRequest.Mode.valueOf(
                                                (String) args.getOrDefault("mode", "ARGV")),
                                        argv,
                                        new ExecPath((String) args.getOrDefault("cwd", ".")),
                                        Duration.ofMillis(
                                                ((Number) args.getOrDefault("timeoutMs", 120000))
                                                        .longValue())),
                                context.cancellation());
                if (artifacts == null) return result;
                Map<String, Object> value = new LinkedHashMap<>(result.value());
                Map<String, Object> model = new LinkedHashMap<>();
                for (String key :
                        List.of(
                                "exitCode",
                                "timedOut",
                                "cancelled",
                                "truncated",
                                "cleanupStatus",
                                "observedClean",
                                "residualPids",
                                "sideEffectUncertain",
                                "reason",
                                "limitations"))
                    if (value.containsKey(key)) model.put(key, value.get(key));
                result.error().ifPresent(error -> model.put("error", error.code()));
                for (String key : List.of("stdout", "stderr")) {
                    if (value.get(key) instanceof String text) {
                        model.put(
                                key,
                                text.length() <= 1024
                                        ? text
                                        : JsonEncoding.prefix(text, 1024)
                                                + "[MODEL PREVIEW TRUNCATED]");
                        if (text.length() > 1024) {
                            ArtifactStore.Artifact artifact;
                            try {
                                artifact = artifacts.put(scope.sessionId(), text);
                            } catch (java.io.IOException | RuntimeException failure) {
                                value.put(key + "ArtifactStatus", "FAILED");
                                model.put(key + "ArtifactStatus", "FAILED");
                                continue;
                            }
                            Map<String, Object> reference =
                                    Map.of(
                                            "id",
                                            artifact.id(),
                                            "bytes",
                                            artifact.bytes(),
                                            "sha256",
                                            artifact.sha256(),
                                            "readBackTool",
                                            "read_artifact");
                            value.put(key + "Artifact", reference);
                            model.put(key + "Artifact", reference);
                        }
                    }
                }
                return new ToolResult(
                        result.callId(),
                        result.status(),
                        value,
                        JsonEncoding.text(model, 16384, "PROCESS_RESULT_LIMIT"),
                        result.display(),
                        result.error());
            } catch (IllegalArgumentException ex) {
                return ToolResults.failure(call.callId(), ToolStatus.FAILED, "INVALID_COMMAND");
            }
        }
    }
}
