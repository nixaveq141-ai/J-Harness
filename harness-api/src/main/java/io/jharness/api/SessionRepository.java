package io.jharness.api;

import java.util.List;

public interface SessionRepository {
    SessionWriter openWriter(String sessionId, WriteOwnership ownership);

    SessionReader openReader(String sessionId);

    List<String> list();

    String fork(String sessionId, String fromEventId);
}
