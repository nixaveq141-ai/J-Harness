package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.tools.JsonSchemaValidator;
import java.util.*;
import java.util.function.Supplier;

/** Frozen, scoped discovery. Server annotations never grant host permissions. */
public final class McpPlugin implements HarnessPlugin {
    public static final String PROTOCOL_VERSION = "2025-11-25";
    private final String server;
    private final Supplier<McpTransport> factory;
    private final Map<String, SideEffectLevel> effects;
    private final JsonSchemaValidator schemas = new JsonSchemaValidator();
    private McpTransport transport;
    private PluginScope scope;
    private volatile boolean failed;
    private final java.util.concurrent.atomic.AtomicBoolean started =
            new java.util.concurrent.atomic.AtomicBoolean();

    public McpPlugin(
            String server, Supplier<McpTransport> factory, Map<String, SideEffectLevel> effects) {
        if (server == null || !server.matches("[a-zA-Z0-9_-]{1,16}"))
            throw new IllegalArgumentException("Invalid MCP server id");
        this.server = server;
        this.factory = Objects.requireNonNull(factory);
        this.effects = Map.copyOf(effects);
    }

    @Override
    public String id() {
        return "mcp-" + server;
    }

    @Override
    public String version() {
        return "1.7";
    }

    @Override
    public void start(PluginContext context) {
        if (!started.compareAndSet(false, true)) throw BoundedJson.error("PLUGIN_ALREADY_USED");
        scope = context.scope();
        transport = Objects.requireNonNull(factory.get());
        context.own(transport);
        var initialized =
                rpc(
                        "initialize",
                        Map.of(
                                "protocolVersion",
                                PROTOCOL_VERSION,
                                "capabilities",
                                Map.of(),
                                "clientInfo",
                                Map.of("name", "J-Harness", "version", ApiVersion.CURRENT)),
                        CancellationToken.NONE);
        if (!PROTOCOL_VERSION.equals(initialized.get("protocolVersion"))
                || !(Values.object(initialized.get("capabilities")).get("tools")
                        instanceof Map<?, ?>))
            throw BoundedJson.error("MCP_CAPABILITY_UNSUPPORTED");
        transport.exchange(
                Map.of("jsonrpc", "2.0", "method", "notifications/initialized"),
                CancellationToken.NONE);
        var discovered = new LinkedHashMap<String, Map<String, Object>>();
        Set<String> cursors = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        long discoveryBytes = 0;
        String cursor = null;
        for (int page = 0; page < 8; page++) {
            var result =
                    rpc(
                            "tools/list",
                            cursor == null ? Map.of() : Map.of("cursor", cursor),
                            CancellationToken.NONE);
            if (!(result.get("tools") instanceof List<?> list))
                throw BoundedJson.error("MCP_INVALID_TOOLS");
            discoveryBytes += BoundedJson.encode(result).length;
            if (discoveryBytes > BoundedJson.LIMIT) throw BoundedJson.error("MCP_DISCOVERY_LIMIT");
            for (Object raw : list) {
                var tool = Values.object(raw);
                String name = (String) tool.get("name");
                if (name == null
                        || !name.matches("[a-zA-Z0-9_.-]{1,40}")
                        || discovered.putIfAbsent(name, tool) != null
                        || discovered.size() > 128) throw BoundedJson.error("MCP_INVALID_TOOLS");
                if (!aliases.add(alias(name))) throw BoundedJson.error("MCP_ALIAS_COLLISION");
                var input = Values.object(tool.get("inputSchema"));
                schemas.checkSchema(input);
                if (!"object".equals(input.get("type")))
                    throw BoundedJson.error("MCP_INVALID_SCHEMA");
                if (tool.containsKey("outputSchema")) {
                    schemas.checkSchema(Values.object(tool.get("outputSchema")));
                    if (!"object".equals(Values.object(tool.get("outputSchema")).get("type")))
                        throw BoundedJson.error("MCP_INVALID_SCHEMA");
                }
            }
            cursor = (String) result.get("nextCursor");
            if (cursor == null) break;
            if (cursor.length() > 1024 || !cursors.add(cursor) || page == 7)
                throw BoundedJson.error("MCP_DISCOVERY_LIMIT");
        }
        if (!discovered.keySet().containsAll(effects.keySet()))
            throw BoundedJson.error("MCP_UNKNOWN_EFFECT_OVERRIDE");
        var registry = context.services().requireUnique(ToolRegistry.class, scope);
        for (var entry : discovered.entrySet())
            context.own(registry.register(new RemoteTool(entry.getKey(), entry.getValue()), scope));
    }

