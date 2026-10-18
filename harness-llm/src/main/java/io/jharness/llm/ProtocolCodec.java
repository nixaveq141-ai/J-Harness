package io.jharness.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import java.math.BigDecimal;
import java.util.*;

/** Text/function-only codecs. Shared transport and common Chat validation remain authoritative. */
final class ProtocolCodec {
    private final ModelProtocol protocol;
    private final String model;
    private final ObjectMapper json;
    private final String endpoint;

    ProtocolCodec(ModelProtocol protocol, String model, ObjectMapper json) {
        this(protocol, model, json, "");
    }

    ProtocolCodec(ModelProtocol protocol, String model, ObjectMapper json, String endpoint) {
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.model = model;
        this.json = json;
    }

    Map<String, Object> encode(Map<String, Object> chat, ChatRequest request) {
        var result = new LinkedHashMap<>(chat);
        result.remove("protocol");
        if (protocol == ModelProtocol.CHAT_COMPLETIONS) return result;
        result.remove("stream_options");
        result.remove("messages");
        result.remove("tools");
        if (result.containsKey("seed") || result.containsKey("response_format"))
            throw error("UNSUPPORTED_PARAMETER");
        if (protocol == ModelProtocol.RESPONSES) {
            if (result.containsKey("thinking") || result.containsKey("stop"))
                throw error("UNSUPPORTED_PARAMETER");
            move(result, "max_tokens", "max_output_tokens");
            Object effort = result.remove("reasoning_effort");
            if (effort != null) result.put("reasoning", Map.of("effort", effort));
            result.put("store", false);
            result.put("include", List.of("reasoning.encrypted_content"));
            var input = new ArrayList<Map<String, Object>>();
            var messages = list(chat.get("messages"));
            for (int i = 0; i < messages.size(); i++) {
                var message = obj(messages.get(i));
                if ("assistant".equals(message.get("role"))) {
                    var state =
                            obj(request.messages().get(i).getOrDefault("provider_state", Map.of()));
                    if (!state.isEmpty()
                            && protocol.id().equals(state.get("protocol"))
                            && model.equals(state.get("model"))
                            && endpoint.equals(state.get("endpoint"))) {
                        for (var raw : list(state.get("items"))) input.add(reasoning(obj(raw)));
                    }
                }
                if ("tool".equals(message.get("role"))) {
                    input.add(
                            Map.of(
                                    "type",
                                    "function_call_output",
                                    "call_id",
                                    text(message, "tool_call_id"),
                                    "output",
                                    Objects.toString(message.get("content"), "")));
                } else {
                    String content = Objects.toString(message.get("content"), "");
                    if (!content.isEmpty())
                        input.add(Map.of("role", message.get("role"), "content", content));
                    for (var raw : list(message.getOrDefault("tool_calls", List.of()))) {
                        var call = obj(raw);
                        var function = obj(call.get("function"));
                        input.add(
                                Map.of(
                                        "type",
                                        "function_call",
                                        "call_id",
                                        text(call, "id"),
                                        "name",
                                        text(function, "name"),
                                        "arguments",
                                        text(function, "arguments")));
                    }
                }
            }
            result.put("input", input);
            if (!request.tools().isEmpty())
                result.put(
                        "tools",
                        request.tools().stream()
                                .map(
                                        raw -> {
                                            var function = obj(raw.get("function"));
                                            var tool = new LinkedHashMap<>(function);
                                            tool.put("type", "function");
                                            tool.putIfAbsent("strict", false);
                                            return tool;
                                        })
                                .toList());
        } else {
            if (result.containsKey("reasoning_effort")) throw error("UNSUPPORTED_REASONING");
            if (result.remove("thinking") != null)
                result.put("thinking", Map.of("type", "disabled"));
            move(result, "stop", "stop_sequences");
            if (!result.containsKey("max_tokens")) throw error("UNSUPPORTED_PARAMETER");
            var messages = new ArrayList<Map<String, Object>>();
            var system = new ArrayList<String>();
            for (var raw : list(chat.get("messages"))) {
                var message = obj(raw);
                String role = text(message, "role");
                String content = Objects.toString(message.get("content"), "");
                if (role.equals("system")) {
                    if (!messages.isEmpty()) throw error("UNSUPPORTED_MESSAGE");
                    system.add(content);
                    continue;
                }
                var blocks = new ArrayList<Map<String, Object>>();
                if (role.equals("tool")) {
                    role = "user";
                    blocks.add(
                            Map.of(
                                    "type",
                                    "tool_result",
                                    "tool_use_id",
                                    text(message, "tool_call_id"),
                                    "content",
                                    content));
                } else {
                    if (!content.isEmpty()) blocks.add(Map.of("type", "text", "text", content));
                    for (var callRaw : list(message.getOrDefault("tool_calls", List.of()))) {
                        var call = obj(callRaw);
                        var function = obj(call.get("function"));
                        blocks.add(
                                Map.of(
                                        "type",
                                        "tool_use",
                                        "id",
                                        text(call, "id"),
                                        "name",
                                        text(function, "name"),
                                        "input",
                                        parseObject(text(function, "arguments"))));
                    }
                }
                if (blocks.isEmpty()) throw error("UNSUPPORTED_MESSAGE");
                if (!messages.isEmpty() && role.equals(messages.getLast().get("role"))) {
                    var merged = new ArrayList<Object>(list(messages.getLast().get("content")));
                    merged.addAll(blocks);
                    messages.set(messages.size() - 1, Map.of("role", role, "content", merged));
                } else messages.add(Map.of("role", role, "content", blocks));
            }
            if (!system.isEmpty()) result.put("system", String.join("\n\n", system));
            result.put("messages", messages);
            if (!request.tools().isEmpty())
                result.put(
                        "tools",
                        request.tools().stream()
                                .map(
                                        raw -> {
                                            var function = obj(raw.get("function"));
                                            return Map.of(
                                                    "name",
                                                    text(function, "name"),
                                                    "description",
                                                    Objects.toString(
                                                            function.get("description"), ""),
                                                    "input_schema",
                                                    obj(function.get("parameters")));
                                        })
                                .toList());
            if (result.containsKey("tool_choice")) {
                Object choice = result.get("tool_choice");
                if (!(choice instanceof String value)
                        || !Set.of("auto", "required", "none").contains(value))
                    throw error("UNSUPPORTED_PARAMETER");
                result.put(
                        "tool_choice", Map.of("type", choice.equals("required") ? "any" : choice));
            }
        }
        return result;
    }

