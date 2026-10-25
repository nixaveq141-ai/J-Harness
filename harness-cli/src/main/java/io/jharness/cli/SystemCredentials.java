package io.jharness.cli;

import io.jharness.api.HarnessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Current-user native vault. No secret in argv, environment or temporary files. */
class SystemCredentials {
    private Boolean available;
    private String availabilityFailure = "";

    synchronized String availabilityFailure() {
        supported();
        return availabilityFailure;
    }

    synchronized boolean supported() {
        if (available == null) {
            try {
                available =
                        "available"
                                .equals(
                                        invokeNative(
                                                List.of(
                                                        Map.of(
                                                                "action",
                                                                "probe",
                                                                "target",
                                                                "J-Harness/probe",
                                                                "value",
                                                                ""))));
            } catch (IOException | RuntimeException failure) {
                String code =
                        failure instanceof HarnessException h ? h.code() : failure.getMessage();
                availabilityFailure =
                        Set.of(
                                                "CREDENTIAL_WORKER_TIMEOUT",
                                                "CREDENTIAL_WORKER_INTERRUPTED",
                                                "CREDENTIAL_WORKER_FAILED",
                                                "CREDENTIAL_STORE_UNAVAILABLE",
                                                "CREDENTIAL_OPERATION_INVALID")
                                        .contains(String.valueOf(code))
                                ? code
                                : "CREDENTIAL_PROBE_FAILED";
                available = false;
            }
        }
        return available;
    }

