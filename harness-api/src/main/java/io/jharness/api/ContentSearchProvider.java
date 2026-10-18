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
            int score) {}

    record Result(List<Hit> hits, int indexedEvents, int reusedEvents, boolean rebuilt) {
        public Result {
            hits = List.copyOf(hits);
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
