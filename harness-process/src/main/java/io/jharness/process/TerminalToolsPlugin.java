package io.jharness.process;

import io.jharness.api.*;
import java.time.Duration;
import java.util.*;

/** Terminal mutations use the ordinary policy/approval/durable-intent tool pipeline. */
public final class TerminalToolsPlugin implements HarnessPlugin {
    private final TerminalSession terminal;

    public TerminalToolsPlugin(TerminalSession terminal) {
        this.terminal = Objects.requireNonNull(terminal);
    }

    @Override
    public String id() {
        return "terminal-local";
    }

    @Override
    public String version() {
        return "1.6";
    }

    @Override
    public void start(PluginContext context) {
        context.register(TerminalSession.class, "default", terminal);
        var registry = context.services().requireUnique(ToolRegistry.class, context.scope());
        for (String operation : List.of("start", "status", "log", "write", "resize", "stop")) {
            AgentTool tool =
                    new AgentTool() {
                        @Override
                        public String name() {
                            return "terminal_" + operation;
                        }

                        @Override
                        public String description() {
                            return "Persistent local PTY/background task "
                                    + operation
                                    + "; tasks last until CLI/REPL closes, shell/input require approval, sampled cleanup only.";
                        }

                        @Override
                        public SideEffectLevel sideEffectLevel() {
                            return Set.of("status", "log").contains(operation)
                                    ? SideEffectLevel.READ_ONLY
                                    : SideEffectLevel.SYSTEM_IMPACT;
                        }

                        @Override
                        public Map<String, Object> inputSchema() {
                            Map<String, Object> properties = new LinkedHashMap<>();
                            List<String> required = new ArrayList<>();
                            if (operation.equals("start")) {
                                properties.put(
                                        "argv",
                                        Map.of(
                                                "type",
                                                "array",
                                                "items",
                                                Map.of("type", "string"),
                                                "minItems",
                                                1,
                                                "maxItems",
                                                256));
                                required.add("argv");
                                properties.put(
                                        "mode",
                                        Map.of(
                                                "type",
                                                "string",
                                                "enum",
                                                List.of("ARGV", "CMD", "POWERSHELL", "SH")));
                                properties.put("cwd", Map.of("type", "string"));
                                properties.put("pty", Map.of("type", "boolean"));
                                properties.put(
                                        "timeoutMs",
                                        Map.of(
                                                "type", "integer", "minimum", 1, "maximum",
                                                3600000));
                            } else {
                                properties.put("id", Map.of("type", "string"));
                                required.add("id");
                            }
                            if (operation.equals("write")) {
                                properties.put(
                                        "text", Map.of("type", "string", "maxLength", 65536));
                                required.add("text");
                            }
                            if (operation.equals("log")) {
                                properties.put("offset", Map.of("type", "integer", "minimum", 0));
                                properties.put(
                                        "limit",
                                        Map.of("type", "integer", "minimum", 1, "maximum", 65536));
                            }
                            if (operation.equals("resize")) {
                                properties.put(
                                        "columns",
                                        Map.of("type", "integer", "minimum", 10, "maximum", 500));
                                required.add("columns");
                                properties.put(
                                        "rows",
                                        Map.of("type", "integer", "minimum", 5, "maximum", 200));
                                required.add("rows");
                            }
                            return Map.of(
                                    "type",
                                    "object",
                                    "additionalProperties",
                                    false,
                                    "properties",
                                    properties,
                                    "required",
                                    required);
                        }

                        @Override
                        public Map<String, Object> outputSchema() {
                            return Map.of("type", "object");
                        }

                        @Override
                        public void validateArguments(ToolInvocation call) {
                            new JsonSchema().validate(inputSchema(), call.arguments());
                            if (operation.equals("start")) CommandInputs.request(call);
                        }

                        @Override
                        public ToolResult execute(ToolInvocation call, ToolContext invocation) {
                            if (!context.scope().equals(invocation.scope()))
                                throw new HarnessException(
                                        "SCOPE_DENIED", "Terminal scope mismatch");
                            validateArguments(call);
                            var args = call.arguments();
                            String id = (String) args.get("id");
                            try {
                                Map<String, Object> value =
                                        switch (operation) {
                                            case "start" ->
                                                    terminal.start(
                                                            new CommandRequest(
                                                                    call.callId(),
                                                                    CommandRequest.Mode.valueOf(
                                                                            (String)
                                                                                    args
                                                                                            .getOrDefault(
                                                                                                    "mode",
                                                                                                    "ARGV")),
                                                                    ((List<?>) args.get("argv"))
                                                                            .stream()
                                                                                    .map(
                                                                                            String
                                                                                                            .class
                                                                                                    ::cast)
                                                                                    .toList(),
                                                                    new ExecPath(
                                                                            (String)
                                                                                    args
                                                                                            .getOrDefault(
                                                                                                    "cwd",
                                                                                                    ".")),
                                                                    Duration.ofMillis(
                                                                            ((Number)
                                                                                            args
                                                                                                    .getOrDefault(
                                                                                                            "timeoutMs",
                                                                                                            120000))
                                                                                    .longValue())),
                                                            Boolean.TRUE.equals(args.get("pty")),
                                                            invocation.cancellation());
                                            case "status" -> terminal.status(id);
                                            case "log" ->
                                                    terminal.log(
                                                            id,
                                                            ((Number)
                                                                            args.getOrDefault(
                                                                                    "offset", 0))
                                                                    .longValue(),
                                                            ((Number)
                                                                            args.getOrDefault(
                                                                                    "limit", 4096))
                                                                    .intValue());
                                            case "write" ->
                                                    terminal.write(
                                                            id,
                                                            (String) args.get("text"),
                                                            invocation.cancellation());
                                            case "resize" ->
                                                    terminal.resize(
                                                            id,
                                                            ((Number) args.get("columns"))
                                                                    .intValue(),
                                                            ((Number) args.get("rows")).intValue());
                                            case "stop" -> terminal.stop(id);
                                            default -> throw new IllegalStateException();
                                        };
                                var preview = new LinkedHashMap<String, Object>(value);
                                if (value.get("text") instanceof String text
                                        && text.length() > 2048) {
                                    preview.put("text", JsonEncoding.prefix(text, 2048));
                                    preview.put("modelPreviewTruncated", true);
                                    preview.put("readBackTool", "terminal_log");
                                    preview.put("readBackOffset", args.getOrDefault("offset", 0));
                                    preview.put(
                                            "readBackHint",
                                            "Use the same byte offset and a smaller limit");
                                }
                                return new ToolResult(
                                        call.callId(),
                                        ToolStatus.SUCCESS,
                                        value,
                                        JsonEncoding.text(preview, 16384, "TERMINAL_RESULT_LIMIT"),
                                        preview,
                                        Optional.empty());
                            } catch (HarnessException ex) {
                                return ToolResults.failure(
                                        call.callId(),
                                        ex.code().equals("TERMINAL_INPUT_UNKNOWN")
                                                ? ToolStatus.UNKNOWN
                                                : ex.code().equals("CANCELLED")
                                                        ? ToolStatus.CANCELLED
                                                        : ToolStatus.FAILED,
                                        ex.code());
                            }
                        }
                    };
            context.own(registry.register(tool, context.scope()));
        }
    }

    @Override
    public void stop() {}
}
