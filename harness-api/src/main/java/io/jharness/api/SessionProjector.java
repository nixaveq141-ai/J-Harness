package io.jharness.api;

import java.util.List;

@FunctionalInterface
public interface SessionProjector {
    SessionProjection project(List<SessionEvent> events);
}
