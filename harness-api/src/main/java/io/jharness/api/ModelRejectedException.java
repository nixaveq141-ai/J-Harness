package io.jharness.api;

/** Trusted adapter proof of a complete non-streaming HTTP 429 rejection, without a response. */
public final class ModelRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ModelRejectedException() {
        super("MODEL_RATE_LIMIT");
    }
}
