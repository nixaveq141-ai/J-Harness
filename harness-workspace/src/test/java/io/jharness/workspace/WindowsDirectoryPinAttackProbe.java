package io.jharness.workspace;

import com.sun.jna.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Opt-in real kernel proof: a writable pin must also reject an in-place mount point. */
public final class WindowsDirectoryPinAttackProbe {
    interface AttackApi extends WindowsDirectoryPin.Kernel32 {
        int DeviceIoControl(
                Pointer handle,
                int code,
                Pointer input,
                int length,
                Pointer output,
                int capacity,
                Pointer returned,
                Pointer overlapped);
    }

    private static void attack(Path root, Path outside, boolean guarded) throws Exception {
        var api = Native.load("kernel32", AttackApi.class);
        Pointer handle =
                api.CreateFileW(
                        new WString("\\\\?\\" + root), 0x40000000, 7, null, 3, 0x02200000, null);
        if (handle == null || Pointer.nativeValue(handle) == -1L)
            throw new IOException(
                    "Write-sharing attack handle unavailable: " + Native.getLastError());
        boolean installed = false;
        try (var returned = new Memory(4)) {
            byte[] substitute = ("\\??\\" + outside).getBytes(StandardCharsets.UTF_16LE);
            byte[] display = outside.toString().getBytes(StandardCharsets.UTF_16LE);
            int paths = substitute.length + display.length + 4;
            try (var data = new Memory(16 + paths)) {
                data.clear();
                data.setInt(0, 0xA0000003);
                data.setShort(4, (short) (8 + paths));
                data.setShort(10, (short) substitute.length);
                data.setShort(12, (short) (substitute.length + 2));
                data.setShort(14, (short) display.length);
                data.write(16, substitute, 0, substitute.length);
                data.write(18 + substitute.length, display, 0, display.length);
                installed =
                        api.DeviceIoControl(
                                        handle,
                                        0x900A4,
                                        data,
                                        (int) data.size(),
                                        null,
                                        0,
                                        returned,
                                        null)
                                != 0;
                int error = Native.getLastError();
                if (guarded ? installed || error != 145 : !installed)
                    throw new AssertionError(
                            "Reparse control mismatch: guarded="
                                    + guarded
                                    + "; installed="
                                    + installed
                                    + "; error="
                                    + error);
            }
        } finally {
            try {
                if (installed)
                    try (var data = new Memory(8);
                            var returned = new Memory(4)) {
                        data.clear();
                        data.setInt(0, 0xA0000003);
                        if (api.DeviceIoControl(handle, 0x900AC, data, 8, null, 0, returned, null)
                                == 0) throw new IOException("Cannot restore owned reparse control");
                    }
            } finally {
                if (api.CloseHandle(handle) == 0)
                    throw new IOException("Attack handle cleanup failed");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (!WindowsDirectoryPin.supported()) throw new IOException("Windows required");
        Path base = Files.createDirectory(Path.of(args[0])).toRealPath();
        Path outside = Files.createDirectory(base.resolve("outside"));
        Files.writeString(outside.resolve("canary"), "unchanged");
        Path guarded = Files.createDirectory(base.resolve("guarded"));
        try (var pin = WindowsDirectoryPin.open(guarded, null)) {
            pin.allowChildRenames();
            int guards = 0;
            try (var entries = Files.newDirectoryStream(guarded)) {
                for (Path entry : entries) {
                    try {
                        Files.delete(entry);
                        throw new AssertionError("Guard deleted while held");
                    } catch (FileSystemException expected) {
                        guards++;
                    }
                }
            }
            if (guards != 1) throw new AssertionError("Expected exactly one undeletable guard");
            attack(guarded, outside, true);
        }
        try (var entries = Files.newDirectoryStream(guarded)) {
            if (entries.iterator().hasNext()) throw new AssertionError("Guard cleanup leaked");
        }
        attack(Files.createDirectory(base.resolve("control")), outside, false);
        if (!Files.readString(outside.resolve("canary")).equals("unchanged"))
            throw new AssertionError("Outside content changed");
        System.out.println(
                "PASS: guarded reparse denied145; unguarded reparse succeeds and is restored; cleanup verified");
    }
}
