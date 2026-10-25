package io.jharness.web;

import io.jharness.api.*;
import io.jharness.session.JsonlSessionRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Fixed workspace ownership, independent of catalog. State is a bounded write-ahead pointer. */
final class WorkspaceLease implements AutoCloseable {
    static final String LOCK = "web-workspace-owner";
    private final SessionWriter owner;
    private final Path directory, file;
    private Map<String, Object> pending;

    WorkspaceLease(JsonlSessionRepository sessions) throws IOException {
        directory = sessions.directory();
        file = directory.resolve("web-workspace-owner.json");
        owner = sessions.openWriter(LOCK, WriteOwnership.EXCLUSIVE);
        try {
            check();
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes;
                try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(65537);
                }
                if (bytes.length > 65536) throw new IOException("Lease state limit");
                var state = WebJson.decode(bytes);
                if (!Integer.valueOf(1).equals(state.get("version")))
                    throw new IOException("Future lease");
                pending = Values.object(state.get("pending"));
                validate(pending);
            } else pending = Map.of();
        } catch (IOException | RuntimeException ex) {
            owner.close();
            throw ex;
        }
    }

    Map<String, Object> pending() {
        return pending;
    }

    void accept(Map<String, Object> accepted, String catalogId) throws IOException {
        if (!pending.isEmpty())
            throw new HarnessException("WORKSPACE_BUSY", "Workspace has unsettled admission");
        var next = new LinkedHashMap<>(accepted);
        next.put("catalogKey", catalogId);
        validate(next);
        save(next);
    }

    void clear(String task, String catalogId) throws IOException {
        if (pending.isEmpty()) return;
        if (!task.equals(pending.get("taskId")) || !catalogId.equals(pending.get("catalogKey")))
            throw new HarnessException("ENVIRONMENT_MISMATCH", "Wrong workspace admission");
        save(Map.of());
    }

    private static void validate(Map<String, Object> state) {
        if (state.isEmpty()) return;
        if (!(state.get("taskId") instanceof String task)
                || !task.matches("[A-Za-z0-9_-]{1,128}")
                || !(state.get("catalogKey") instanceof String catalog)
                || !catalog.matches("[a-f0-9]{64}")
                || !(state.get("requestHash") instanceof String hash)
                || !hash.matches("[a-f0-9]{64}"))
            throw new HarnessException(
                    "INVALID_WORKSPACE_LEASE", "Invalid workspace admission identity");
    }

    private void check() throws IOException {
        if (!directory.toRealPath().equals(directory.toAbsolutePath().normalize())
                || Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                        && (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                || Files.isSymbolicLink(file)))
            throw new IOException("Unsafe workspace lease path");
    }

    private void save(Map<String, Object> next) throws IOException {
        owner.flush();
        check();
        byte[] bytes = WebJson.encodeState(Map.of("version", 1, "pending", next));
        if (bytes.length > 65536) throw new IOException("Lease state limit");
        Path temp = Files.createTempFile(directory, ".workspace-lease-", ".tmp");
        try {
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            check();
            Files.move(
                    temp,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            pending = Values.snapshot(next);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public void close() {
        owner.close();
    }
}
