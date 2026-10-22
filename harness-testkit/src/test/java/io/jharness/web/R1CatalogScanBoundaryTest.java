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
