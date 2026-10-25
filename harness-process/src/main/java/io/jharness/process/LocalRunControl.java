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
        OUTCOME_UNKNOWN(6),
        UNSUPPORTED_STATE(7),
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
    private volatile boolean stateExited;
    private static final String UUID_PATTERN =
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";

    public LocalRunControl(Path directory) throws IOException {
        Files.createDirectories(directory, privateAttributes(directory, true));
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

    public void requestCancellation() {
        cancelled.set(true);
    }

    private void listen() {
        while (!closed.get()) {
            try (Socket connection = server.accept()) {
                connection.setSoTimeout(500);
                DataInputStream input = new DataInputStream(connection.getInputStream());
                long deadline =
                        System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(500);
                byte[] header = new byte[2];
                for (int i = 0; i < header.length; i++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new SocketTimeoutException("Control frame deadline");
                    connection.setSoTimeout(
                            (int)
                                    Math.max(
                                            1,
                                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                    remaining)));
                    int value = input.read();
                    if (value < 0) throw new EOFException();
                    header[i] = (byte) value;
                }
                int length = (header[0] & 255) << 8 | header[1] & 255;
                if (length > 256) throw new IOException("Invalid control frame length");
                byte[] frame = new byte[length + 2];
                frame[0] = (byte) (length >>> 8);
                frame[1] = (byte) length;
                int at = 2;
                while (at < frame.length) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new SocketTimeoutException("Control frame deadline");
                    connection.setSoTimeout(
                            (int)
                                    Math.max(
                                            1,
                                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                    remaining)));
                    int count = input.read(frame, at, frame.length - at);
                    if (count < 0) throw new EOFException();
                    at += count;
                }
                String message = new DataInputStream(new ByteArrayInputStream(frame)).readUTF();
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
                "version=2\nrunId="
                        + runId
                        + "\npid="
                        + ProcessHandle.current().pid()
                        + "\nprocessStart="
                        + ProcessHandle.current()
                                .info()
                                .startInstant()
                                .orElseThrow(
                                        () -> new IOException("Process start identity unavailable"))
                        + "\nport="
                        + server.getLocalPort()
                        + "\nhost=127.0.0.1\nstatus="
                        + status
                        + "\ntoken="
                        + credential
                        + "\n";
        Path temporary = state.resolveSibling(runId + ".tmp");
        try {
            Files.createFile(temporary, privateAttributes(temporary, false));
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

    private static FileAttribute<?>[] privateAttributes(Path path, boolean directory)
            throws IOException {
        Path existing = path.toAbsolutePath();
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) throw new IOException("Owner-only state permissions unsupported");
        if (Files.getFileStore(existing).supportsFileAttributeView("posix"))
            return new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))
            };
        var acl = Files.getFileAttributeView(existing, AclFileAttributeView.class);
        if (acl == null) throw new IOException("Owner-only state permissions unsupported");
        var entry =
                AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(acl.getOwner())
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        List<AclEntry> entries = List.of(entry.build());
        return new FileAttribute<?>[] {
            new FileAttribute<List<AclEntry>>() {
                public String name() {
                    return "acl:acl";
                }

                public List<AclEntry> value() {
                    return entries;
                }
            }
        };
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
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .filter(s -> s.matches(UUID_PATTERN + "\\.run"))
                    .limit(1000)
                    .map(s -> s.substring(0, 36))
                    .sorted()
                    .toList();
        }
    }

    public static CancelResult cancel(Path directory, String runId) {
        if (runId == null || !runId.matches(UUID_PATTERN)) return CancelResult.INVALID_STATE;
        boolean delivered = false;
        Path state;
        try {
            Path canonical = directory.toRealPath();
            if (!canonical.equals(directory.toAbsolutePath().normalize())
                    || Files.isSymbolicLink(directory)) return CancelResult.INVALID_STATE;
            state = canonical.resolve(runId + ".run");
        } catch (NoSuchFileException ex) {
            return CancelResult.NOT_FOUND;
        } catch (IOException | RuntimeException ex) {
            return CancelResult.INVALID_STATE;
        }
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
            if (!Set.of("1", "2").contains(properties.getProperty("version", "")))
                return CancelResult.UNSUPPORTED_STATE;
            if (!runId.equals(properties.getProperty("runId"))) return CancelResult.INVALID_STATE;
            if ("EXITED".equals(properties.getProperty("status")))
                return CancelResult.ALREADY_EXITED;
            String token = properties.getProperty("token", "");
            if (!"RUNNING".equals(properties.getProperty("status"))
                    || !token.matches("[a-f0-9]{64}")) return CancelResult.INVALID_STATE;
            if (!"127.0.0.1".equals(properties.getProperty("host")))
                return CancelResult.INVALID_STATE;
            long pid = Long.parseLong(properties.getProperty("pid"));
            var process = ProcessHandle.of(pid);
            if (process.isEmpty() || !process.get().isAlive()) return CancelResult.ALREADY_EXITED;
            String started = properties.getProperty("processStart");
            if (started == null) return CancelResult.UNSUPPORTED_STATE;
            if (!process.get()
                    .info()
                    .startInstant()
                    .map(Object::toString)
                    .orElse("")
                    .equals(started)) return CancelResult.INVALID_STATE;
            int port = Integer.parseInt(properties.getProperty("port"));
            if (port < 1 || port > 65535) return CancelResult.INVALID_STATE;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                socket.setSoTimeout(1000);
                new DataOutputStream(socket.getOutputStream()).writeUTF("CANCEL " + token);
                delivered = true;
                return "REQUESTED".equals(new DataInputStream(socket.getInputStream()).readUTF())
                        ? CancelResult.REQUESTED
                        : CancelResult.INVALID_STATE;
            }
        } catch (IllegalArgumentException ex) {
            return CancelResult.INVALID_STATE;
        } catch (IOException ex) {
            return delivered ? CancelResult.OUTCOME_UNKNOWN : CancelResult.UNREACHABLE;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (stateExited) return;
        closed.set(true);
        cancelled.set(true);
        IOException primary = null;
        boolean interrupted = Thread.interrupted();
        try {
            server.close();
        } catch (IOException ex) {
            primary = ex;
        }
        try {
            listener.join(1500);
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        try {
            writeState("EXITED", "");
            stateExited = true;
        } catch (IOException ex) {
            if (primary == null) primary = ex;
            else primary.addSuppressed(ex);
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (primary != null) throw primary;
    }
}
