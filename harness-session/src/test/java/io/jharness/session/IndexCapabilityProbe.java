package io.jharness.session;

import io.jharness.api.*;
import io.jharness.workspace.WindowsDirectoryPin;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Opt-in directory-replacement probe; reports unsupported providers explicitly. */
public final class IndexCapabilityProbe {
    private static void windowsRace(Path base, String phase) throws Exception {
        Path root = Files.createDirectory(base.resolve("windows-" + phase)).toRealPath();
        Path moved = base.resolve("windows-original-" + phase), target = root.resolve("entry");
        var identity = IndexFiles.bind(root);
        if (!phase.equals("lock")) Files.writeString(target, "original");
        boolean[] blocked = {false};
        IndexFiles.PublicationHook replace =
                () -> {
                    Path temp = null;
                    if (phase.equals("publish"))
                        try (var names = Files.newDirectoryStream(root, "index-*.tmp")) {
                            temp = names.iterator().next().getFileName();
                        }
                    try {
                        Files.move(root, moved);
                    } catch (FileSystemException expected) {
                        if (!Files.isDirectory(root) || Files.exists(moved)) throw expected;
                        blocked[0] = true;
                        return;
                    }
                    Files.createDirectory(root);
                    if (temp != null) Files.writeString(root.resolve(temp), "trap");
                    if (!phase.equals("lock")) Files.writeString(target, "outside");
                };
        if (phase.equals("publish")) {
            try {
                IndexFiles.publish(
                        root, identity, target, Map.of("marker", "third"), 4096, replace);
            } catch (IOException failure) {
                if (blocked[0]) throw failure;
            }
            if (!blocked[0] || !Files.readString(target).contains("\"marker\":\"third\""))
                throw new AssertionError("redirected index publication");
        } else
            try (var channel =
                    IndexFiles.open(
                            root,
                            identity,
                            target,
                            phase.equals("read")
                                    ? Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
                                    : Set.of(
                                            StandardOpenOption.CREATE,
                                            StandardOpenOption.WRITE,
                                            LinkOption.NOFOLLOW_LINKS),
                            replace)) {
                if (phase.equals("read")) {
                    var buffer = java.nio.ByteBuffer.allocate(32);
                    int count = channel.read(buffer);
                    if (!blocked[0]
                            || !new String(
                                            buffer.array(),
                                            0,
                                            count,
                                            java.nio.charset.StandardCharsets.UTF_8)
                                    .equals("original"))
                        throw new AssertionError("redirected index read");
                } else
                    try (var ownership = channel.tryLock()) {
                        if (ownership == null || !blocked[0])
                            throw new AssertionError("redirected index lock");
                    }
            }
        try (var names = Files.newDirectoryStream(root, ".jharness-pin-*.tmp")) {
            if (names.iterator().hasNext()) throw new AssertionError("Native guard not cleaned");
        }
    }

    private static void openRace(Path base, boolean lock) throws Exception {
        Path root =
                Files.createDirectory(base.resolve(lock ? "lock-root" : "read-root")).toRealPath();
        Path moved = base.resolve(lock ? "lock-original" : "read-original"),
                target = root.resolve("entry");
        var identity = IndexFiles.bind(root);
        if (!lock) Files.writeString(target, "original");
        try (var channel =
                IndexFiles.open(
                        root,
                        identity,
                        target,
                        lock
                                ? Set.of(
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS)
                                : Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                        () -> {
                            Files.move(root, moved);
                            Files.createDirectory(root);
                            if (!lock) Files.writeString(target, "outside");
                        })) {
            if (lock) {
                try (var owned = channel.tryLock()) {
                    if (owned == null) throw new AssertionError("Expected exclusive index lock");
                    if (Files.exists(target)) throw new AssertionError("redirected index lock");
                    if (!Files.isRegularFile(moved.resolve("entry")))
                        throw new AssertionError("Missing original lock");
                }
            } else {
                var bytes = java.nio.ByteBuffer.allocate(32);
                int count = channel.read(bytes);
                String actual =
                        new String(
                                bytes.array(), 0, count, java.nio.charset.StandardCharsets.UTF_8);
                if (!actual.equals("original")) throw new AssertionError("redirected index read");
                if (!Files.readString(target).equals("outside"))
                    throw new AssertionError("Outside read sentinel changed");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String phase = args.length > 1 ? args[1] : "all";
        Path base = Files.createDirectory(Path.of(args[0]));
        Path root = Files.createDirectory(base.resolve("index")).toRealPath();
        var identity = IndexFiles.bind(root);
        Path target = root.resolve("s.search.json");
        IndexFiles.publish(root, identity, target, Map.of("marker", "first"), 4096);
        IndexFiles.publish(root, identity, target, Map.of("marker", "second"), 4096);
        if (!Files.readString(target).contains("\"marker\":\"second\""))
            throw new AssertionError("Normal replacement failed");
        boolean secure;
        try (var directory = Files.newDirectoryStream(root)) {
            secure = directory instanceof SecureDirectoryStream<?>;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("normalPublication", true);
        result.put("secureProvider", secure);
        boolean windows = WindowsDirectoryPin.supported();
        result.put("windowsPinned", windows);
        if (windows)
            for (String operation : List.of("publish", "read", "lock")) {
                if (phase.equals("all") || phase.equals(operation)) {
                    windowsRace(base, operation);
                    result.put(operation + "Race", "PASS_REPLACEMENT_BLOCKED");
                }
            }
        if ((secure || !windows) && Set.of("all", "publish").contains(phase)) {
            Path moved = base.resolve("original");
            boolean[] entered = {false};
            try {
                IndexFiles.publish(
                        root,
                        identity,
                        target,
                        Map.of("marker", "third"),
                        4096,
                        () -> {
                            entered[0] = true;
                            Path temporary;
                            try (var files = Files.newDirectoryStream(root, "index-*.tmp")) {
                                temporary = files.iterator().next().getFileName();
                            }
                            Files.move(root, moved);
                            Files.createDirectory(root);
                            Files.writeString(root.resolve(temporary), "attacker temporary");
                            Files.writeString(target, "outside sentinel");
                        });
                throw new AssertionError("Changed root accepted");
            } catch (IOException expected) {
                if (!entered[0]) throw new AssertionError("Race seam not reached", expected);
            }
            if (!Files.readString(target).equals("outside sentinel"))
                throw new AssertionError("redirected index publication");
            if (!Files.readString(moved.resolve(target.getFileName()))
                    .contains("\"marker\":\"third\""))
                throw new AssertionError("Original capability not used");
            try (var files = Files.newDirectoryStream(moved, "index-*.tmp")) {
                if (files.iterator().hasNext()) throw new AssertionError("Temporary leaked");
            }
            result.put("replacementRace", "PASS_ORIGINAL_DIRECTORY_ONLY");
        } else if (windows) result.put("replacementRace", "PASS_REPLACEMENT_BLOCKED");
        else if (!secure && windows) result.put("replacementRace", "UNSUPPORTED_RETAINED_BOUNDARY");
        if ((secure || !windows) && Set.of("all", "read").contains(phase)) {
            openRace(base, false);
            result.put("readRace", "PASS_ORIGINAL_DIRECTORY_ONLY");
        }
        if ((secure || !windows) && Set.of("all", "lock").contains(phase)) {
            openRace(base, true);
            result.put("lockRace", "PASS_ORIGINAL_DIRECTORY_ONLY");
        }
        result.put("posixDescriptor", !windows && !secure);
        result.put("status", "PASS");
        Files.write(
                base.resolve("result.json"), JsonEncoding.encode(result, 4096, 4096, 16, "PROBE"));
    }
}
