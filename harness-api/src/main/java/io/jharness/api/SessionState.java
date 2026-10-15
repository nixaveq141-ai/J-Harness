package io.jharness.api;

public record SessionState(String sessionId, TaskStatus status) {
    public SessionState {
        sessionId = Values.required(sessionId, "sessionId");
        java.util.Objects.requireNonNull(status);
    }
}
