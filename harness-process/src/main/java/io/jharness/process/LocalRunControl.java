package io.jharness.process;

import io.jharness.api.*;
import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One authenticated loopback endpoint per run; no process termination by caller-supplied PID. */
public final class LocalRunControl implements AutoCloseable {
    public enum CancelResult {
        REQUESTED(0),
        NOT_FOUND(2),
        ALREADY_EXITED(3),
        UNREACHABLE(4),
        INVALID_STATE(5);
        private final int exitCode;

        CancelResult(int code) {
            exitCode = code;
        }

        public int exitCode() {
            return exitCode;
        }
    }

    private final String runId = UUID.randomUUID().toString();
    private final String token;
    private final Path state;
    private final ServerSocket server;
    private final AtomicBoolean cancelled = new AtomicBoolean(), closed = new AtomicBoolean();
    private final Thread listener;

    public LocalRunControl(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path canonical = directory.toRealPath();
        if (Files.isSymbolicLink(directory)
                || !canonical.equals(directory.toAbsolutePath().normalize()))
            throw new IOException("Run state directory cannot be a link");
        try (var entries = Files.list(canonical)) {
            if (entries.anyMatch(
                    p -> !p.getFileName().toString().matches("[a-f0-9-]{36}\\.(run|tmp)")))
                throw new IOException("Use a dedicated run state directory");
        }
        ownerOnly(canonical, true);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        token = HexFormat.of().formatHex(random);
        state = canonical.resolve(runId + ".run");
        server = new ServerSocket();
        try {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            writeState("RUNNING", token);
        } catch (IOException ex) {
            server.close();
            throw ex;
        }
        listener = Thread.ofVirtual().name("run-control-" + runId).start(this::listen);
    }

    public String runId() {
        return runId;
    }

    public Path stateFile() {
        return state;
    }

    public CancellationToken cancellation() {
        return cancelled::get;
    }

    private void listen() {
        while (!closed.get()) {
            try (Socket connection = server.accept()) {
                connection.setSoTimeout(1000);
                DataInputStream input = new DataInputStream(connection.getInputStream());
                String message = input.readUTF();
                boolean authenticated =
                        MessageDigest.isEqual(
                                ("CANCEL " + token).getBytes(StandardCharsets.UTF_8),
                                message.getBytes(StandardCharsets.UTF_8));
                if (authenticated) cancelled.set(true);
                new DataOutputStream(connection.getOutputStream())
                        .writeUTF(authenticated ? "REQUESTED" : "DENIED");
            } catch (IOException ex) {
                /* timeout or closed listener, no unauthenticated cancellation */
            }
        }
    }

    private void writeState(String status, String credential) throws IOException {
        String body =
                "version=1\nrunId="
                        + runId
                        + "\npid="
                        + ProcessHandle.current().pid()
                        + "\nport="
                        + server.getLocalPort()
                        + "\nhost=127.0.0.1\nstatus="
                        + status
                        + "\ntoken="
                        + credential
                        + "\n";
        Path temporary = state.resolveSibling(runId + ".tmp");
        try {
            Files.createFile(temporary);
            ownerOnly(temporary, false);
            try (FileChannel channel =
                    FileChannel.open(
                            temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var bytes = StandardCharsets.UTF_8.encode(body);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(
                    temporary,
                    state,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void ownerOnly(Path path, boolean directory) throws IOException {
        PosixFileAttributeView posix =
                Files.getFileAttributeView(
                        path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView acl =
                Files.getFileAttributeView(
                        path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Owner-only state permissions unsupported");
        acl.setAcl(
                List.of(
                        AclEntry.newBuilder()
                                .setType(AclEntryType.ALLOW)
                                .setPrincipal(acl.getOwner())
                                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                .build()));
    }

    public static List<String> discover(Path directory) throws IOException {
        if (!Files.exists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(s -> s.matches("[a-f0-9-]{36}\\.run"))
                    .limit(1000)
                    .map(s -> s.substring(0, 36))
                    .sorted()
                    .toList();
        }
    }

    public static CancelResult cancel(Path directory, String runId) {
        if (runId == null
                || !runId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            return CancelResult.INVALID_STATE;
        Path state = directory.resolve(runId + ".run");
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) return CancelResult.NOT_FOUND;
        try {
            if (Files.isSymbolicLink(state)
                    || Files.size(state) > 4096
                    || !Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS))
                return CancelResult.INVALID_STATE;
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(state, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(4097);
                if (bytes.length > 4096) return CancelResult.INVALID_STATE;
                properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            }
            if (!"1".equals(properties.getProperty("version"))
                    || !runId.equals(properties.getProperty("runId")))
                return CancelResult.INVALID_STATE;
            if ("EXITED".equals(properties.getProperty("status")))
                return CancelResult.ALREADY_EXITED;
            String token = properties.getProperty("token", "");
            if (!"RUNNING".equals(properties.getProperty("status"))
                    || !token.matches("[a-f0-9]{64}")) return CancelResult.INVALID_STATE;
            int port = Integer.parseInt(properties.getProperty("port"));
            if (port < 1 || port > 65535) return CancelResult.INVALID_STATE;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                socket.setSoTimeout(1000);
                new DataOutputStream(socket.getOutputStream()).writeUTF("CANCEL " + token);
                return "REQUESTED".equals(new DataInputStream(socket.getInputStream()).readUTF())
                        ? CancelResult.REQUESTED
                        : CancelResult.INVALID_STATE;
            }
        } catch (IllegalArgumentException ex) {
            return CancelResult.INVALID_STATE;
        } catch (IOException ex) {
            return CancelResult.UNREACHABLE;
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        cancelled.set(true);
        server.close();
        try {
            listener.join(1500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        writeState("EXITED", "");
    }
}
