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
                                        Map.of(
                                                "strategy",
                                                compactor.getClass().getSimpleName(),
                                                "removedMessageIndices",
                                                result.removedMessageIndices(),
                                                "summary",
                                                result.summary(),
                                                "beforeCharacters",
                                                request.messages().toString().length(),
                                                "afterCharacters",
                                                result.request().messages().toString().length(),
                                                "sourceEvents",
                                                reader.events().stream()
                                                        .filter(
                                                                e ->
                                                                        Set.of(
                                                                                        "user.message",
                                                                                        "assistant.message",
                                                                                        "tool.completed",
                                                                                        "request.context")
                                                                                .contains(e.type()))
                                                        .map(SessionEvent::eventId)
                                                        .toList()));
                                writer.flush();
                            }
                            return next.proceed(result.request());
                        },
                        100);
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
        Set<String> pending = new HashSet<>();
        for (var message : request.messages()) {
            if (message.get("tool_calls") instanceof List<?> calls)
                for (Object raw : calls) pending.add((String) Values.object(raw).get("id"));
            if ("tool".equals(message.get("role"))) pending.remove(message.get("tool_call_id"));
        }
        int latestUser = -1;
        for (int i = 0; i < request.messages().size(); i++) {
            var message = request.messages().get(i);
            if ("user".equals(message.get("role"))) latestUser = i;
            boolean protectedMessage = "system".equals(message.get("role"));
            if (message.get("tool_calls") instanceof List<?> calls)
                protectedMessage |=
                        calls.stream()
                                .anyMatch(raw -> pending.contains(Values.object(raw).get("id")));
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
    }

    @Override
    public void stop() {}
}
