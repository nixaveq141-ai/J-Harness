package io.jharness.api;

import java.util.*;

/** Replaceable context compaction; keep unresolved tool protocol groups intact. */
public interface ContextCompactor {
    Compaction compact(ChatRequest request, int maxCharacters);

    record Compaction(ChatRequest request, List<Integer> removedMessageIndices, String summary) {
        public Compaction {
            Objects.requireNonNull(request);
            removedMessageIndices = List.copyOf(removedMessageIndices);
            Objects.requireNonNull(summary);
        }
    }
}
