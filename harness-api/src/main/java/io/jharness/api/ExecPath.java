package io.jharness.api;

/**
 * Environment path value, including the provider root token. Lexical and protected-path validation
 * belongs to the selected provider before any I/O; this value never grants access. Host
 * configuration paths are not automatically converted into tool paths.
 */
public record ExecPath(String value) {
    public ExecPath {
        value = Values.required(value, "value");
    }
}
