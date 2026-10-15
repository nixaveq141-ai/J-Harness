package io.jharness.testkit;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Call the concrete bus's notification barrier before asserting its snapshot. */
public final class EventRecorder implements AutoCloseable {
    private final List<SessionEvent> events = new CopyOnWriteArrayList<>();
    private final Registration subscription;

    public EventRecorder(EventBus bus) {
        subscription = bus.notify("*", events::add);
    }

    public List<SessionEvent> events() {
        return List.copyOf(events);
    }

    public List<String> types() {
        return events.stream().map(SessionEvent::type).toList();
    }

    @Override
    public void close() {
        subscription.close();
    }
}
