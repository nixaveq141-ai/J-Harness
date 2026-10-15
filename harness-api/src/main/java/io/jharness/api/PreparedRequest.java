package io.jharness.api;

/** Prepared does not mean persisted or sent: the caller must satisfy checkpoint policy. */
public record PreparedRequest(ChatRequest request) {
    public PreparedRequest {
        java.util.Objects.requireNonNull(request);
    }
}