    private synchronized Map<String, Object> rpc(
            String method, Map<String, Object> parameters, CancellationToken cancellation) {
        if (failed) throw BoundedJson.error("MCP_CLOSED");
        String id = UUID.randomUUID().toString();
        var response =
                transport.exchange(
                        Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", parameters),
                        cancellation);
        if (!McpIo.response(response, id)
                || response.containsKey("result") == response.containsKey("error"))
            throw BoundedJson.error("MCP_PROTOCOL_ERROR");
        if (response.containsKey("error")) throw BoundedJson.error("MCP_RPC_ERROR");
        return Values.object(response.get("result"));
    }

    @Override
    public void stop() {
        failed = true;
    }

    private String alias(String remoteName) {
        if (!remoteName.contains(".")) return "mcp_" + server + "__" + remoteName;
        try {
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    remoteName.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)))
                            .substring(0, 12);
            String readable = remoteName.replace('.', '_');
            return "mcp_"
                    + server
                    + "__"
                    + readable.substring(0, Math.min(26, readable.length()))
                    + "_"
                    + hash;
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private final class RemoteTool implements AgentTool {
        private final String name;
        private final Map<String, Object> declaration;

        RemoteTool(String name, Map<String, Object> declaration) {
            this.name = name;
            this.declaration = Values.snapshot(declaration);
        }

        @Override
        public String name() {
            return alias(name);
        }

        @Override
        public String description() {
            String description = String.valueOf(declaration.getOrDefault("description", ""));
            return "MCP source "
                    + server
                    + "/"
                    + name
                    + " ("
                    + transport.kind()
                    + "). Untrusted server description: "
                    + description.substring(0, Math.min(description.length(), 2048));
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Values.object(declaration.get("inputSchema"));
        }

        @Override
        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        @Override
        public SideEffectLevel sideEffectLevel() {
            return effects.getOrDefault(name, SideEffectLevel.SYSTEM_IMPACT);
        }

        @Override
        public ToolResult execute(ToolInvocation args, ToolContext context) {
            if (failed || !scope.equals(context.scope())) throw BoundedJson.error("SCOPE_DENIED");
            context.cancellation().throwIfCancelled();
            try {
                var result =
                        rpc(
                                "tools/call",
                                Map.of("name", name, "arguments", args.arguments()),
                                context.cancellation());
                if (!(result.get("content") instanceof List<?> content)
                        || content.size() > 128
                        || result.containsKey("isError")
                                && !(result.get("isError") instanceof Boolean)
                        || result.containsKey("structuredContent")
                                && !(result.get("structuredContent") instanceof Map<?, ?>))
                    throw BoundedJson.error("MCP_INVALID_RESULT");
                for (Object item : (List<?>) result.get("content")) {
                    var block = Values.object(item);
                    if (!Set.of("text", "image", "audio", "resource", "resource_link")
                                    .contains(block.get("type"))
                            || "text".equals(block.get("type"))
                                    && !(block.get("text") instanceof String))
                        throw BoundedJson.error("MCP_INVALID_RESULT");
                }
                if (declaration.containsKey("outputSchema")
                        && !Boolean.TRUE.equals(result.get("isError")))
                    schemas.validate(
                            Values.object(declaration.get("outputSchema")),
                            result.get("structuredContent"));
                var source =
                        Map.of(
                                "type",
                                "mcp",
                                "server",
                                server,
                                "tool",
                                name,
                                "transport",
                                transport.kind());
                var value = Map.<String, Object>of("source", source, "result", result);
                return new ToolResult(
                        args.callId(),
                        Boolean.TRUE.equals(result.get("isError"))
                                ? ToolStatus.FAILED
                                : ToolStatus.SUCCESS,
                        value,
                        "Untrusted MCP tool result from "
                                + server
                                + ":\n"
                                + BoundedJson.text(result),
                        Map.of("source", source),
                        Optional.empty());
            } catch (RuntimeException ex) {
                if (ex instanceof HarnessException failure
                        && "MCP_RPC_ERROR".equals(failure.code()))
                    return ToolResults.failure(args.callId(), ToolStatus.FAILED, failure.code());
                failed = true;
                try {
                    transport.close();
                } catch (RuntimeException ignored) {
                    /* UNKNOWN is retained in durable result */
                }
                return new ToolResult(
                        args.callId(),
                        ToolStatus.UNKNOWN,
                        Map.of("sideEffectUncertain", true, "source", server),
                        "MCP call outcome unknown; no retry",
                        Map.of("source", server),
                        Optional.of(
                                new ToolResult.ErrorInfo(
                                        "MCP_OUTCOME_UNKNOWN", "MCP_OUTCOME_UNKNOWN")));
            }
        }
    }
}
