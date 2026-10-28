package io.jharness.workspace;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import io.jharness.api.DirectoryIdentity;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Internal host filesystem support; pins ancestor names and rejects reparse traversal. */
public final class WindowsDirectoryPin implements Closeable {
    interface Kernel32 extends StdCallLibrary {
        Pointer CreateFileW(
                WString path,
                int access,
                int share,
                Pointer security,
                int disposition,
                int flags,
                Pointer template);

        int GetFileInformationByHandle(Pointer handle, Pointer info);

        int GetFileInformationByHandleEx(Pointer handle, int kind, Pointer info, int size);

        int CloseHandle(Pointer handle);

        int CreateHardLinkW(WString target, WString existing, Pointer security);

        int MoveFileExW(WString source, WString target, int flags);
    }

    private static final class Api {
        private static final Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
    }

    private record Key(long volume, long low, long high) {}

    private final Kernel32 api;
    private final List<Pointer> handles = new ArrayList<>();
    private Object key;
    private Path parent, authorizedRoot;
    private Pointer nonemptyGuard;
    private boolean childRenamesAllowed;

    private WindowsDirectoryPin(Kernel32 api) {
        this.api = api;
    }

    public static boolean supported() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    public static boolean nativeKey(Object value) {
        return value instanceof Key;
    }

    public Object key() {
        return key;
    }

    public static WindowsDirectoryPin open(Path root, Object expected) throws IOException {
        return open(root, root, expected);
    }

    public static WindowsDirectoryPin open(Path parent, Path authorizedRoot, Object expected)
            throws IOException {
        if (!supported()) throw new IOException("Windows directory pin unavailable");
        return open(parent, authorizedRoot, expected, Api.INSTANCE);
    }

    static WindowsDirectoryPin open(Path parent, Path authorizedRoot, Object expected, Kernel32 api)
            throws IOException {
        return open(parent, authorizedRoot, expected, api, 1);
    }

