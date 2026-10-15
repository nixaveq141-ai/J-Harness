package io.jharness.agent;

import io.jharness.api.*;
import java.util.*;

/** Extractive summaries; completed assistant/tool groups are removed atomically. */
public final class ExtractiveContextCompactor implements ContextCompactor {
    @Override
    public Compaction compact(ChatRequest request, int maxCharacters) {
        if (size(request) <= maxCharacters) return new Compaction(request, List.of(), "");
        var messages = request.messages();
        int latestUser = -1;
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            var message = messages.get(i);
            if ("user".equals(message.get("role"))) latestUser = i;
            List<Integer> group = new ArrayList<>();
            group.add(i);
            if (message.get("tool_calls") instanceof List<?> calls) {
                Set<String> pending = new HashSet<>();
                for (Object raw : calls) {
                    Object id = Values.object(raw).get("id");
                    if (!(id instanceof String text) || !pending.add(text)) throw limit();
                }
                while (!pending.isEmpty()) {
                    if (++i >= messages.size()) throw limit();
                    var result = messages.get(i);
                    if (!"tool".equals(result.get("role"))
                            || !pending.remove(result.get("tool_call_id"))) throw limit();
                    group.add(i);
                }
            } else if ("tool".equals(message.get("role"))) throw limit();
            groups.add(List.copyOf(group));
        }
        if (latestUser < 0) throw limit();
        Set<Integer> removed = new LinkedHashSet<>();
        StringBuilder extract =
                new StringBuilder(
                        "Earlier context extract; partial quotes, not proof of completion:\n");
        ChatRequest compacted = request;
        String summary = "";
        for (int g = 0; g < groups.size() - 1; g++) {
            var group = groups.get(g);
            if (group.contains(latestUser)
                    || group.stream().anyMatch(i -> "system".equals(messages.get(i).get("role"))))
                continue;
            removed.addAll(group);
            for (int index : group)
                if (extract.length() < 512) {
                    var message = messages.get(index);
                    String content = String.valueOf(message.getOrDefault("content", ""));
                    extract.append(message.get("role"))
                            .append(": ")
                            .append(content, 0, Math.min(content.length(), 60))
                            .append('\n');
                }
            summary = extract.toString();
            List<Map<String, Object>> retained = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                if (i == latestUser) retained.add(Map.of("role", "user", "content", summary));
                if (!removed.contains(i)) retained.add(messages.get(i));
            }
            compacted = new ChatRequest(retained, request.tools(), request.parameters());
            if (size(compacted) <= maxCharacters)
                return new Compaction(compacted, List.copyOf(removed), summary);
        }
        throw limit();
    }

    private static long size(ChatRequest request) {
        return (long) request.messages().toString().length() + request.tools().toString().length();
    }

    private static HarnessException limit() {
        return new HarnessException(
                "CONTEXT_LIMIT", "Protected or unresolved context exceeds compaction budget");
    }
}
