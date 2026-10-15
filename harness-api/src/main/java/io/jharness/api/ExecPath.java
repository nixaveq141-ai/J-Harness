package io.jharness.api;

/** Environment-relative path; no host java.nio.file.Path escapes into tool contracts. */
public record ExecPath(String value) {
    public ExecPath {
        value = Values.required(value, "value");
    }
}
