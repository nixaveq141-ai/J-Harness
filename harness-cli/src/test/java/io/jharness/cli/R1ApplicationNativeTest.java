package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1ApplicationNativeTest {
    @TempDir Path root;

    @Test
    void utf8ByteLimitIsValidatedBeforeNativeOperation() {
        NativeCredentials.validateOperations(
                List.of(
                        Map.of(
                                "action",
                                "write",
                                "target",
                                "J-Harness/test",
                                "value",
                                "界".repeat(341))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeCredentials.validateOperations(
                                List.of(
                                        Map.of(
                                                "action",
                                                "write",
                                                "target",
                                                "J-Harness/test",
                                                "value",
                                                "界".repeat(342)))));
    }

    @Test
    void conflictingAndOversizedBatchesNeverReachVault() {
        var vault = new SystemCredentials();
        assertEquals(
                "CREDENTIAL_BATCH_INVALID",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        vault.update(
                                                Map.of("J-Harness/test", "synthetic"),
                                                List.of("J-Harness/test")))
                        .code());
        assertEquals(
                "CREDENTIAL_BATCH_INVALID",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        vault.update(
                                                Map.of(),
                                                Collections.nCopies(33, "J-Harness/test")))
                        .code());
    }

    @Test
    void resourceCloseFailureCannotSkipOtherResourcesAndPreservesSuppressed() {
        var calls = new ArrayList<Integer>();
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second");
        var failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                CliApplication.closeAll(
                                        () -> {
                                            calls.add(1);
                                            throw first;
                                        },
                                        () -> {
                                            calls.add(2);
                                            throw second;
                                        },
                                        () -> calls.add(3)));
        assertEquals(List.of(1, 2, 3), calls);
        assertSame(first, failure);
        assertArrayEquals(new Throwable[] {second}, failure.getSuppressed());
    }

    @Test
    void credentialsAliasesAreRedactedInWorkbenchAndFailedInputsRemainRegistered()
            throws Exception {
        var settings =
                new WorkbenchSettings(
                        new CliConfig(Map.of()),
                        Map.of("AWS_SECRET_ACCESS_KEY", "synthetic-aws-fixture"),
                        new Metadata(root),
                        new WorkbenchSettingsTest.MemoryVault());
        assertFalse(
                settings.sanitize(Map.of("value", "synthetic-aws-fixture"))
                        .toString()
                        .contains("synthetic-aws-fixture"));
    }

    @Test
    void reportRedactsBeforeTruncationWithoutLeakingKnownSecretPrefix() {
        String secret = "synthetic-boundary-" + "z".repeat(100);
        var event =
                new SessionEvent(
                        "e",
                        "s",
                        "assistant.message",
                        java.time.Instant.now(),
                        1,
                        EventLevel.STEP,
                        "t",
                        "step",
                        null,
                        Map.of("content", "a".repeat(16380) + secret));
        String report =
                EvidenceReport.build("fixture", List.of(event), new SecretRedactor(List.of(secret)))
                        .toString();
        assertFalse(report.contains("synthetic-boundary"));
        assertTrue(report.contains("TRUNCATED"));
    }

    @Test
    void nativeProbeReportsOnlyFixedSafeFailureCategories() {
        assertEquals(
                "WORKER_TIMEOUT",
                CredentialStoreProbe.failureCode(
                        new java.io.IOException("CREDENTIAL_WORKER_TIMEOUT")));
        assertEquals(
                "UPDATE_UNKNOWN",
                CredentialStoreProbe.failureCode(
                        new java.io.IOException("CREDENTIAL_UPDATE_UNKNOWN")));
        assertEquals(
                "STORE_UNAVAILABLE",
                CredentialStoreProbe.failureCode(
                        new java.io.IOException("CREDENTIAL_STORE_UNAVAILABLE")));
        assertEquals(
                "OPERATION_INVALID",
                CredentialStoreProbe.failureCode(
                        new java.io.IOException("CREDENTIAL_OPERATION_INVALID")));
        assertEquals(
                "PROBE_FAILED",
                CredentialStoreProbe.failureCode(
                        new java.io.IOException("synthetic-secret-native-error")));
    }
}
