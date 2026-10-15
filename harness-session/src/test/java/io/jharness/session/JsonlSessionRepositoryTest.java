package io.jharness.session;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlSessionRepositoryTest {
    @Test
    void accumulatedNodeCapacityIsCheckedBeforeAppendSoTheExistingLogStaysReadable()
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < 10000; index++) payload.put("k" + index, 0);
        JsonlSessionRepository repository = new JsonlSessionRepository(root);
        int accepted = 0;
        try (SessionWriter writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            for (int index = 0; index < 20; index++) {
                try {
                    writer.append(SessionEvent.session("s", "dense", payload));
                    accepted++;
                } catch (HarnessException ex) {
                    assertEquals("RECORD_LIMIT", ex.code());
                    break;
                }
            }
            assertTrue(accepted > 0 && accepted < 20);
            assertEquals(accepted, repository.openReader("s").events().size());
        }
        assertEquals(accepted, new JsonlSessionRepository(root).openReader("s").events().size());
    }

    @Test
    void structurallyDenseRecordIsRejectedBeforeObjectMaterializationAndNeverTailRepaired()
            throws Exception {
        JsonlSessionRepository repository = new JsonlSessionRepository(root);
        try (SessionWriter writer = repository.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.flush();
        }
        String fields =
                java.util.stream.IntStream.range(0, 60000)
                        .mapToObj(i -> "\"k" + i + "\":0")
                        .collect(java.util.stream.Collectors.joining(","));
        Files.writeString(root.resolve("s.jsonl"), "{" + fields + "}\n", StandardOpenOption.APPEND);
        assertEquals(
                "RECORD_LIMIT",
                assertThrows(HarnessException.class, () -> repository.openReader("s").events())
                        .code());
        JsonlSessionRepository repair =
                new JsonlSessionRepository(
                        root,
                        JsonlSessionRepository.TailPolicy.REPAIR_TAIL,
                        c -> c.force(true),
                        code -> {});
        byte[] before = Files.readAllBytes(root.resolve("s.jsonl"));
        assertEquals(
                "RECORD_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> repair.openWriter("s", WriteOwnership.EXCLUSIVE))
                        .code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("s.jsonl")));
    }

    @TempDir Path root;

    @Test
    void forcedRecordsSurviveReopenAndAClosedWriterCannotAppend() throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        SessionEvent event =
                SessionEvent.session("s", "session.created", Map.of("text", "line\n中文"));
        SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
        assertTrue(writer.durable());
        writer.append(event);
        writer.flush();
        writer.close();
        writer.close();
        assertEquals(List.of(event), new JsonlSessionRepository(root).openReader("s").events());
        assertEquals(
                "WRITER_CLOSED",
                assertThrows(HarnessException.class, () -> writer.append(event)).code());
        assertTrue(Files.readString(root.resolve("s.jsonl")).endsWith("\n"));
        assertEquals(2, Files.readAllLines(root.resolve("s.jsonl")).size());
    }

    @Test
    void sameProcessAndSecondJvmCannotWriteWhileOwnerHoldsTheLock() throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
            byte[] before = Files.readAllBytes(root.resolve("s.jsonl"));
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            new JsonlSessionRepository(root)
                                                    .openWriter("s", WriteOwnership.EXCLUSIVE))
                            .code());
            Process process = probe();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(23, process.exitValue());
            assertTrue(
                    new String(process.getInputStream().readAllBytes()).contains("WRITE_CONFLICT"));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("s.jsonl")));
            assertEquals(
                    "WRITE_CONFLICT",
                    assertThrows(HarnessException.class, () -> repo.repairTail("s")).code());
            Process afterMaintenance = probe();
            assertTrue(afterMaintenance.waitFor(10, TimeUnit.SECONDS));
            assertEquals(23, afterMaintenance.exitValue());
        }
        Process second = probe();
        assertTrue(second.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, second.exitValue(), new String(second.getInputStream().readAllBytes()));
        assertEquals(2, repo.openReader("s").events().size());
    }

    @Test
    void tailDamageIsReportedAndRepairRequiresExclusiveWriterAndExplicitPolicy() throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
        }
        byte[] valid = Files.readAllBytes(root.resolve("s.jsonl"));
        Files.writeString(root.resolve("s.jsonl"), "{\"half\":", StandardOpenOption.APPEND);
        assertEquals(
                "TAIL_DAMAGED",
                assertThrows(HarnessException.class, () -> repo.openReader("s").events()).code());
        assertEquals("TAIL_DAMAGED", repo.inspect("s").diagnostic());
        assertEquals(
                "TAIL_DAMAGED",
                assertThrows(
                                HarnessException.class,
                                () -> repo.openWriter("s", WriteOwnership.EXCLUSIVE))
                        .code());
        List<String> diagnostics = new ArrayList<>();
        JsonlSessionRepository repair =
                new JsonlSessionRepository(
                        root,
                        JsonlSessionRepository.TailPolicy.REPAIR_TAIL,
                        c -> c.force(true),
                        diagnostics::add);
        try (SessionWriter writer = repair.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.flush();
        }
        assertArrayEquals(valid, Files.readAllBytes(root.resolve("s.jsonl")));
        assertEquals(List.of("TAIL_REPAIRED"), diagnostics);
    }

    @Test
    void malformedMiddleLineNeverGetsSilentlyDiscardedByTailRepair() throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session("s", "session.created", Map.of()));
        }
        String valid = Files.readString(root.resolve("s.jsonl"));
        int header = valid.indexOf('\n') + 1;
        Files.writeString(
                root.resolve("s.jsonl"),
                valid.substring(0, header) + "{invalid}\n" + valid.substring(header));
        JsonlSessionRepository repair =
                new JsonlSessionRepository(
                        root,
                        JsonlSessionRepository.TailPolicy.REPAIR_TAIL,
                        c -> c.force(true),
                        code -> {});
        byte[] before = Files.readAllBytes(root.resolve("s.jsonl"));
        assertEquals(
                "MID_LOG_DAMAGED",
                assertThrows(
                                HarnessException.class,
                                () -> repair.openWriter("s", WriteOwnership.EXCLUSIVE))
                        .code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("s.jsonl")));
    }

    @Test
    void futureHeaderIsMetadataReadableButNeverDecodedOrWritten() throws Exception {
        Files.writeString(
                root.resolve("s.jsonl"),
                "{\"format\":\"jharness-jsonl\",\"version\":99,\"sessionId\":\"s\"}\n");
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        byte[] before = Files.readAllBytes(root.resolve("s.jsonl"));
        assertEquals(
                new JsonlSessionRepository.Inspection(99, false, "FUTURE_VERSION_READ_ONLY"),
                repo.inspect("s"));
        assertEquals(
                "FUTURE_VERSION_READ_ONLY",
                assertThrows(
                                HarnessException.class,
                                () -> repo.openWriter("s", WriteOwnership.EXCLUSIVE))
                        .code());
        assertEquals(
                "FUTURE_VERSION_READ_ONLY",
                assertThrows(HarnessException.class, () -> repo.openReader("s").events()).code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("s.jsonl")));
    }

    @Test
    void forceFailurePoisonsWriterAndReportsThroughIndependentDiagnosticChannel() throws Exception {
        List<String> diagnostics = new ArrayList<>();
        int[] forces = {0};
        JsonlSessionRepository repo =
                new JsonlSessionRepository(
                        root,
                        JsonlSessionRepository.TailPolicy.REJECT,
                        c -> {
                            if (++forces[0] > 1) throw new IOException("injected storage failure");
                            c.force(true);
                        },
                        diagnostics::add);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            assertEquals(
                    "CHECKPOINT_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            writer.append(
                                                    SessionEvent.session(
                                                            "s", "session.created", Map.of())))
                            .code());
            assertEquals(
                    "WRITER_POISONED", assertThrows(HarnessException.class, writer::flush).code());
        }
        assertEquals(List.of("CHECKPOINT_FAILED"), diagnostics);
    }

    @Test
    void duplicateWrongScopeAndUnsafeSessionNamesAreRejectedWithoutAppending() throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(root);
        try (SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE)) {
            SessionEvent event = SessionEvent.session("s", "session.created", Map.of());
            writer.append(event);
            assertEquals(
                    "DUPLICATE_EVENT",
                    assertThrows(HarnessException.class, () -> writer.append(event)).code());
            assertEquals(
                    "SESSION_MISMATCH",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            writer.append(
                                                    SessionEvent.session(
                                                            "other", "session.created", Map.of())))
                            .code());
        }
        for (String id : List.of("../outside", "C:escape", "NUL"))
            assertEquals(
                    "INVALID_SESSION_ID",
                    assertThrows(
                                    HarnessException.class,
                                    () -> repo.openWriter(id, WriteOwnership.EXCLUSIVE))
                            .code());
        assertEquals(List.of("s"), repo.list());
    }

    @Test
    void oversizedSparseLogIsRefusedBeforeAllocationAndArtifactsPersistWithScopeAndIntegrity()
            throws Exception {
        try (var channel =
                java.nio.channels.FileChannel.open(
                        root.resolve("large.jsonl"),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
            channel.position(64L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        assertEquals(
                "SESSION_LIMIT",
                assertThrows(
                                HarnessException.class,
                                () -> new JsonlSessionRepository(root).openReader("large").events())
                        .code());
        LocalArtifactStore store =
                new LocalArtifactStore(
                        root.resolve("artifacts"), new SecretRedactor(List.of("secret")));
        ArtifactStore.Artifact artifact = store.put("s", "secret full content");
        assertEquals(
                "[REDACTED] full content",
                new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor())
                        .read("s", artifact.id(), 0, 100));
        assertEquals(
                "ARTIFACT_NOT_FOUND",
                assertThrows(
                                HarnessException.class,
                                () -> store.read("other", artifact.id(), 0, 100))
                        .code());
        assertFalse(Files.exists(root.resolve("artifacts/other")));
        Files.writeString(root.resolve("artifacts/s").resolve(artifact.id() + ".txt"), "tampered");
        assertEquals(
                "ARTIFACT_CORRUPT",
                assertThrows(HarnessException.class, () -> store.read("s", artifact.id(), 0, 100))
                        .code());
    }

    private Process probe() throws IOException {
        String java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString();
        return new ProcessBuilder(
                        java,
                        "-cp",
                        System.getProperty("java.class.path"),
                        WriterProbe.class.getName(),
                        root.toString(),
                        "s")
                .redirectErrorStream(true)
                .start();
    }
}
