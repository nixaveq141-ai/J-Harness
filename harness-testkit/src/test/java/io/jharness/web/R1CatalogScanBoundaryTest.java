package io.jharness.web;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.session.JsonlSessionRepository;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1CatalogScanBoundaryTest {
    @TempDir Path root;

    private SessionEvent accepted(String id, String hash) {
        return SessionEvent.session(
                "catalog",
                "web.task.accepted",
                Map.of(
                        "taskId",
                        id,
                        "profile",
                        "local",
                        "requestHash",
                        hash,
                        "goalHash",
                        "a".repeat(64),
                        "workspaceBinding",
                        "fixture-workspace"));
    }

    @Test
    void coldLegacyLookupReadsOnlyItsRecordButFullScanStillRejectsCorruption() throws Exception {
        var repo = new JsonlSessionRepository(root);
        Path records = root.resolve("tasks-" + DurableTaskCatalog.hash("catalog").substring(0, 24));
        String wanted = DurableTaskCatalog.hash("legacy-0"),
                other = DurableTaskCatalog.hash("legacy-39");
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor())) {
            for (int i = 0; i < 40; i++)
                catalog.append(accepted("legacy-" + i, DurableTaskCatalog.hash("legacy-" + i)));
        }
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor())) {
            // Evict any startup admission-cache ordering before exercising the legacy index.
            for (int i = 1; i < 40; i++) catalog.byId("legacy-" + i);
            Path unrelated = records.resolve("h-" + other + ".jsonl");
            byte[] original = Files.readAllBytes(unrelated);
            Files.writeString(unrelated, "corrupt unrelated durable fact\n");
            try {
                assertEquals(
                        "legacy-0", catalog.byId("legacy-0").getFirst().payload().get("taskId"));
                assertTrue(catalog.byId("missing-legacy").isEmpty());
                assertTrue(catalog.byId("../invalid").isEmpty());
                assertThrows(HarnessException.class, () -> catalog.scan(history -> {}));
                Path target = records.resolve("h-" + wanted + ".jsonl");
                byte[] saved = Files.readAllBytes(target);
                Files.writeString(target, "corrupt addressed fact\n");
                try {
                    assertThrows(HarnessException.class, () -> catalog.byId("legacy-0"));
                } finally {
                    Files.write(target, saved);
                }
            } finally {
                Files.write(unrelated, original);
            }
        }
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor())) {
            assertEquals("legacy-0", catalog.byId("legacy-0").getFirst().payload().get("taskId"));
        }
    }

    @Test
    void duplicateLegacyIdentityIsRejectedBeforeASecondRecordIsWritten() throws Exception {
        var repo = new JsonlSessionRepository(root);
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog =
                        new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor())) {
            catalog.append(accepted("legacy", "a".repeat(64)));
            assertEquals(
                    "INVALID_WEB_CATALOG",
                    assertThrows(
                                    HarnessException.class,
                                    () -> catalog.append(accepted("legacy", "b".repeat(64))))
                            .code());
            assertEquals(1, catalog.count());
            assertTrue(catalog.byHash("b".repeat(64)).isEmpty());
            assertEquals(
                    "a".repeat(64), catalog.byId("legacy").getFirst().payload().get("requestHash"));
        }
    }

    @Test
    void flatTenThousandRecordScanIsBoundedAndDoesNotHoldCatalogMonitor() throws Exception {
        var repo = new JsonlSessionRepository(root);
        try (var owner = repo.openWriter("catalog", WriteOwnership.EXCLUSIVE);
                var catalog = new DurableTaskCatalog(repo, "catalog", owner, new SecretRedactor());
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Path records =
                    root.resolve("tasks-" + DurableTaskCatalog.hash("catalog").substring(0, 24));
            String templateId = "h-" + "0".repeat(64);
            var physical = new JsonlSessionRepository(records);
            try (var writer = physical.openWriter(templateId, WriteOwnership.EXCLUSIVE)) {
                writer.append(
                        SessionEvent.session(
                                templateId,
                                "web.task.accepted",
                                Map.of(
                                        "taskId",
                                        "web-" + "f".repeat(32) + "-" + "0".repeat(64),
                                        "profile",
                                        "local",
                                        "requestHash",
                                        "0".repeat(64),
                                        "goalHash",
                                        "2".repeat(64),
                                        "workspaceBinding",
                                        "fixture-workspace",
                                        "selection",
                                        Map.of())));
            }
            String template = Files.readString(records.resolve(templateId + ".jsonl"));
            for (int i = 1; i < 10000; i++) {
                String id = "h-" + String.format(Locale.ROOT, "%064x", i);
                Files.writeString(
                        records.resolve(id + ".jsonl"),
                        template.replace("0".repeat(64), id.substring(2)));
            }
            var visited = ConcurrentHashMap.<String>newKeySet();
            var atFirst = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var scan =
                    pool.submit(
                            () -> {
                                catalog.scanSnapshot(
                                        history -> {
                                            assertEquals(1, history.size());
                                            assertEquals(
                                                    "web.task.accepted", history.getFirst().type());
                                            assertEquals(
                                                    history.getFirst().sessionId().substring(2),
                                                    history.getFirst()
                                                            .payload()
                                                            .get("requestHash"));
                                            assertEquals(
                                                    "web-"
                                                            + "f".repeat(32)
                                                            + "-"
                                                            + history.getFirst()
                                                                    .sessionId()
                                                                    .substring(2),
                                                    history.getFirst().payload().get("taskId"));
                                            assertTrue(
                                                    visited.add(history.getFirst().sessionId()),
                                                    "no duplicate record");
                                            if (visited.size() == 1) {
                                                atFirst.countDown();
                                                try {
                                                    if (!release.await(4, TimeUnit.SECONDS))
                                                        throw new AssertionError(
                                                                "scan release timeout");
                                                } catch (InterruptedException ex) {
                                                    Thread.currentThread().interrupt();
                                                    throw new AssertionError(ex);
                                                }
                                            }
                                        });
                                return true;
                            });
            try {
                assertTrue(atFirst.await(2, TimeUnit.SECONDS));
                var independent =
                        pool.submit(
                                () -> {
                                    synchronized (catalog) {
                                        assertEquals(1, catalog.byHash("0".repeat(64)).size());
                                        return true;
                                    }
                                });
                assertTrue(independent.get(2, TimeUnit.SECONDS));
                assertFalse(scan.isDone());
            } finally {
                release.countDown();
            }
            assertTrue(scan.get(30, TimeUnit.SECONDS));
            assertEquals(10000, visited.size());
            String overflow = "h-" + String.format(Locale.ROOT, "%064x", 10000);
            Files.writeString(
                    records.resolve(overflow + ".jsonl"),
                    template.replace("0".repeat(64), overflow.substring(2)));
            var received = new AtomicInteger();
            var failure =
                    assertThrows(
                            HarnessException.class,
                            () -> catalog.scanSnapshot(history -> received.incrementAndGet()));
            assertEquals("WEB_HISTORY_QUOTA", failure.code());
            assertEquals(
                    10000, received.get(), "10001st record is rejected before its history is read");
        }
    }
}
