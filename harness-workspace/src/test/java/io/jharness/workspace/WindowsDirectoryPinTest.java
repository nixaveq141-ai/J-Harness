package io.jharness.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsDirectoryPinTest {
    @TempDir Path root;

    static final class Api implements WindowsDirectoryPin.Kernel32 {
        final Map<Long, String> live = new LinkedHashMap<>();
        final List<Long> closed = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        long next;
        int generation, failOpenAt, reparseAt, failMoveCount, moveError = 32, moves;
        long closeFailure;

        public Pointer CreateFileW(
                WString path,
                int access,
                int share,
                Pointer security,
                int disposition,
                int flags,
                Pointer template) {
            long id = ++next;
            events.add(disposition + ":" + share + ":" + access + ":" + flags);
            assertNull(security);
            assertNull(template);
            if (id == failOpenAt) {
                Native.setLastError(5);
                return new Pointer(-1);
            }
            live.put(id, path.toString());
            return new Pointer(id);
        }

        public int GetFileInformationByHandle(Pointer handle, Pointer info) {
            info.setInt(0, Pointer.nativeValue(handle) == reparseAt ? 0x410 : 0x10);
            return 1;
        }

        public int GetFileInformationByHandleEx(Pointer handle, int kind, Pointer info, int size) {
            assertEquals(18, kind);
            assertEquals(24, size);
            info.setLong(0, 7);
            info.setLong(8, live.get(Pointer.nativeValue(handle)).hashCode());
            info.setLong(16, generation);
            return 1;
        }

        public int CloseHandle(Pointer handle) {
            long value = Pointer.nativeValue(handle);
            assertNotNull(live.remove(value));
            closed.add(value);
            if (value == closeFailure) {
                Native.setLastError(5);
                return 0;
            }
            return 1;
        }

        public int CreateHardLinkW(WString target, WString existing, Pointer security) {
            return 1;
        }

        public int MoveFileExW(WString source, WString target, int flags) {
            assertEquals(1, flags);
            moves++;
            if (failMoveCount-- > 0) {
                Native.setLastError(moveError);
                return 0;
            }
            return 1;
        }
    }

    @Test
    void rootUsesReadAccessNoDeleteShareAndFullNativeIdentity() throws Exception {
        var api = new Api();
        Object key;
        try (var pin = WindowsDirectoryPin.open(root, root, null, api)) {
            key = pin.key();
            assertTrue(WindowsDirectoryPin.nativeKey(key));
            assertEquals(root.getNameCount() + 1, api.live.size());
            assertTrue(api.events.stream().allMatch(v -> v.startsWith("3:1:-2147483648:")));
        }
        assertTrue(api.live.isEmpty());
        int count = api.closed.size();
        try (var pin = WindowsDirectoryPin.open(root, root, key, api)) {
            assertEquals(key, pin.key());
        }
        assertEquals(2 * count, api.closed.size());
        assertTrue(api.live.isEmpty());
    }

    @Test
    void reparseAncestorRejectsAndClosesPartialChainInReverse() {
        var api = new Api();
        api.reparseAt = 2;
        assertThrows(IOException.class, () -> WindowsDirectoryPin.open(root, root, null, api));
        assertEquals(List.of(2L, 1L), api.closed);
        assertTrue(api.live.isEmpty());
    }

    @Test
    void reusedNameCannotSubstituteAnotherNativeId() throws Exception {
        var api = new Api();
        Object key;
        try (var pin = WindowsDirectoryPin.open(root, root, null, api)) {
            key = pin.key();
        }
        api.generation++;
        assertThrows(IOException.class, () -> WindowsDirectoryPin.open(root, root, key, api));
        assertTrue(api.live.isEmpty());
    }

    @Test
    void openAndCloseFailuresStillAttemptAllOwnedCleanup() throws Exception {
        var api = new Api();
        api.failOpenAt = 3;
        assertThrows(IOException.class, () -> WindowsDirectoryPin.open(root, root, null, api));
        assertEquals(List.of(2L, 1L), api.closed);
        assertTrue(api.live.isEmpty());
        var closingApi = new Api();
        var pin = WindowsDirectoryPin.open(root, root, null, closingApi);
        int count = closingApi.live.size();
        closingApi.closeFailure = 2;
        assertThrows(IOException.class, pin::close);
        assertEquals(count, closingApi.closed.size());
        assertTrue(closingApi.live.isEmpty());
        pin.close();
        assertEquals(count, closingApi.closed.size());
    }

    @Test
    void writePhasePinsNonemptyChildBeforeChangingDirectorySharing() throws Exception {
        var api = new Api();
        try (var pin = WindowsDirectoryPin.open(root, root, null, api)) {
            assertTrue(api.events.stream().allMatch(v -> v.startsWith("3:1:")));
            pin.allowChildRenames();
            int count = api.events.size();
            pin.allowChildRenames();
            assertEquals(count, api.events.size());
            int guard = -1, shared = -1;
            for (int i = 0; i < api.events.size(); i++) {
                String item = api.events.get(i);
                if (item.startsWith("1:1:")) {
                    guard = i;
                    String[] parts = item.split(":");
                    assertTrue((Integer.parseInt(parts[2]) & 0x10000) != 0);
                    assertTrue((Integer.parseInt(parts[3]) & 0x4000000) != 0);
                }
                if (item.startsWith("3:3:") && shared < 0) shared = i;
            }
            assertTrue(guard >= 0 && shared > guard);
        }
        assertTrue(api.live.isEmpty());
    }

    @Test
    void onlyKnownRejectedSharingGetsBoundedCacheRetries() throws Exception {
        var api = new Api();
        try (var pin = WindowsDirectoryPin.open(root, root, null, api)) {
            pin.allowChildRenames();
            api.failMoveCount = 2;
            pin.publishCache(root.resolve("source"), root.resolve("target"));
            assertEquals(3, api.moves);
            api.moves = 0;
            api.failMoveCount = 3;
            api.moveError = 5;
            assertThrows(
                    IOException.class,
                    () -> pin.publishCache(root.resolve("source"), root.resolve("target")));
            assertEquals(1, api.moves);
            assertThrows(
                    IOException.class,
                    () ->
                            pin.publishCache(
                                    root.resolve("source"), root.getParent().resolve("outside")));
            assertEquals(1, api.moves);
        }
        assertTrue(api.live.isEmpty());
    }
}
