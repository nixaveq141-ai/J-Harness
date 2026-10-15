package io.jharness.api;

/** Stable machine-readable error code; messages must not contain secrets. */
public final class HarnessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

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
}