    Map<String, Object> decode(Map<String, Object> response) {
        if (protocol == ModelProtocol.CHAT_COMPLETIONS) return response;
        var content = new StringBuilder();
        var calls = new ArrayList<Map<String, Object>>();
        var reasoning = new ArrayList<Map<String, Object>>();
        if (response.get("model") != null) text(response, "model");
        if (protocol == ModelProtocol.RESPONSES) {
            if (!"completed".equals(response.get("status"))
                    || response.get("error") != null
                    || response.get("incomplete_details") != null) throw error("MODEL_INCOMPLETE");
            for (var raw : list(response.get("output"))) {
                var item = obj(raw);
                if (item.get("status") != null && !"completed".equals(item.get("status")))
                    throw error("MODEL_INCOMPLETE");
                switch (text(item, "type")) {
                    case "message" -> {
                        if (!"assistant".equals(item.get("role")))
                            throw error("MODEL_BAD_RESPONSE");
                        for (var blockRaw : list(item.get("content"))) {
                            var block = obj(blockRaw);
                            if (!"output_text".equals(block.get("type")))
                                throw error("UNSUPPORTED_MESSAGE");
                            content.append(string(block, "text"));
                        }
                    }
                    case "function_call" ->
                            calls.add(
                                    call(
                                            text(item, "call_id"),
                                            text(item, "name"),
                                            text(item, "arguments")));
                    case "reasoning" -> reasoning.add(reasoning(item));
                    default -> throw error("UNSUPPORTED_MESSAGE");
                }
            }
        } else {
            if (!"message".equals(response.get("type"))
                    || !"assistant".equals(response.get("role"))) throw error("MODEL_BAD_RESPONSE");
            String stop = text(response, "stop_reason");
            if (!Set.of("end_turn", "stop_sequence", "tool_use").contains(stop))
                throw error("MODEL_INCOMPLETE");
            for (var raw : list(response.get("content"))) {
                var item = obj(raw);
                switch (text(item, "type")) {
                    case "text" -> content.append(string(item, "text"));
                    case "tool_use" ->
                            calls.add(
                                    call(
                                            text(item, "id"),
                                            text(item, "name"),
                                            stringify(obj(item.get("input")))));
                    case "thinking", "redacted_thinking" -> throw error("UNSUPPORTED_REASONING");
                    default -> throw error("UNSUPPORTED_MESSAGE");
                }
            }
            if (calls.isEmpty() == stop.equals("tool_use")) throw error("MODEL_INCOMPLETE");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put(
                "choices",
                List.of(
                        Map.of(
                                "finish_reason",
                                calls.isEmpty() ? "stop" : "tool_calls",
                                "message",
                                Map.of(
                                        "role",
                                        "assistant",
                                        "content",
                                        content.toString(),
                                        "tool_calls",
                                        calls))));
        result.put("usage", usage(response.get("usage")));
        if (!reasoning.isEmpty())
            result.put(
                    "provider_state",
                    Map.of(
                            "protocol",
                            protocol.id(),
                            "model",
                            model,
                            "endpoint",
                            endpoint,
                            "items",
                            reasoning));
        return result;
    }

    private Map<String, Object> reasoning(Map<String, Object> item) {
        if (!"reasoning".equals(item.get("type"))
                || item.get("content") != null && !list(item.get("content")).isEmpty())
            throw error("UNSUPPORTED_REASONING");
        var safe = new LinkedHashMap<String, Object>();
        safe.put("type", "reasoning");
        safe.put("id", text(item, "id"));
        safe.put("encrypted_content", text(item, "encrypted_content"));
        var summaries = list(item.getOrDefault("summary", List.of()));
        for (var raw : summaries) {
            var summary = obj(raw);
            if (!"summary_text".equals(summary.get("type"))) throw error("UNSUPPORTED_REASONING");
            string(summary, "text");
        }
        safe.put("summary", summaries);
        return safe;
    }

    private Map<String, Object> usage(Object raw) {
        if (raw == null) return Map.of();
        var usage = new LinkedHashMap<>(obj(raw));
        if (!usage.containsKey("input_tokens") || !usage.containsKey("output_tokens")) return usage;
        long input = number(usage, "input_tokens"), output = number(usage, "output_tokens");
        if (protocol == ModelProtocol.ANTHROPIC) {
            try {
                input =
                        Math.addExact(
                                input,
                                Math.addExact(
                                        number(usage, "cache_read_input_tokens"),
                                        number(usage, "cache_creation_input_tokens")));
            } catch (ArithmeticException overflow) {
                throw error("MODEL_BAD_RESPONSE");
            }
        }
        long total;
        try {
            total = Math.addExact(input, output);
        } catch (ArithmeticException overflow) {
            throw error("MODEL_BAD_RESPONSE");
        }
        usage.put("prompt_tokens", input);
        usage.put("completion_tokens", output);
        usage.put("total_tokens", total);
        return usage;
    }

    private static long number(Map<String, Object> value, String key) {
        if (!value.containsKey(key)) return 0;
        if (!(value.get(key) instanceof Number n)) throw error("MODEL_BAD_RESPONSE");
        try {
            long result = new BigDecimal(n.toString()).longValueExact();
            if (result < 0) throw error("MODEL_BAD_RESPONSE");
            return result;
        } catch (ArithmeticException bad) {
            throw error("MODEL_BAD_RESPONSE");
        }
    }

    private static void move(Map<String, Object> map, String from, String to) {
        if (map.containsKey(from)) map.put(to, map.remove(from));
    }

    private static Map<String, Object> call(String id, String name, String arguments) {
        return Map.of(
                "id",
                id,
                "type",
                "function",
                "function",
                Map.of("name", name, "arguments", arguments));
    }

    private Map<String, Object> parseObject(String text) {
        try {
            return obj(json.readValue(text, Object.class));
        } catch (JsonProcessingException bad) {
            throw error("MODEL_BAD_RESPONSE");
        }
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException bad) {
            throw error("MODEL_BAD_RESPONSE");
        }
    }

    static Map<String, Object> obj(Object value) {
        if (!(value instanceof Map<?, ?>)) throw error("MODEL_BAD_RESPONSE");
        return Values.object(value);
    }

    static List<?> list(Object value) {
        if (!(value instanceof List<?> values)) throw error("MODEL_BAD_RESPONSE");
        return values;
    }

    static String string(Map<String, Object> value, String key) {
        if (!(value.get(key) instanceof String text)) throw error("MODEL_BAD_RESPONSE");
        return text;
    }

    static String text(Map<String, Object> value, String key) {
        String text = string(value, key);
        if (text.isBlank()) throw error("MODEL_BAD_RESPONSE");
        return text;
    }

    static HarnessException error(String code) {
        return new HarnessException(code, code);
    }
}
