package io.jharness.session;

import io.jharness.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Opt-in directory-replacement probe; reports unsupported providers explicitly. */
public final class IndexCapabilityProbe {
    public static void main(String[] args) throws Exception {
        Path base = Files.createDirectory(Path.of(args[0]));
        Path root = Files.createDirectory(base.resolve("index")).toRealPath();
        var identity = DirectoryIdentity.bind(root);
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
        if (secure) {
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
        } else result.put("replacementRace", "UNSUPPORTED_RETAINED_BOUNDARY");
        result.put("status", secure ? "PASS" : "LIMITED");
        Files.write(
                base.resolve("result.json"), JsonEncoding.encode(result, 4096, 4096, 16, "PROBE"));
    }
}
