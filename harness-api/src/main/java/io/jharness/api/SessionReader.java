package io.jharness.api;

import java.util.List;

public interface SessionReader {
    List<SessionEvent> events();

    default List<SessionEvent> query(SessionQuery query) {
        return query.apply(events());
    }
}
