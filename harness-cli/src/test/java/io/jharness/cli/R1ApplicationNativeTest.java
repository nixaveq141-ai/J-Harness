package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1ApplicationNativeTest {
    @TempDir Path root;

    @BeforeEach
    void canonicalTemporaryRoot() throws java.io.IOException {
        root = root.toRealPath();
    }

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
                "STORE_UNAVAILABLE",
                CredentialStoreProbe.failureCode(
                        new HarnessException(
                                "CREDENTIAL_STORE_UNAVAILABLE", "synthetic-secret-native-error")));
        assertEquals(
                "PROBE_FAILED",
                CredentialStoreProbe.failureCode(
                        new HarnessException(
                                "synthetic-secret-code", "CREDENTIAL_STORE_UNAVAILABLE")));
        for (var entry :
                Map.of(
                                "CREDENTIAL_WORKER_INTERRUPTED",
                                "WORKER_INTERRUPTED",
                                "CREDENTIAL_WORKER_FAILED",
                                "WORKER_FAILED",
                                "Credential store response limit",
                                "RESPONSE_LIMIT",
                                "Credential store response invalid",
                                "RESPONSE_INVALID")
                        .entrySet())
            assertEquals(
                    entry.getValue(),
                    CredentialStoreProbe.failureCode(new java.io.IOException(entry.getKey())));
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

    @Test
    void awsAliasesAreRedactedByClassicCliAndRejectedByEcosystemAssembly() throws Exception {
        for (String alias :
                List.of("AWS_SECRET_ACCESS_KEY", "AWS_ACCESS_KEY_ID", "AWS_CREDENTIALS")) {
            String secret = "synthetic-aws-" + alias.toLowerCase(Locale.ROOT);
            var environment = Map.of(alias, secret);
            var output = new java.io.ByteArrayOutputStream();
            var errors = new java.io.ByteArrayOutputStream();
            int code =
                    new CliApplication(environment)
                            .execute(
                                    new String[] {
                                        "config",
                                        "set",
                                        "model",
                                        secret,
                                        "--workspace",
                                        root.toString()
                                    },
                                    new java.io.ByteArrayInputStream(new byte[0]),
                                    new java.io.PrintStream(output),
                                    new java.io.PrintStream(errors));
            assertEquals(5, code, errors.toString());
            assertTrue(errors.toString().contains("SECRET_CONFIG"));
            assertFalse(output.toString().contains(secret));
            assertFalse(errors.toString().contains(secret));
            assertEquals(
                    "SECRET_CONFIG",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            new EcosystemCli(
                                                    new CliConfig(Map.of("model", secret)),
                                                    environment,
                                                    root,
                                                    new Metadata(root)))
                            .code());
        }
    }

    @Test
    void mcpUserInfoAndFragmentsAreRejectedBeforeAnyConnection() {
        for (String url :
                List.of(
                        "https://user:synthetic-password@localhost/mcp",
                        "https://localhost/mcp#synthetic-fragment")) {
            assertThrows(
                    HarnessException.class,
                    () ->
                            new CliConfig(
                                    Map.of(
                                            "ecosystem",
                                            Map.of(
                                                    "mcp",
                                                    List.of(
                                                            Map.of(
                                                                    "id",
                                                                    "fixture",
                                                                    "transport",
                                                                    "http",
                                                                    "url",
                                                                    url))))));
        }
        assertDoesNotThrow(
                () ->
                        new CliConfig(
                                Map.of(
                                        "ecosystem",
                                        Map.of(
                                                "mcp",
                                                List.of(
                                                        Map.of(
                                                                "id",
                                                                "fixture",
                                                                "transport",
                                                                "http",
                                                                "url",
                                                                "https://localhost/mcp",
                                                                "tokenEnv",
                                                                "MCP_FIXTURE"))))));
    }
}
