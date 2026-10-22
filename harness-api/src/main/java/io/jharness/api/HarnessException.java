package io.jharness.api;

/**
 * Stable code and public-safe message. Call boundaries must redact using their known secret set;
 * causes are internal diagnostics and must never be serialized to users.
 */
public final class HarnessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private boolean rejectedBeforeMutation;

    public HarnessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public HarnessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Capability-provider proof from a local pre-mutation boundary, never inferred from a code. */
    public static HarnessException rejectedBeforeMutation(HarnessException cause) {
        var rejected = new HarnessException(cause.code(), cause.getMessage(), cause);
        rejected.rejectedBeforeMutation = true;
        return rejected;
    }

    public boolean rejectedBeforeMutation() {
        return rejectedBeforeMutation;
    }
}
