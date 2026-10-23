package io.jharness.api;

import java.io.IOException;
import java.util.List;

/** Host-bound disposable content index. Callers supply only an authorized session identifier. */
public interface ContentSearchProvider {
    record Hit(
            String eventId,
            String type,
            String timestamp,
            String turnId,
            String snippet,
            int score) {
        public Hit {
            eventId = Values.required(eventId, "eventId");
            type = Values.required(type, "type");
            timestamp = Values.required(timestamp, "timestamp");
            turnId = turnId == null ? "" : turnId;
            java.util.Objects.requireNonNull(snippet, "snippet");
            if (snippet.length() > 400 || score < 0)
                throw new IllegalArgumentException("Invalid search hit bounds");
        }
    }

    record Result(List<Hit> hits, int indexedEvents, int reusedEvents, boolean rebuilt) {
        public Result {
            hits = List.copyOf(hits);
            if (hits.size() > 4096
                    || indexedEvents < 0
                    || reusedEvents < 0
                    || reusedEvents > indexedEvents)
                throw new IllegalArgumentException("Invalid search result bounds");
        }
    }

    Result search(
            String sessionId,
            String text,
            String mode,
            int limit,
            boolean rebuild,
            CancellationToken cancellation)
            throws IOException;
}
