package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1WorkbenchTransactionTest {
    @TempDir Path root;

    class Vault extends SystemCredentials {
        final Map<String, String> values = new HashMap<>();
        boolean failWrite, failDelete, sabotage;

        boolean supported() {
            return true;
        }

        String read(String target) {
            return values.getOrDefault(target, "");
        }

        void write(String target, String value) throws IOException {
            if (failWrite) throw new IOException("unavailable");
            values.put(target, value);
        }

        void delete(String target) throws IOException {
            if (failDelete) throw new IOException("unavailable");
            values.remove(target);
        }

        void update(Map<String, String> writes, List<String> deletes) throws IOException {
            for (var e : writes.entrySet()) write(e.getKey(), e.getValue());
            for (var t : deletes) delete(t);
            if (sabotage) {
                sabotage = false;
                Files.delete(root.resolve(".jharness/settings/workbench.json"));
                Files.createDirectory(root.resolve(".jharness/settings/workbench.json"));
            }
        }
    }

    WorkbenchSettings manager(Vault vault) throws IOException {
        return new WorkbenchSettings(new CliConfig(Map.of()), Map.of(), new Metadata(root), vault);
    }

    Map<String, Object> input(WorkbenchSettings manager, String secret) throws IOException {
        var read = manager.read();
        var out = new HashMap<String, Object>();
        out.put("revision", read.get("revision"));
        out.put("selected", read.get("selected"));
        out.put("extensions", read.get("extensions"));
        out.put("models", read.get("models"));
        out.put("apiKeys", secret.isEmpty() ? Map.of() : Map.of("default", secret));
        return out;
    }

    @Test
    void failedRotationPreservesDiskAndOriginalCredentialAcrossRestart() throws Exception {
        var vault = new Vault();
        var settings = manager(vault);
        settings.save(input(settings, "synthetic-original-secret"));
        byte[] before = Files.readAllBytes(root.resolve(".jharness/settings/workbench.json"));
        vault.failWrite = true;
        assertThrows(
                IOException.class,
                () -> settings.save(input(settings, "synthetic-replacement-secret")));
        assertArrayEquals(
                before, Files.readAllBytes(root.resolve(".jharness/settings/workbench.json")));
        assertEquals(
                "synthetic-original-secret",
                manager(vault).runtime().environment().get("JHARNESS_UI_API_KEY"));
        assertEquals(Set.of("synthetic-original-secret"), new HashSet<>(vault.values.values()));
    }

    @Test
    void failedFileCommitCompensatesOnlyNewCredentialAndRedactsFailedInput() throws Exception {
        var vault = new Vault();
        var settings = manager(vault);
        settings.save(input(settings, "synthetic-original-secret"));
        byte[] before = Files.readAllBytes(root.resolve(".jharness/settings/workbench.json"));
        vault.sabotage = true;
        assertThrows(
                HarnessException.class,
                () -> settings.save(input(settings, "synthetic-replacement-secret")));
        assertEquals(Set.of("synthetic-original-secret"), new HashSet<>(vault.values.values()));
        assertFalse(
                settings.sanitize(Map.of("value", "synthetic-replacement-secret"))
                        .toString()
                        .contains("synthetic-replacement-secret"));
        Files.delete(root.resolve(".jharness/settings/workbench.json"));
        Files.write(root.resolve(".jharness/settings/workbench.json"), before);
        assertEquals(
                "synthetic-original-secret",
                manager(vault).runtime().environment().get("JHARNESS_UI_API_KEY"));
    }

    @Test
    void obsoleteCredentialCleanupSurvivesRestartAndResetsPending() throws Exception {
        var vault = new Vault();
        var settings = manager(vault);
        settings.save(input(settings, "synthetic-original-secret"));
        vault.failDelete = true;
        settings.save(input(settings, "synthetic-replacement-secret"));
        assertEquals(true, settings.read().get("credentialCleanupPending"));
        assertEquals(2, vault.values.size());
        vault.failDelete = false;
        var reopened = manager(vault);
        assertEquals(false, reopened.read().get("credentialCleanupPending"));
        assertEquals(Set.of("synthetic-replacement-secret"), new HashSet<>(vault.values.values()));
    }

    @Test
    void invalidSaveLeavesExistingDiskRuntimeAndVaultUntouched() throws Exception {
        var vault = new Vault();
        var settings = manager(vault);
        settings.save(input(settings, "synthetic-original-secret"));
        byte[] before = Files.readAllBytes(root.resolve(".jharness/settings/workbench.json"));
        var candidate = input(settings, "synthetic-failed-secret");
        candidate.put("selected", "missing");
        assertThrows(HarnessException.class, () -> settings.save(candidate));
        assertArrayEquals(
                before, Files.readAllBytes(root.resolve(".jharness/settings/workbench.json")));
        assertEquals(
                "synthetic-original-secret",
                manager(vault).runtime().environment().get("JHARNESS_UI_API_KEY"));
    }

    @Test
    void repeatedSavesReuseRedactorUntilASecretIsAddedIncludingFailedRotation() throws Exception {
        var vault = new Vault();
        var settings = manager(vault);
        String original = "synthetic-repeated-save-original",
                replacement = "synthetic-failed-rotation-new";
        settings.save(input(settings, original));
        var field = WorkbenchSettings.class.getDeclaredField("redactor");
        field.setAccessible(true);
        Object first = field.get(settings);
        for (int i = 0; i < 5; i++) {
            settings.save(input(settings, original));
            assertSame(
                    first,
                    field.get(settings),
                    "unchanged secret set must reuse the immutable redactor");
        }
        vault.failWrite = true;
        assertThrows(IOException.class, () -> settings.save(input(settings, replacement)));
        Object changed = field.get(settings);
        assertNotSame(first, changed);
        assertEquals(
                "[REDACTED] [REDACTED]",
                settings.sanitize(Map.of("value", original + " " + replacement)).get("value"));
        assertThrows(IOException.class, () -> settings.save(input(settings, replacement)));
        assertSame(changed, field.get(settings), "failed-input registration is deduplicated too");
        assertEquals(Set.of(original), new HashSet<>(vault.values.values()));
    }
}
