package io.jharness.session;

import io.jharness.api.*;
import java.util.*;

/** Archival changes visibility/continuation, never removes facts or settles unknown effects. */
public final class SessionLifecycle {
    private SessionLifecycle() {}

    public static boolean archived(List<SessionEvent> events) {
        boolean archived = false;
        for (var e : events) {
            if (e.type().equals("session.archived")) archived = true;
            if (e.type().equals("session.restored")) archived = false;
        }
        return archived;
    }

    public static void setArchived(
            SessionRepository repository, String id, boolean archived, DataSanitizer sanitizer) {
        repository.openReader(id).events(); // Existence and validity before acquiring a writer.
        try (var writer =
                new SanitizingSessionWriter(
                        repository.openWriter(id, WriteOwnership.EXCLUSIVE), sanitizer)) {
            var history = repository.openReader(id).events();
            if (!SessionRecovery.inspect(history).ready())
                throw new HarnessException(
                        "UNKNOWN_SIDE_EFFECT", "Inspect unresolved facts before archival changes");
            if (archived(history) == archived) return;
            writer.append(
                    SessionEvent.session(
                            id,
                            archived ? "session.archived" : "session.restored",
                            Map.of(
                                    "actor",
                                    "operator",
                                    "reason",
                                    "Explicit session visibility change; all facts and artifacts retained")));
            writer.flush();
        }
    }
}
