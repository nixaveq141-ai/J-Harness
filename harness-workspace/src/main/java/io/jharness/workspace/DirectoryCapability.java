package io.jharness.workspace;

import com.sun.jna.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Short-lived directory authority. All names below it are single relative components. */
public final class DirectoryCapability implements Closeable {
    interface Posix extends Library {
        int open(String path, int flags, int mode);

        int openat(int directory, String name, int flags, int mode);

        int close(int descriptor);

        int mkdirat(int directory, String name, int mode);

        int unlinkat(int directory, String name, int flags);

        int renameat(int from, String source, int to, String target);

        int linkat(int from, String source, int to, String target, int flags);

        int renameat2(int from, String source, int to, String target, int flags);

        int renameatx_np(int from, String source, int to, String target, int flags);

        int dup(int descriptor);

        Pointer fdopendir(int descriptor);

        Pointer readdir(Pointer directory);

        int closedir(Pointer directory);
    }

    private static final boolean MAC = System.getProperty("os.name").startsWith("Mac");

    private static final class Api {
        // Darwin x86_64 retains legacy32-bit inode symbols; arm64 uses the64-bit ABI directly.
        static final Posix INSTANCE =
                Native.load(
                        "c",
                        Posix.class,
                        Map.of(
                                Library.OPTION_FUNCTION_MAPPER,
                                (FunctionMapper)
                                        (library, method) ->
                                                MAC
                                                                && Set.of("amd64", "x86_64")
                                                                        .contains(
                                                                                System.getProperty(
                                                                                        "os.arch"))
                                                                && Set.of("fdopendir", "readdir")
                                                                        .contains(method.getName())
                                                        ? method.getName() + "$INODE64"
                                                        : method.getName()));
    }

    private final Path directory;
    private final WindowsDirectoryPin windows;
    private int descriptor;
    private boolean closed;

    private DirectoryCapability(Path directory, WindowsDirectoryPin windows, int descriptor) {
        this.directory = directory;
        this.windows = windows;
        this.descriptor = descriptor;
    }

    public static Object identity(Path root) throws IOException {
        if (WindowsDirectoryPin.supported())
            try (var pin = WindowsDirectoryPin.open(root, null)) {
                return pin.key();
            }
        try (var capability = open(root, null, root, false)) {
            return capability.self().fileKey();
        }
    }

    public static io.jharness.api.DirectoryIdentity bindPrivate(Path root) throws IOException {
        if (WindowsDirectoryPin.supported()) return WindowsDirectoryPin.bindPrivate(root);
        try (var held = open(root, null, root, false)) {
            Path marker = Path.of(".directory-identity");
            if (!held.exists(marker)) {
                Path temporary = Path.of(".identity-" + UUID.randomUUID() + ".tmp");
                try {
                    try (var channel =
                            held.channel(
                                    temporary,
                                    Set.of(
                                            StandardOpenOption.CREATE_NEW,
                                            StandardOpenOption.WRITE,
                                            LinkOption.NOFOLLOW_LINKS))) {
                        var bytes =
                                java.nio.ByteBuffer.wrap(
                                        UUID.randomUUID()
                                                .toString()
                                                .getBytes(
                                                        java.nio.charset.StandardCharsets
                                                                .US_ASCII));
                        while (bytes.hasRemaining()) channel.write(bytes);
                        channel.force(true);
                    }
                    try {
                        held.move(temporary, marker, false);
                    } catch (FileAlreadyExistsException winner) {
                        /* The other complete anchor wins. */
                    }
                } finally {
                    if (held.exists(temporary)) held.delete(temporary, false);
                }
            }
            return held.privateIdentity();
        }
    }

