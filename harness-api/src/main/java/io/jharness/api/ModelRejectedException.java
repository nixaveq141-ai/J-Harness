package io.jharness.api;

/** Trusted adapter proof of a complete non-streaming HTTP 429 rejection, without a response. */
public final class ModelRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterMillis;

    public ModelRejectedException() {
        this(java.util.OptionalLong.empty());
    }

    /** Safe structured metadata only; raw provider bodies and headers are never retained. */
    public ModelRejectedException(java.util.OptionalLong retryAfterMillis) {
        super("MODEL_RATE_LIMIT");
        java.util.Objects.requireNonNull(retryAfterMillis);
        if (retryAfterMillis.isPresent()
                && (retryAfterMillis.getAsLong() < 0 || retryAfterMillis.getAsLong() > 86_400_000))
            throw new IllegalArgumentException("retryAfterMillis");
        this.retryAfterMillis = retryAfterMillis.orElse(-1);
    }

    public int statusCode() {
        return 429;
    }

    public java.util.OptionalLong retryAfterMillis() {
        return retryAfterMillis < 0
                ? java.util.OptionalLong.empty()
                : java.util.OptionalLong.of(retryAfterMillis);
    }
}
