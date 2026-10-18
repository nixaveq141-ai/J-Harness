package io.jharness.llm;

import static io.jharness.llm.ProtocolCodec.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Typed stream state; executable calls are released only after a valid terminal event and EOF. */
final class ProtocolStream {
    private final ModelProtocol protocol;
    private final ObjectMapper json;
    private final StringBuilder preview = new StringBuilder();
    private final List<Map<String, Object>> blocks = new ArrayList<>();
    private final Map<String, Object> usage = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> started = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> ended = new LinkedHashMap<>();
    private Map<String, Object> result;
    private String id, stop;
    private long sequence = -1;
    private Integer open;
    private StringBuilder arguments;
    private boolean terminal, messageDelta;

    ProtocolStream(ModelProtocol protocol, ObjectMapper json) {
        this.protocol = protocol;
        this.json = json;
    }

    String preview() {
        return preview.toString();
    }

    Map<String, Object> finish() {
        if (!terminal || result == null) throw error("MODEL_STREAM_INCOMPLETE");
        return result;
    }

    void frame(Map<String, Object> frame) {
        if (terminal) throw error("MODEL_BAD_RESPONSE");
        if (protocol == ModelProtocol.RESPONSES) responses(frame);
        else anthropic(frame);
    }

    private void responses(Map<String, Object> frame) {
        String type = text(frame, "type");
        if (frame.get("sequence_number") != null) {
            Object n = frame.get("sequence_number");
            if (!(n instanceof Integer || n instanceof Long)
                    || ((Number) n).longValue() <= sequence) throw error("MODEL_BAD_RESPONSE");
            sequence = ((Number) n).longValue();
        }
        if (type.equals("response.created")) {
            if (id != null) throw error("MODEL_BAD_RESPONSE");
            id = text(obj(frame.get("response")), "id");
            return;
        }
        if (id == null) throw error("MODEL_BAD_RESPONSE");
        switch (type) {
            case "response.completed" -> {
                var response = obj(frame.get("response"));
                if (!id.equals(response.get("id"))) throw error("MODEL_BAD_RESPONSE");
                var output = list(response.get("output"));
                if (!started.keySet().equals(ended.keySet()))
                    throw error("MODEL_STREAM_INCOMPLETE");
                for (var entry : ended.entrySet()) {
                    if (entry.getKey() >= output.size()
                            || !entry.getValue().equals(obj(output.get(entry.getKey()))))
                        throw error("MODEL_BAD_RESPONSE");
                }
                result = response;
                terminal = true;
            }
            case "response.output_item.added" -> {
                int index = index(frame);
                if (index != started.size()
                        || started.putIfAbsent(index, obj(frame.get("item"))) != null)
                    throw error("MODEL_BAD_RESPONSE");
            }
            case "response.output_item.done" -> {
                int index = index(frame);
                var item = obj(frame.get("item"));
                if (!started.containsKey(index)
                        || !Objects.equals(started.get(index).get("id"), item.get("id"))
                        || ended.putIfAbsent(index, item) != null)
                    throw error("MODEL_BAD_RESPONSE");
            }
            case "response.output_text.delta" -> {
                activeItem(frame);
                preview.append(string(frame, "delta"));
            }
            case "response.function_call_arguments.delta",
                            "response.function_call_arguments.done",
                            "response.output_text.done",
                            "response.content_part.added",
                            "response.content_part.done",
                            "response.reasoning_summary_part.added",
                            "response.reasoning_summary_part.done",
                            "response.reasoning_summary_text.delta",
                            "response.reasoning_summary_text.done" ->
                    activeItem(frame);
            case "response.in_progress" -> {
                if (!id.equals(obj(frame.get("response")).get("id")))
                    throw error("MODEL_BAD_RESPONSE");
            }
            case "error", "response.failed", "response.incomplete" ->
                    throw error("MODEL_INCOMPLETE");
            default -> throw error("UNSUPPORTED_MESSAGE");
        }
    }

