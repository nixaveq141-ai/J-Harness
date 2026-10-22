package io.jharness.cli;

import java.io.IOException;
import java.util.*;

/** Opt-in real OS integration, launched separately; never substituted by MemoryVault. */
public final class CredentialStoreProbe {
    private CredentialStoreProbe() {}

    public static void main(String[] args) throws Exception {
        String target = "J-Harness/native-validation/" + UUID.randomUUID();
        String first = "synthetic-native-" + UUID.randomUUID();
        String second = "synthetic-rotation-" + UUID.randomUUID();
        var checks = new LinkedHashMap<String, Boolean>();
        var vault = new SystemCredentials();
        boolean succeeded = false;
        String failureCode = "";
        String cleanupFailureCode = "";
        try {
            require(vault.supported());
            checks.put("available", true);
            require(new SystemCredentials().read(target).isEmpty());
            checks.put("missing", true);
            vault.write(target, first);
            require(first.equals(new SystemCredentials().read(target)));
            checks.put("writeRestartRead", true);
            new SystemCredentials().write(target, second);
            require(second.equals(new SystemCredentials().read(target)));
            checks.put("rotateRestartRead", true);
            new SystemCredentials().delete(target);
            require(new SystemCredentials().read(target).isEmpty());
            checks.put("deleteRestartRead", true);
            new SystemCredentials().delete(target);
            require(new SystemCredentials().read(target).isEmpty());
            checks.put("deleteMissing", true);
            succeeded = true;
        } catch (Exception failure) {
            // Only fixed categories: native errors and exception text may contain secrets.
            failureCode = failureCode(failure);
        } finally {
            try {
                vault.delete(target);
                require(new SystemCredentials().read(target).isEmpty());
                checks.put("cleanup", true);
            } catch (Exception failure) {
                succeeded = false;
                cleanupFailureCode = "CLEANUP_FAILED";
                if (failureCode.isEmpty()) failureCode = cleanupFailureCode;
                checks.put("cleanup", false);
            }
            System.out.println(
                    Json.text(
                            Map.of(
                                    "platform",
                                    System.getProperty("os.name"),
                                    "credentialStore",
                                    vault.description(),
                                    "passed",
                                    succeeded,
                                    "failureCode",
                                    failureCode,
                                    "cleanupFailureCode",
                                    cleanupFailureCode,
                                    "checks",
                                    checks)));
        }
        if (!succeeded) throw new IllegalStateException("Native credential validation failed");
    }

    static String failureCode(Exception failure) {
        if (failure instanceof IOException) {
            return switch (String.valueOf(failure.getMessage())) {
                case "CREDENTIAL_WORKER_TIMEOUT", "Credential store timeout" -> "WORKER_TIMEOUT";
                case "CREDENTIAL_UPDATE_UNKNOWN" -> "UPDATE_UNKNOWN";
                case "CREDENTIAL_OPERATION_INVALID" -> "OPERATION_INVALID";
                case "CREDENTIAL_STORE_UNAVAILABLE" -> "STORE_UNAVAILABLE";
                default -> "PROBE_FAILED";
            };
        }
        return "PROBE_FAILED";
    }

    private static void require(boolean value) {
        if (!value) throw new IllegalStateException("Native credential validation failed");
    }
}