    String description() {
        if (!supported()) return "unavailable";
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) return "Windows Credential Manager";
        return os.startsWith("Mac") ? "macOS Keychain" : "Linux Secret Service";
    }

    private String invokeNative(List<Map<String, String>> operations) throws IOException {
        String os = System.getProperty("os.name");
        if (!os.startsWith("Mac") && !os.equals("Linux") && !os.startsWith("Windows"))
            throw new IOException("Credential store unavailable on this platform");
        var builder =
                new ProcessBuilder(
                        Path.of(
                                        System.getProperty("java.home"),
                                        "bin",
                                        os.startsWith("Windows") ? "java.exe" : "java")
                                .toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        NativeCredentials.class.getName());
        return run(
                builder,
                operations,
                List.of(
                        "SystemRoot",
                        "USERPROFILE",
                        "LOCALAPPDATA",
                        "APPDATA",
                        "HOME",
                        "TMPDIR",
                        "TMP",
                        "TEMP",
                        "DBUS_SESSION_BUS_ADDRESS",
                        "XDG_RUNTIME_DIR",
                        "XDG_DATA_HOME"));
    }

    String read(String target) throws IOException {
        return invoke("read", target, "");
    }

    void write(String target, String value) throws IOException {
        invoke("write", target, value);
    }

    void delete(String target) throws IOException {
        invoke("delete", target, "");
    }

    private String invoke(String action, String target, String value) throws IOException {
        return invoke(List.of(Map.of("action", action, "target", target, "value", value)));
    }

    void update(Map<String, String> writes, List<String> deletes) throws IOException {
        if (writes.keySet().stream().anyMatch(deletes::contains)
                || writes.size() + deletes.size() > 32
                || new HashSet<>(deletes).size() != deletes.size())
            throw new HarnessException(
                    "CREDENTIAL_BATCH_INVALID", "Conflicting or oversized credential batch");
        var operations = new ArrayList<Map<String, String>>();
        writes.forEach(
                (target, value) ->
                        operations.add(
                                Map.of("action", "write", "target", target, "value", value)));
        deletes.forEach(
                target ->
                        operations.add(Map.of("action", "delete", "target", target, "value", "")));
        if (!operations.isEmpty()) invoke(operations);
    }

    private String invoke(List<Map<String, String>> operations) throws IOException {
        try {
            NativeCredentials.validateOperations(operations);
            byte[] encoded = Json.text(operations).getBytes(StandardCharsets.UTF_8);
            try {
                if (encoded.length > 65536)
                    throw new IllegalArgumentException("Credential request limit");
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (IllegalArgumentException | HarnessException invalid) {
            throw new HarnessException(
                    "CREDENTIAL_OPERATION_INVALID", "Invalid credential operation");
        }
        if (!supported())
            throw new HarnessException(
                    "CREDENTIAL_STORE_UNAVAILABLE",
                    "Native credential store unavailable; use an environment variable");
        return invokeNative(operations);
    }

    private String run(
            ProcessBuilder builder,
            List<Map<String, String>> operations,
            List<String> permittedEnvironment)
            throws IOException {
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var inherited = new HashMap<>(builder.environment());
        builder.environment().clear();
        for (String name : permittedEnvironment) {
            if (inherited.containsKey(name)) builder.environment().put(name, inherited.get(name));
        }
        var process = builder.start();
        return exchange(process, operations);
    }

    static String exchange(Process process, List<Map<String, String>> operations)
            throws IOException {
        boolean mutating =
                operations.stream()
                        .anyMatch(op -> Set.of("write", "delete").contains(op.get("action")));
        byte[] request = null;
        java.util.concurrent.ExecutorService workers = null;
        java.util.concurrent.Future<Boolean> sent = null;
        java.util.concurrent.Future<byte[]> received = null;
        byte[] bytes = null, decoded = null;
        try {
            request = Json.text(operations).getBytes(StandardCharsets.UTF_8);
            if (request.length > 65536) throw new IOException("CREDENTIAL_OPERATION_INVALID");
            byte[] sending = request;
            workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            sent =
                    workers.submit(
                            () -> {
                                try (var input = process.getOutputStream()) {
                                    input.write(sending);
                                } finally {
                                    Arrays.fill(sending, (byte) 0);
                                }
                                return true;
                            });
            received = workers.submit(() -> process.getInputStream().readNBytes(2049));
            if (!process.waitFor(7, TimeUnit.SECONDS))
                throw new IOException(
                        mutating ? "CREDENTIAL_UPDATE_UNKNOWN" : "CREDENTIAL_WORKER_TIMEOUT");
            if (process.exitValue() != 0)
                throw new IOException(
                        process.exitValue() == 8
                                ? "CREDENTIAL_UPDATE_UNKNOWN"
                                : process.exitValue() == 6
                                        ? "CREDENTIAL_OPERATION_INVALID"
                                        : "CREDENTIAL_STORE_UNAVAILABLE");
            sent.get(1, TimeUnit.SECONDS);
            bytes = received.get(1, TimeUnit.SECONDS);
            if (bytes.length > 2048) throw new IOException("Credential store response limit");
            try {
                decoded = Base64.getDecoder().decode(bytes);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Credential store response invalid");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    mutating ? "CREDENTIAL_UPDATE_UNKNOWN" : "CREDENTIAL_WORKER_INTERRUPTED");
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException failure) {
            throw new IOException(
                    mutating ? "CREDENTIAL_UPDATE_UNKNOWN" : "CREDENTIAL_WORKER_FAILED");
        } catch (IOException failure) {
            if (mutating
                    && !Set.of("CREDENTIAL_OPERATION_INVALID", "CREDENTIAL_STORE_UNAVAILABLE")
                            .contains(String.valueOf(failure.getMessage())))
                throw new IOException("CREDENTIAL_UPDATE_UNKNOWN");
            throw failure;
        } finally {
            if (request != null) Arrays.fill(request, (byte) 0);
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
            process.destroyForcibly();
            if (sent != null) sent.cancel(true);
            if (received != null) received.cancel(true);
            if (workers != null) workers.shutdownNow();
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                /* bounded worker teardown */
            }
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                /* no diagnostic output */
            }
            try {
                process.getErrorStream().close();
            } catch (IOException ignored) {
                /* no diagnostic output */
            }
        }
    }
}
