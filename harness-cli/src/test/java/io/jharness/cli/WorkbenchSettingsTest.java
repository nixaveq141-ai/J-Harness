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

    @Test
    void missingMcpCredentialRejectsCaptureAndFrozenRuntimeBeforeAssembly() throws Exception {
        var manager = manager(new MemoryVault());
        manager.save(input(manager.read(), Map.of("default", "synthetic-model-key")));
        var frozen = manager.capture("");
        var change = input(manager.read(), Map.of());
        change.put(
                "extensions",
                Map.of(
                        "mcp",
                        List.of(
                                Map.of(
                                        "id",
                                        "docs",
                                        "transport",
                                        "http",
                                        "url",
                                        "http://127.0.0.1:1/mcp",
                                        "tokenEnv",
                                        "M1057_ABSENT_TOKEN"))));
        manager.save(change);
        assertEquals(
                "MCP_CREDENTIAL_MISSING",
                assertThrows(HarnessException.class, () -> manager.capture("")).code());
        assertNotNull(manager.runtime(frozen));
        var values = new LinkedHashMap<>(Values.object(frozen.get("resolved")));
        values.put(
                "ecosystem",
                Map.of(
                        "mcp",
                        List.of(
                                Map.of(
                                        "id",
                                        "docs",
                                        "transport",
                                        "http",
                                        "url",
                                        "http://127.0.0.1:1/mcp",
                                        "tokenEnv",
                                        "M1057_ABSENT_TOKEN"))));
        var missing = new LinkedHashMap<>(frozen);
        missing.put("resolved", values);
        assertEquals(
                "MCP_CREDENTIAL_MISSING",
                assertThrows(HarnessException.class, () -> manager.runtime(missing)).code());
    }

    @Test
    void explicitSkillRootsPersistDiscoverAndRejectTraversalWithoutNetwork() throws Exception {
        var manager = manager(new MemoryVault());
        Path directory = temp.resolve("skills/guide");
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("SKILL.md"),
                "---\nname: guide\ndescription: Local guide\n---\nBody");
        var candidate = input(manager.read(), Map.of("default", "synthetic-skill-key"));
        candidate.put(
                "extensions",
                Map.of(
                        "skillRoots",
                        List.of(
                                Map.of(
                                        "id",
                                        "project",
                                        "kind",
                                        "project",
                                        "directory",
                                        "skills"))));
        manager.save(candidate);
        var catalog = manager.skills(temp.toRealPath().toString());
        assertEquals(1, ((List<?>) catalog.get("skills")).size());
        assertEquals(
                "guide", Values.object(((List<?>) catalog.get("skills")).getFirst()).get("id"));
        var config = manager.runtime().config();
        assertTrue(Values.object(config.values.get("ecosystem")).containsKey("skillRoots"));
        for (String path : List.of("../outside", temp.toString(), "C:outside", "\\outside")) {
            var invalid = input(manager.read(), Map.of());
            invalid.put(
                    "extensions",
                    Map.of(
                            "skillRoots",
                            List.of(
                                    Map.of(
                                            "id",
                                            "project",
                                            "kind",
                                            "project",
                                            "directory",
                                            path))));
            assertThrows(HarnessException.class, () -> manager.save(invalid));
        }
        assertEquals(
                1, ((List<?>) manager.skills(temp.toRealPath().toString()).get("skills")).size());
    }

    @Test
    void explicitUserSkillRootAcceptsLocalAbsoluteAndRejectsRelativeOrUnc() throws Exception {
        var manager = manager(new MemoryVault());
        Path skill = temp.resolve("user-skills/guide");
        Files.createDirectories(skill);
        Files.writeString(
                skill.resolve("SKILL.md"),
                "---\nname: user-guide\ndescription: User guide\n---\nUser body");
        var candidate = input(manager.read(), Map.of("default", "synthetic-user-root-key"));
        candidate.put(
                "extensions",
                Map.of(
                        "skillRoots",
                        List.of(
                                Map.of(
                                        "id",
                                        "user",
                                        "kind",
                                        "user",
                                        "directory",
                                        skill.getParent().toRealPath().toString()))));
        manager.save(candidate);
        assertEquals(
                1, ((List<?>) manager.skills(temp.toRealPath().toString()).get("skills")).size());
        for (String directory : List.of("user-skills", "\\\\server\\share\\skills")) {
            var invalid = input(manager.read(), Map.of());
            invalid.put(
                    "extensions",
                    Map.of(
                            "skillRoots",
                            List.of(Map.of("id", "user", "kind", "user", "directory", directory))));
            assertThrows(HarnessException.class, () -> manager.save(invalid));
        }
        assertEquals(
                1, ((List<?>) manager.skills(temp.toRealPath().toString()).get("skills")).size());
    }

    @Test
    void acceptedModelAndBudgetSnapshotSurvivesDefaultAndCapabilityChanges() throws Exception {
        var vault = new MemoryVault();
        var manager = manager(vault);
        manager.save(input(manager.read(), Map.of("default", "synthetic-snapshot-key")));
        var old = manager.capture("default");
        var read = manager.read();
        var first = new LinkedHashMap<>(Values.object(((List<?>) read.get("models")).getFirst()));
        first.put("maxOutputTokens", 256);
        first.put("contextTokens", 8192);
        first.put("protocol", "responses");
        first.put("reasoningEffort", "low");
        first.put("disableThinking", false);
        var second = new LinkedHashMap<>(first);
        second.put("id", "other");
        second.put("model", "new-model");
        var change = input(read, Map.of("other", "synthetic-other-key"));
        change.put("models", List.of(first, second));
        change.put("selected", "other");
        manager.save(change);
        var reopened = manager(vault);
        assertEquals("initial", reopened.runtime(old).config().string("model", ""));
        assertEquals(
                "chat-completions", reopened.runtime(old).config().parameters().get("protocol"));
        var chosen = reopened.capture("default");
        assertEquals("default", reopened.runtime(chosen).config().selection.get("modelRoute"));
        assertEquals("responses", reopened.runtime(chosen).config().parameters().get("protocol"));
        assertEquals(256, reopened.runtime(chosen).config().maxOutputTokens());
        assertEquals("new-model", reopened.runtime().config().string("model", ""));
        assertEquals(false, reopened.check("default").get("requestSent"));
        assertFalse(Json.text(old).contains("synthetic-snapshot-key"));
        vault.values.clear();
        assertEquals(
                "API_KEY_MISSING",
                assertThrows(HarnessException.class, () -> manager(vault).runtime(old)).code());
    }

    @Test
    void unavailableNativeVaultPreservesSettingsAndBlocksAdmissionWithoutFallback()
            throws Exception {
        var memory = new MemoryVault();
        var original = manager(memory);
        original.save(input(original.read(), Map.of("default", "synthetic-unavailable-key")));
        byte[] before = Files.readAllBytes(temp.resolve(".jharness/settings/workbench.json"));
        var unavailable =
                new SystemCredentials() {
                    @Override
                    boolean supported() {
                        return false;
                    }

                    @Override
                    String read(String target) throws IOException {
                        throw new IOException("Unavailable");
                    }
                };
        var reopened = manager(unavailable);
        assertEquals("unavailable", reopened.read().get("credentialStore"));
        var model = Values.object(((List<?>) reopened.read().get("models")).getFirst());
        assertEquals(false, model.get("keyConfigured"));
        assertThrows(IOException.class, reopened::runtime);
        assertArrayEquals(
                before, Files.readAllBytes(temp.resolve(".jharness/settings/workbench.json")));
        assertFalse(Json.text(reopened.read()).contains("synthetic-unavailable-key"));
        var replacement = new LinkedHashMap<>(model);
        replacement.put("id", "environment-only");
        var change = input(reopened.read(), Map.of());
        change.put("models", List.of(replacement));
        change.put("selected", "environment-only");
        var saved = reopened.save(change);
        assertEquals(true, saved.get("credentialCleanupPending"));
        assertEquals("environment-only", manager(unavailable).read().get("selected"));
        assertEquals(
                1, memory.values.size()); // An inaccessible old native item is not claimed deleted.
    }

    @Test
    void permissionSelectionIsBoundedPersistentAndDoesNotRewriteRuntimeSnapshots()
            throws Exception {
        var vault = new MemoryVault();
        var manager = manager(vault);
        var initial = manager.read();
        assertEquals("default", initial.get("policy"));
        manager.save(input(initial, Map.of("default", "synthetic-permission-key")));
        var original = manager.runtime();
        var change = input(manager.read(), Map.of());
        change.put("policy", "read-only");
        manager.save(change);
        assertEquals("read-only", manager.runtime().config().string("policy", ""));
        assertEquals("default", original.config().string("policy", ""));
        assertEquals("read-only", manager(vault).runtime().config().string("policy", ""));
        // Older clients omit the new optional field: they must preserve an existing restriction.
        manager.save(input(manager.read(), Map.of()));
        assertEquals("read-only", manager.read().get("policy"));
        var invalid = input(manager.read(), Map.of());
        invalid.put("policy", "allow-all");
        assertEquals(
                "INVALID_SETTINGS",
                assertThrows(HarnessException.class, () -> manager.save(invalid)).code());
        assertEquals("read-only", manager.read().get("policy"));
    }

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