    private void activeItem(Map<String, Object> frame) {
        int index = index(frame);
        if (!started.containsKey(index)
                || ended.containsKey(index)
                || !Objects.equals(started.get(index).get("id"), frame.get("item_id")))
            throw error("MODEL_BAD_RESPONSE");
    }

    private static int index(Map<String, Object> frame) {
        Object raw = frame.get("output_index");
        if (!(raw instanceof Integer i) || i < 0 || i >= 64) throw error("MODEL_BAD_RESPONSE");
        return i;
    }

    private void anthropic(Map<String, Object> frame) {
        String type = text(frame, "type");
        if (type.equals("ping")) return;
        if (type.equals("message_start")) {
            if (id != null) throw error("MODEL_BAD_RESPONSE");
            var message = obj(frame.get("message"));
            id = text(message, "id");
            if (!"message".equals(message.get("type"))
                    || !"assistant".equals(message.get("role"))
                    || !list(message.get("content")).isEmpty()) throw error("MODEL_BAD_RESPONSE");
            if (message.get("usage") != null) usage.putAll(obj(message.get("usage")));
            result = new LinkedHashMap<>(message);
            return;
        }
        if (id == null) throw error("MODEL_BAD_RESPONSE");
        switch (type) {
            case "content_block_start" -> {
                int index = blockIndex(frame);
                if (messageDelta || open != null || index != blocks.size() || index >= 64)
                    throw error("MODEL_BAD_RESPONSE");
                var block = new LinkedHashMap<>(obj(frame.get("content_block")));
                String kind = text(block, "type");
                if (!Set.of("text", "tool_use").contains(kind)) throw error("UNSUPPORTED_MESSAGE");
                if (kind.equals("text")) preview.append(string(block, "text"));
                else {
                    text(block, "id");
                    text(block, "name");
                    if (!obj(block.get("input")).isEmpty()) throw error("MODEL_BAD_RESPONSE");
                }
                blocks.add(block);
                open = index;
                arguments = new StringBuilder();
            }
            case "content_block_delta" -> {
                if (open == null || blockIndex(frame) != open || messageDelta)
                    throw error("MODEL_BAD_RESPONSE");
                var block = blocks.get(open);
                var delta = obj(frame.get("delta"));
                if ("text".equals(block.get("type")) && "text_delta".equals(delta.get("type"))) {
                    String fragment = string(delta, "text");
                    preview.append(fragment);
                    block.put("text", string(block, "text") + fragment);
                } else if ("tool_use".equals(block.get("type"))
                        && "input_json_delta".equals(delta.get("type")))
                    arguments.append(string(delta, "partial_json"));
                else throw error("UNSUPPORTED_MESSAGE");
            }
            case "content_block_stop" -> {
                if (open == null || blockIndex(frame) != open) throw error("MODEL_BAD_RESPONSE");
                var block = blocks.get(open);
                if ("tool_use".equals(block.get("type"))) {
                    try {
                        block.put(
                                "input",
                                obj(
                                        json.readValue(
                                                arguments.isEmpty() ? "{}" : arguments.toString(),
                                                Object.class)));
                    } catch (java.io.IOException bad) {
                        throw error("MODEL_BAD_RESPONSE");
                    }
                }
                open = null;
            }
            case "message_delta" -> {
                if (open != null || messageDelta) throw error("MODEL_BAD_RESPONSE");
                stop = text(obj(frame.get("delta")), "stop_reason");
                if (frame.get("usage") != null) usage.putAll(obj(frame.get("usage")));
                messageDelta = true;
            }
            case "message_stop" -> {
                if (open != null || !messageDelta) throw error("MODEL_STREAM_INCOMPLETE");
                result.put("content", blocks);
                result.put("usage", usage);
                result.put("stop_reason", stop);
                terminal = true;
            }
            case "error" -> throw error("MODEL_INCOMPLETE");
            default -> throw error("UNSUPPORTED_MESSAGE");
        }
    }

    private static int blockIndex(Map<String, Object> frame) {
        if (!(frame.get("index") instanceof Integer i) || i < 0) throw error("MODEL_BAD_RESPONSE");
        return i;
    }
}
