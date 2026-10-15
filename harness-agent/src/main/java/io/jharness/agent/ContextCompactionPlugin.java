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

    @Override
    public void stop() {}
}