    private io.jharness.api.DirectoryIdentity privateIdentity() throws IOException {
        var attrs = self();
        String anchor;
        try (var channel =
                        channel(
                                Path.of(".directory-identity"),
                                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var input = java.nio.channels.Channels.newInputStream(channel)) {
            anchor = new String(input.readNBytes(37), java.nio.charset.StandardCharsets.US_ASCII);
        }
        if (!anchor.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new io.jharness.api.DirectoryIdentity.InvalidAnchorException();
        return new io.jharness.api.DirectoryIdentity(attrs.fileKey(), attrs.creationTime(), anchor);
    }

    public static void checkPrivate(Path root, io.jharness.api.DirectoryIdentity expected)
            throws IOException {
        if (WindowsDirectoryPin.nativeKey(expected.fileKey())) {
            WindowsDirectoryPin.checkPrivate(root, expected);
            return;
        }
        try (var held = open(root, expected.fileKey(), root, false)) {
            if (!expected.equals(held.privateIdentity()))
                throw new IOException("Private directory identity changed");
        }
    }

    public static DirectoryCapability open(Path root, Object key, Path parent, boolean write)
            throws IOException {
        if (!root.isAbsolute()
                || !root.normalize().equals(root)
                || !parent.startsWith(root)
                || !parent.normalize().equals(parent)
                || parent.getNameCount() > 128)
            throw new IOException("Invalid directory capability scope");
        if (WindowsDirectoryPin.supported()) {
            var pin = WindowsDirectoryPin.open(parent, root, key);
            try {
                if (write) pin.allowChildRenames();
                return new DirectoryCapability(parent, pin, -1);
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    pin.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
        if (!MAC && !System.getProperty("os.name").startsWith("Linux"))
            throw new IOException("Directory capabilities unavailable on this platform");
        int fd = Api.INSTANCE.open("/", directoryFlags(), 0);
        if (fd < 0) throw error("open volume");
        try {
            Path at = parent.getRoot();
            if (at.equals(root)) verify(fd, key);
            for (Path part : parent) {
                int next = Api.INSTANCE.openat(fd, component(part), directoryFlags(), 0);
                if (next < 0) throw error("open relative directory");
                int previous = fd;
                fd = next;
                if (Api.INSTANCE.close(previous) != 0) throw error("close ancestor");
                at = at.resolve(part);
                if (at.equals(root)) verify(fd, key);
            }
            var result = new DirectoryCapability(parent, null, fd);
            fd = -1;
            return result;
        } finally {
            if (fd >= 0 && Api.INSTANCE.close(fd) != 0) throw error("close rejected directory");
        }
    }

    private static int directoryFlags() {
        return (MAC ? 0x100000 : 0x10000) | nofollow() | cloexec();
    }

    private static int nofollow() {
        return MAC ? 0x100 : 0x20000;
    }

    private static int cloexec() {
        return MAC ? 0x1000000 : 0x80000;
    }

    private static Path descriptorPath(int fd) {
        return Path.of("/dev/fd/" + fd);
    }

    private static BasicFileAttributes stat(int fd) throws IOException {
        return Files.readAttributes(descriptorPath(fd), BasicFileAttributes.class);
    }

    private static void verify(int fd, Object key) throws IOException {
        var actual = stat(fd);
        if (!actual.isDirectory()
                || actual.fileKey() == null
                || key != null && !key.equals(actual.fileKey()))
            throw new IOException("Opened directory identity changed");
    }

    private static IOException error(String action) {
        int errno = Native.getLastError();
        if (errno == 2) return new NoSuchFileException(action);
        if (errno == 17) return new FileAlreadyExistsException(action);
        return new IOException("Cannot " + action + "; native error " + errno);
    }

    private static String component(Path name) throws IOException {
        String value = name.toString();
        if (name.isAbsolute()
                || name.getNameCount() != 1
                || value.isEmpty()
                || value.equals(".")
                || value.equals("..")
                || value.indexOf('\0') >= 0
                || value.contains("/")
                || value.contains("\\")) throw new IOException("Expected one relative name");
        return value;
    }

    private void ensureOpen() throws IOException {
        if (closed || windows == null && descriptor < 0)
            throw new IOException("Directory capability closed");
    }

    public Path directory() {
        return directory;
    }

    public BasicFileAttributes self() throws IOException {
        ensureOpen();
        return windows == null
                ? stat(descriptor)
                : Files.readAttributes(
                        directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    public BasicFileAttributes attributes(Path name) throws IOException {
        ensureOpen();
        String relative = component(name);
        if (windows != null)
            return Files.readAttributes(
                    directory.resolve(relative),
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        int fd =
                Api.INSTANCE.openat(
                        descriptor, relative, nofollow() | cloexec() | (MAC ? 4 : 0x800), 0);
        if (fd < 0) throw error("inspect relative entry");
        try {
            return stat(fd);
        } finally {
            if (Api.INSTANCE.close(fd) != 0) throw error("close inspected entry");
        }
    }

    public boolean exists(Path name) throws IOException {
        try {
            attributes(name);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    public FileChannel channel(Path name, Set<? extends OpenOption> options) throws IOException {
        ensureOpen();
        String relative = component(name);
        boolean write = options.contains(StandardOpenOption.WRITE),
                create = options.contains(StandardOpenOption.CREATE_NEW);
        var allowed =
                Set.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.CREATE,
                        LinkOption.NOFOLLOW_LINKS);
        if (!allowed.containsAll(options))
            throw new IOException("Unsupported capability channel options");
        if (windows != null) {
            try {
                if (!attributes(name).isRegularFile())
                    throw new IOException("Expected regular file");
            } catch (NoSuchFileException missing) {
                if (!create && !options.contains(StandardOpenOption.CREATE)) throw missing;
            }
            return FileChannel.open(directory.resolve(relative), options);
        }
        int flags =
                (write ? options.contains(StandardOpenOption.READ) ? 2 : 1 : 0)
                        | nofollow()
                        | cloexec()
                        | (MAC ? 4 : 0x800);
        if (create) flags |= MAC ? 0xA00 : 0xC0;
        else if (options.contains(StandardOpenOption.CREATE)) flags |= MAC ? 0x200 : 0x40;
        int fd = Api.INSTANCE.openat(descriptor, relative, flags, 0600);
        if (fd < 0) throw error("open relative file");
        FileChannel channel = null;
        try {
            var before = stat(fd);
            if (!before.isRegularFile()) throw new IOException("Expected regular file");
            // fdescfs/procfs duplicates an already authorized descriptor; no user name is resolved.
            channel =
                    FileChannel.open(
                            descriptorPath(fd),
                            write
                                    ? options.contains(StandardOpenOption.READ)
                                            ? Set.of(
                                                    StandardOpenOption.READ,
                                                    StandardOpenOption.WRITE)
                                            : Set.of(StandardOpenOption.WRITE)
                                    : Set.of(StandardOpenOption.READ));
            if (!Objects.equals(before.fileKey(), stat(fd).fileKey()))
                throw new IOException("Descriptor identity changed");
            FileChannel result = channel;
            channel = null;
            return result;
        } finally {
            if (channel != null) channel.close();
            if (Api.INSTANCE.close(fd) != 0) throw error("close original file descriptor");
        }
    }

    public void createDirectory(Path name) throws IOException {
        ensureOpen();
        String relative = component(name);
        if (windows != null) Files.createDirectory(directory.resolve(relative));
        else if (Api.INSTANCE.mkdirat(descriptor, relative, 0700) != 0)
            throw error("create relative directory");
    }

    public Map<String, Object> copyMetadata(Path source, Path target) throws IOException {
        ensureOpen();
        String from = component(source), to = component(target);
        if (windows != null)
            return io.jharness.api.AtomicFileMetadata.copyForWorkspace(
                    directory.resolve(from), directory.resolve(to));
        int left = Api.INSTANCE.openat(descriptor, from, nofollow() | cloexec(), 0);
        if (left < 0) throw error("open metadata source");
        try {
            int right = Api.INSTANCE.openat(descriptor, to, nofollow() | cloexec(), 0);
            if (right < 0) throw error("open metadata target");
            try {
                if (!stat(left).isRegularFile() || !stat(right).isRegularFile())
                    throw new IOException("Metadata requires regular files");
                var view =
                        Files.getFileAttributeView(
                                descriptorPath(left),
                                java.nio.file.attribute.PosixFileAttributeView.class);
                var destination =
                        Files.getFileAttributeView(
                                descriptorPath(right),
                                java.nio.file.attribute.PosixFileAttributeView.class);
                if (view == null || destination == null)
                    throw new IOException("Descriptor POSIX metadata unavailable");
                var attrs = view.readAttributes();
                try {
                    destination.setOwner(attrs.owner());
                } catch (IOException unavailable) {
                    /* Report verified ownership below. */
                }
                try {
                    destination.setGroup(attrs.group());
                } catch (IOException unavailable) {
                    /* Report verified ownership below. */
                }
                destination.setPermissions(attrs.permissions());
                var actual = destination.readAttributes();
                if (!actual.permissions().equals(attrs.permissions()))
                    throw new IOException("Permissions not preserved");
                if (actual.owner().equals(attrs.owner()) && actual.group().equals(attrs.group()))
                    return Map.of();
                return Map.of(
                        "ownerPreserved",
                        actual.owner().equals(attrs.owner()),
                        "groupPreserved",
                        actual.group().equals(attrs.group()),
                        "permissionsPreserved",
                        true,
                        "ownershipNote",
                        "POSIX ownership reassignment unavailable; creating identity/group retained");
            } finally {
                if (Api.INSTANCE.close(right) != 0) throw error("close metadata target");
            }
        } finally {
            if (Api.INSTANCE.close(left) != 0) throw error("close metadata source");
        }
    }

    public void delete(Path name, boolean isDirectory) throws IOException {
        ensureOpen();
        String relative = component(name);
        if (windows != null) Files.delete(directory.resolve(relative));
        else if (Api.INSTANCE.unlinkat(descriptor, relative, isDirectory ? (MAC ? 0x80 : 0x200) : 0)
                != 0) throw error("delete relative entry");
    }

    public void move(Path source, Path target, boolean replace) throws IOException {
        ensureOpen();
        String from = component(source), to = component(target);
        if (windows != null) {
            if (replace)
                Files.move(
                        directory.resolve(from),
                        directory.resolve(to),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            else Files.move(directory.resolve(from), directory.resolve(to));
        } else if (replace) {
            if (Api.INSTANCE.renameat(descriptor, from, descriptor, to) != 0)
                throw error("replace relative file");
        } else {
            // No-clobber publication of regular files. Never emulate it with exists+rename.
            if (Api.INSTANCE.linkat(descriptor, from, descriptor, to, 0) != 0)
                throw error("publish relative file");
            if (Api.INSTANCE.unlinkat(descriptor, from, 0) != 0)
                throw error("unlink published temporary");
        }
    }

    public void moveDirectory(Path source, Path target) throws IOException {
        ensureOpen();
        String from = component(source), to = component(target);
        if (windows != null) Files.move(directory.resolve(from), directory.resolve(to));
        else {
            int result =
                    MAC
                            ? Api.INSTANCE.renameatx_np(descriptor, from, descriptor, to, 4)
                            : Api.INSTANCE.renameat2(descriptor, from, descriptor, to, 1);
            if (result != 0) throw error("publish relative directory without replacement");
        }
    }

    public DirectoryStream<Path> entries() throws IOException {
        ensureOpen();
        if (windows != null) return Files.newDirectoryStream(directory);
        int copy = Api.INSTANCE.openat(descriptor, ".", directoryFlags(), 0);
        if (copy < 0) throw error("duplicate directory");
        Pointer stream = Api.INSTANCE.fdopendir(copy);
        if (stream == null) {
            Api.INSTANCE.close(copy);
            throw error("enumerate directory");
        }
        return new DirectoryStream<>() {
            boolean closed, iterated;

            public Iterator<Path> iterator() {
                if (closed || iterated)
                    throw new IllegalStateException("Directory stream already used");
                iterated = true;
                return new Iterator<>() {
                    Path next;
                    boolean ended;

                    public boolean hasNext() {
                        if (closed) throw new IllegalStateException("Directory stream closed");
                        while (next == null && !ended) {
                            Native.setLastError(0);
                            Pointer entry = Api.INSTANCE.readdir(stream);
                            if (entry == null) {
                                int errno = Native.getLastError();
                                ended = true;
                                if (errno != 0)
                                    throw new DirectoryIteratorException(
                                            new IOException(
                                                    "Cannot enumerate directory; native error "
                                                            + errno));
                                break;
                            }
                            int offset = MAC ? 21 : 19,
                                    length = Short.toUnsignedInt(entry.getShort(16));
                            if (length <= offset || length > 4096)
                                throw new DirectoryIteratorException(
                                        new IOException("Invalid native directory entry"));
                            byte[] bytes = entry.getByteArray(offset, length - offset);
                            int end = 0;
                            while (end < bytes.length && bytes[end] != 0) end++;
                            if (end == bytes.length)
                                throw new DirectoryIteratorException(
                                        new IOException("Unterminated directory entry"));
                            String name =
                                    new String(
                                            bytes, 0, end, java.nio.charset.StandardCharsets.UTF_8);
                            if (name.equals(".") || name.equals("..")) continue;
                            next = directory.resolve(name);
                        }
                        return next != null;
                    }

                    public Path next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        Path result = next;
                        next = null;
                        return result;
                    }
                };
            }

            public void close() throws IOException {
                if (!closed) {
                    closed = true;
                    if (Api.INSTANCE.closedir(stream) != 0) throw error("close enumeration");
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (windows != null) windows.close();
        else if (descriptor >= 0) {
            int owned = descriptor;
            descriptor = -1;
            if (Api.INSTANCE.close(owned) != 0) throw error("close directory capability");
        }
    }
}