    private static WindowsDirectoryPin open(
            Path parent, Path authorizedRoot, Object expected, Kernel32 api, int share)
            throws IOException {
        if (!parent.isAbsolute()
                || !parent.normalize().equals(parent)
                || !parent.startsWith(authorizedRoot)
                || parent.getNameCount() > 128)
            throw new IOException("Invalid pinned directory scope");
        var pin = new WindowsDirectoryPin(api);
        pin.parent = parent;
        pin.authorizedRoot = authorizedRoot;
        try {
            Path at = parent.getRoot();
            pin.add(at, authorizedRoot, expected, share);
            for (Path segment : parent) {
                at = at.resolve(segment);
                pin.add(at, authorizedRoot, expected, share);
            }
            if (pin.key == null) throw new IOException("Authorized root was not pinned");
            return pin;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                pin.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static String nativePath(Path path) {
        String value = path.toString();
        if (value.startsWith("\\\\?\\")) return value;
        return value.startsWith("\\\\") ? "\\\\?\\UNC\\" + value.substring(2) : "\\\\?\\" + value;
    }

    private void add(Path path, Path authorizedRoot, Object expected, int share)
            throws IOException {
        // GENERIC_READ is essential: attribute-only access does not enforce the same sharing.
        Pointer[] opened = {null};
        retryRejectedSharing(
                "open directory",
                () -> {
                    opened[0] =
                            api.CreateFileW(
                                    new WString(nativePath(path)),
                                    0x80000000,
                                    share,
                                    null,
                                    3,
                                    0x02200000,
                                    null);
                    return opened[0] != null && Pointer.nativeValue(opened[0]) != -1L ? 1 : 0;
                },
                false);
        Pointer handle = opened[0];
        handles.add(handle);
        try (var info = new Memory(52);
                var id = new Memory(24)) {
            if (api.GetFileInformationByHandle(handle, info) == 0)
                throw new IOException(
                        "Cannot inspect pinned directory; native error " + Native.getLastError());
            int attributes = info.getInt(0);
            if ((attributes & 0x10) == 0 || (attributes & 0x400) != 0)
                throw new IOException("Pinned ancestor must be an ordinary directory");
            if (path.equals(authorizedRoot)) {
                // FILE_ID_INFO includes the full128-bit ID, including on ReFS.
                if (api.GetFileInformationByHandleEx(handle, 18, id, 24) == 0)
                    throw new IOException(
                            "Stable native directory ID unavailable; error "
                                    + Native.getLastError());
                var actual = new Key(id.getLong(0), id.getLong(8), id.getLong(16));
                if (expected != null && !expected.equals(actual))
                    throw new IOException("Pinned directory identity changed");
                key = actual;
            }
        }
    }

    public static DirectoryIdentity bindPrivate(Path root) throws IOException {
        if (!supported()) return DirectoryIdentity.bind(root);
        try (var pin = open(root, null)) {
            pin.allowChildRenames();
            Path marker = root.resolve(".directory-identity");
            if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                Path staged = Files.createTempFile(root, ".identity-", ".tmp");
                try {
                    try (var channel =
                            java.nio.channels.FileChannel.open(
                                    staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
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
                    pin.retryRejectedSharing(
                            "publish directory anchor",
                            () ->
                                    pin.api.CreateHardLinkW(
                                            new WString(nativePath(marker)),
                                            new WString(nativePath(staged)),
                                            null),
                            true);
                } finally {
                    Files.deleteIfExists(staged);
                }
            }
            var identity = DirectoryIdentity.read(root);
            return new DirectoryIdentity(pin.key(), identity.created(), identity.anchor());
        }
    }

    @FunctionalInterface
    private interface NativeAction {
        int invoke();
    }

    /** Only a sharing violation from a rejected open/atomic metadata operation is retried. */
    private void retryRejectedSharing(
            String operation, NativeAction action, boolean existingAllowed) throws IOException {
        long deadline = System.nanoTime() + 250_000_000L;
        while (true) {
            if (action.invoke() != 0) return;
            int error = Native.getLastError();
            if (error == 2 || error == 3) throw new NoSuchFileException(operation);
            if (existingAllowed && (error == 80 || error == 183)) return;
            if (error != 32 || System.nanoTime() >= deadline)
                throw new IOException("Cannot " + operation + "; native error " + error);
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Directory metadata wait interrupted", interrupted);
            }
        }
    }

    /** Same-directory derived cache only; no retry of an unknown user mutation. */
    public void publishCache(Path source, Path target) throws IOException {
        if (!childRenamesAllowed
                || !source.getParent().equals(parent)
                || !target.getParent().equals(parent))
            throw new IOException("Invalid cache publication scope");
        retryRejectedSharing(
                "publish cache",
                () ->
                        api.MoveFileExW(
                                new WString(nativePath(source)),
                                new WString(nativePath(target)),
                                1),
                false);
    }

    public static void checkPrivate(Path root, DirectoryIdentity expected) throws IOException {
        if (!nativeKey(expected.fileKey())) throw new IOException("Native identity required");
        try (var pin = open(root, expected.fileKey())) {
            var actual = DirectoryIdentity.read(root);
            if (!Objects.equals(pin.key(), expected.fileKey())
                    || !Objects.equals(actual.anchor(), expected.anchor())
                    || !root.toRealPath().equals(root))
                throw new IOException("Private directory identity changed");
        }
    }

    /** Write operations only: keep an undeletable child before permitting child rename APIs. */
    public void allowChildRenames() throws IOException {
        if (handles.isEmpty()) throw new IOException("Directory pin closed");
        if (childRenamesAllowed) return;
        Path sentinel = parent.resolve(".jharness-pin-" + UUID.randomUUID() + ".tmp");
        nonemptyGuard =
                api.CreateFileW(
                        new WString(nativePath(sentinel)),
                        0x80010000,
                        1,
                        null,
                        1,
                        0x04200100,
                        null);
        if (nonemptyGuard == null || Pointer.nativeValue(nonemptyGuard) == -1L) {
            nonemptyGuard = null;
            throw new IOException(
                    "Cannot protect nonempty directory; native error " + Native.getLastError());
        }
        // Old pins remain until all replacement pins exist. Ancestors retain their pinned child;
        // the final directory retains this undeletable, OS-delete-on-close guard against reparse.
        var next = open(parent, authorizedRoot, key, api, 3);
        var previous = new ArrayList<>(handles);
        handles.clear();
        handles.addAll(next.handles);
        next.handles.clear();
        IOException failure = null;
        for (int i = previous.size() - 1; i >= 0; i--)
            if (api.CloseHandle(previous.get(i)) == 0) {
                var error = new IOException("Cannot release initial directory pin");
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        if (failure != null) throw failure;
        childRenamesAllowed = true;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        if (nonemptyGuard != null) {
            if (api.CloseHandle(nonemptyGuard) == 0)
                failure = new IOException("Cannot close nonempty directory guard");
            nonemptyGuard = null;
        }
        for (int i = handles.size() - 1; i >= 0; i--) {
            if (api.CloseHandle(handles.get(i)) == 0) {
                var error =
                        new IOException(
                                "Cannot close directory pin; native error "
                                        + Native.getLastError());
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        handles.clear();
        if (failure != null) throw failure;
    }
}
