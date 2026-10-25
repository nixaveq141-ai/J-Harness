package io.jharness.agent;

import io.jharness.api.*;
import java.util.*;

/** BEFORE_MODEL contribution; records compaction before the final request journal. */
public final class ContextCompactionPlugin implements HarnessPlugin {
    private final ContextCompactor compactor;
    private final SessionWriter writer;
    private final SessionReader reader;
    private final int characters;

    public ContextCompactionPlugin(
            ContextCompactor compactor,
            SessionWriter writer,
            SessionReader reader,
            int characters) {
        this.compactor = Objects.requireNonNull(compactor);
        this.writer = Objects.requireNonNull(writer);
        this.reader = Objects.requireNonNull(reader);
        if (characters < 1) throw new IllegalArgumentException("characters must be positive");
        this.characters = characters;
    }

    @Override
    public String id() {
        return "context-compaction";
    }

    @Override
    public String version() {
        return "1.6";
    }

    @Override
    public void start(PluginContext context) {
        context.register(ContextCompactor.class, "default", compactor);
        context.events()
                .intercept(
                        StandardExtensionPoints.BEFORE_MODEL,
                        (request, next) -> {
                            var result =
                                    context.services()
                                            .requireUnique(ContextCompactor.class, context.scope())
                                            .compact(request, characters);
                            validateCompaction(request, result);
                            if (!result.removedMessageIndices().isEmpty()) {
                                EventSupport.append(
                                        writer,
                                        context.events(),
                                        context.scope(),
                                        "context.compacted",
                                        EventLevel.SESSION,
                                        null,
                                        null,
                                        null,
                                        audit(request, result));
                                writer.flush();
                            }
                            return next.proceed(result.request());
                        },
                        100);
    }

