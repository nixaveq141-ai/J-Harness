package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchSettingsTest {
    @TempDir Path temp;

    static final class MemoryVault extends SystemCredentials {
        @Override
        void update(Map<String, String> writes, List<String> deletes) throws IOException {
            for (var e : writes.entrySet()) write(e.getKey(), e.getValue());
            for (String target : deletes) delete(target);
        }

        final Map<String, String> values = new HashMap<>();
        boolean fail;

        @Override
        boolean supported() {
            return true;
        }

        @Override
        String read(String target) {
            return values.getOrDefault(target, "");
        }

        @Override
        void write(String target, String value) throws IOException {
            if (fail) throw new IOException("fixture");
            values.put(target, value);
        }

        @Override
        void delete(String target) {
            values.remove(target);
        }
    }

    private WorkbenchSettings manager(SystemCredentials vault) throws IOException {
        return new WorkbenchSettings(
                new CliConfig(Map.of("baseUrl", "https://example.com", "model", "initial")),
                Map.of(),
                new Metadata(temp.toRealPath()),
                vault);
    }

    private static Map<String, Object> input(Map<String, Object> read, Map<String, String> keys) {
        var result = new LinkedHashMap<>(read);
        result.keySet().retainAll(Set.of("revision", "models", "selected", "extensions"));
        result.put("apiKeys", keys);
        return result;
    }

    private static Map<String, Object> edited(
            Map<String, Object> read, Map<String, Object> changes) {
        var model = new LinkedHashMap<>(Values.object(((List<?>) read.get("models")).getFirst()));
        model.putAll(changes);
        var result = new LinkedHashMap<>(read);
        result.put("models", List.of(model));
        return result;
    }

    @Test
    void savedKeySurvivesNewHostWithoutEnteringDiskOrResponses() throws Exception {
        var vault = new MemoryVault();
        var manager = manager(vault);
        String secret = "synthetic-key-" + UUID.randomUUID();
        var before = manager.read();
        var after =
                manager.save(
                        input(
                                edited(
                                        before,
                                        Map.of(
                                                "model",
                                                "second",
                                                "baseUrl",
                                                "http://127.0.0.1:9999")),
                                Map.of("default", secret)));
        assertFalse(Json.text(after).contains(secret));
        assertFalse(
                Files.readString(temp.resolve(".jharness/settings/workbench.json"))
                        .contains(secret));
        assertEquals("[REDACTED]", manager.sanitize(Map.of("text", secret)).get("text"));
        var restart = manager(vault);
        var runtime = restart.runtime();
        assertEquals("second", runtime.config().string("model", ""));
        assertEquals(secret, runtime.config().key(runtime.environment()));
        assertEquals(1, vault.values.size());
        assertEquals(
                "SETTINGS_CONFLICT",
                assertThrows(HarnessException.class, () -> manager.save(input(before, Map.of())))
                        .code());
    }

    @Test
    void invalidConfigurationCannotWriteCredentialsOrReplaceState() throws Exception {
        var vault = new MemoryVault();
        var manager = manager(vault);
        var before = manager.read();
        for (var patch :
                List.of(
                        Map.<String, Object>of("baseUrl", "https://user:password@example.com"),
                        Map.<String, Object>of("baseUrl", "https://example.com?key=foo"),
                        Map.<String, Object>of("pricing", Map.of("maxCost", "1")))) {
            assertThrows(
                    RuntimeException.class,
                    () ->
                            manager.save(
                                    input(
                                            edited(before, patch),
                                            Map.of("default", "synthetic-invalid-test"))));
        }
        assertTrue(vault.values.isEmpty());
        assertEquals(before, manager.read());
        assertFalse(Files.exists(temp.resolve(".jharness/settings/workbench.json")));
    }

    @Test
    void vaultFailureKeepsSettingsAndKeyRotationDeletesOnlyManagedOldKey() throws Exception {
        var vault = new MemoryVault();
        var manager = manager(vault);
        var before = manager.read();
        vault.fail = true;
        assertThrows(
                IOException.class,
                () -> manager.save(input(before, Map.of("default", "synthetic-first-key"))));
        assertEquals(before, manager.read());
        vault.fail = false;
        var after = manager.save(input(before, Map.of("default", "synthetic-first-key")));
        var first = vault.values.keySet().iterator().next();
        manager.save(input(after, Map.of("default", "synthetic-second-key")));
        assertFalse(vault.values.containsKey(first));
        assertEquals(1, vault.values.size());
        assertEquals(
                "synthetic-second-key",
                manager.runtime().config().key(manager.runtime().environment()));
    }

    @Test
    void extensionsAndIndependentModelPricesReachRuntime() throws Exception {
        var manager = manager(new MemoryVault());
        var read =
                edited(
                        manager.read(),
                        Map.of(
                                "model",
                                "priced",
                                "pricing",
                                Map.of(
                                        "currency",
                                        "CNY",
                                        "maxCost",
                                        "2",
                                        "inputPerMillion",
                                        "1",
                                        "outputPerMillion",
                                        "3",
                                        "inputTokenCeiling",
                                        10000)));
        read = new LinkedHashMap<>(read);
        read.put(
                "extensions",
                Map.of(
                        "planning",
                        true,
                        "compactContext",
                        true,
                        "skills",
                        List.of(
                                Map.of(
                                        "id",
                                        "guide",
                                        "directory",
                                        "skills/guide",
                                        "resources",
                                        Map.of("guide", "SKILL.md"),
                                        "scripts",
                                        Map.of()))));
        manager.save(input(read, Map.of("default", "synthetic-priced-key")));
        var config = manager.runtime().config();
        assertEquals("CNY", config.currency());
        assertEquals("2", config.budget().maxApiCost().orElseThrow().toPlainString());
        assertTrue(
                Boolean.TRUE.equals(Values.object(config.values.get("ecosystem")).get("planning")));
        assertEquals(
                1,
                EcosystemConfig.entries(Values.object(config.values.get("ecosystem")), "skills", 8)
                        .size());
    }

    @Test
    void systemCredentialAdapterRoundTripAndCleanupOrExplicitUnsupported() throws Exception {
        var vault = new SystemCredentials();
        if (!vault.supported()) {
            assertEquals(
                    "CREDENTIAL_STORE_UNAVAILABLE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> vault.write("J-Harness/test", "synthetic"))
                            .code());
            return;
        }
        String target = "J-Harness/test-" + UUID.randomUUID(),
                secret = "synthetic-" + UUID.randomUUID();
        try {
            vault.write(target, secret);
            assertEquals(secret, new SystemCredentials().read(target));
            vault.write(target, secret + "-changed");
            assertEquals(secret + "-changed", vault.read(target));
        } finally {
            vault.delete(target);
        }
        assertEquals("", vault.read(target));
    }
}
