package io.jharness.api;

import java.util.*;

/**
 * Immutable message content with host-only provenance. Provenance is not a map entry: JSON,
 * equality, hashing and display-character accounting see only the ordinary message content. A
 * request.context snapshot uses its exact message index; -1 refers to a direct/derived fact.
 */
public final class SourcedMessage extends AbstractMap<String, Object> {
    private final Map<String, Object> content;
    private final String eventId;
    private final int messageIndex;

    public SourcedMessage(Map<String, Object> content, String eventId) {
        this(content, eventId, -1);
    }

    public SourcedMessage(Map<String, Object> content, String eventId, int messageIndex) {
        this.content = Values.snapshot(content);
        this.eventId = Values.required(eventId, "source event ID");
        if (messageIndex < -1) throw new IllegalArgumentException("source message index");
        this.messageIndex = messageIndex;
    }

    public String eventId() {
        return eventId;
    }

    public int messageIndex() {
        return messageIndex;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return content.entrySet();
    }
}