    private Map<String, Object> audit(ChatRequest request, ContextCompactor.Compaction result) {
        var candidates = new HashMap<Map<String, Object>, List<String>>();
        for (var event : reader.events()) {
            Map<String, Object> message = null;
            if (Set.of("user.message", "assistant.message", "system.message", "context.injected")
                    .contains(event.type())) message = event.payload();
            else if (event.type().equals("tool.completed")
                    && event.callId() != null
                    && event.payload().get("content") instanceof String text)
                message = Map.of("role", "tool", "tool_call_id", event.callId(), "content", text);
            else if (event.type().equals("sdk.input.applied")
                    && event.payload().get("userMessage") instanceof Map<?, ?>)
                message = Values.object(event.payload().get("userMessage"));
            else if (event.type().equals("context.compacted")
                    && event.payload().get("summary") instanceof String summary
                    && !summary.isEmpty()) message = Map.of("role", "user", "content", summary);
            if (message != null) {
                var ids = candidates.computeIfAbsent(message, ignored -> new ArrayList<>());
                if (ids.size() < 2) ids.add(event.eventId());
            }
        }
        var sources = new LinkedHashSet<String>();
        var associations = new ArrayList<Map<String, Object>>();
        int unmapped = 0;
        for (int index : result.removedMessageIndices()) {
            var matches = candidates.getOrDefault(request.messages().get(index), List.of());
            if (matches.size() != 1) {
                unmapped++;
                continue;
            }
            sources.add(matches.getFirst());
            if (associations.size() < 256)
                associations.add(Map.of("messageIndex", index, "eventId", matches.getFirst()));
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("strategy", compactor.getClass().getSimpleName());
        out.put("removedMessageIndices", result.removedMessageIndices());
        out.put("summary", result.summary());
        out.put("beforeCharacters", ExtractiveContextCompactor.characters(request));
        out.put("afterCharacters", ExtractiveContextCompactor.characters(result.request()));
        out.put("measurement", "messages-and-tools display characters");
        out.put("sourceEvents", sources.stream().limit(256).toList());
        out.put("sourceEventCount", sources.size());
        out.put("sourceAssociations", associations);
        out.put(
                "sourceMapping",
                "unique exact message values only; ambiguous or synthetic sources remain unmapped");
        out.put("unmappedMessages", unmapped);
        out.put(
                "sourceEventsTruncated",
                sources.size() > 256 || result.removedMessageIndices().size() - unmapped > 256);
        return out;
    }

    static void validateCompaction(ChatRequest request, ContextCompactor.Compaction result) {
        if (result == null
                || !request.tools().equals(result.request().tools())
                || !request.parameters().equals(result.request().parameters()))
            throw new HarnessException(
                    "INVALID_COMPACTION", "Compactor changed request configuration");
        var removed = new HashSet<Integer>();
        for (int index : result.removedMessageIndices())
            if (index < 0 || index >= request.messages().size() || !removed.add(index))
                throw new HarnessException("INVALID_COMPACTION", "Invalid original message index");
        validateGroups(request.messages(), removed);
        int latestUser = -1, latestAssistant = -1;
        for (int i = 0; i < request.messages().size(); i++) {
            var message = request.messages().get(i);
            if ("user".equals(message.get("role"))) latestUser = i;
            if ("assistant".equals(message.get("role"))) latestAssistant = i;
            boolean protectedMessage = "system".equals(message.get("role"));
            if (protectedMessage
                    && (removed.contains(i) || !result.request().messages().contains(message)))
                throw new HarnessException(
                        "INVALID_COMPACTION", "Protected protocol message removed");
        }
        if (latestUser >= 0
                && (removed.contains(latestUser)
                        || !result.request()
                                .messages()
                                .contains(request.messages().get(latestUser))))
            throw new HarnessException("INVALID_COMPACTION", "Latest user message removed");
        if (latestAssistant >= 0 && removed.contains(latestAssistant))
            throw new HarnessException(
                    "INVALID_COMPACTION", "Latest assistant protocol group removed");
        var retained = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < request.messages().size(); i++)
            if (!removed.contains(i)) retained.add(request.messages().get(i));
        var actual = result.request().messages();
        boolean identical = retained.equals(actual);
        if (!identical && !removed.isEmpty() && !result.summary().isBlank()) {
            var summary = Map.<String, Object>of("role", "user", "content", result.summary());
            for (int i = 0; i < actual.size(); i++) {
                if (!summary.equals(actual.get(i))) continue;
                var withoutSummary = new ArrayList<>(actual);
                withoutSummary.remove(i);
                if (retained.equals(withoutSummary)) {
                    identical = true;
                    break;
                }
            }
        }
        if (!identical) throw invalidCompaction();
        validateGroups(actual, Set.of());
    }

    private static HarnessException invalidCompaction() {
        return new HarnessException(
                "INVALID_COMPACTION", "Inconsistent compaction protocol or audit");
    }

    private static void validateGroups(List<Map<String, Object>> messages, Set<Integer> removed) {
        for (int i = 0; i < messages.size(); i++) {
            var message = messages.get(i);
            if ("tool".equals(message.get("role"))) throw invalidCompaction();
            if (!message.containsKey("tool_calls")) continue;
            if (!"assistant".equals(message.get("role"))
                    || !(message.get("tool_calls") instanceof List<?> calls)
                    || calls.isEmpty()) throw invalidCompaction();
            var pending = new HashSet<String>();
            for (Object raw : calls) {
                if (!(raw instanceof Map<?, ?> call)
                        || !(call.get("id") instanceof String id)
                        || id.isBlank()
                        || !pending.add(id)) throw invalidCompaction();
            }
            boolean drop = removed.contains(i);
            while (i + 1 < messages.size() && "tool".equals(messages.get(i + 1).get("role"))) {
                i++;
                if (!pending.remove(messages.get(i).get("tool_call_id"))
                        || drop != removed.contains(i)) throw invalidCompaction();
            }
            if (!pending.isEmpty() && (drop || i + 1 < messages.size())) throw invalidCompaction();
        }
    }

    @Override
    public void stop() {}
}
