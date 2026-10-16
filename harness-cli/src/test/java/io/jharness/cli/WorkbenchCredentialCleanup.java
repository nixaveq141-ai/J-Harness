package io.jharness.cli;

import io.jharness.api.Values;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cleanup only synthetic credentials referenced by a bounded browser fixture workspace. */
public final class WorkbenchCredentialCleanup {
    private WorkbenchCredentialCleanup() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        Path allowed = Path.of(".build-cache").toRealPath();
        if (!root.startsWith(allowed) || !root.getFileName().toString().startsWith("M10-settings-"))
            throw new IllegalArgumentException("Not a fixture");
        Path file = root.resolve(".jharness/settings/workbench.json");
        if (!Files.exists(file)) return;
        var config = Json.read(file, 32768);
        String namespace =
                "J-Harness/" + Json.hash(root.toString().getBytes(StandardCharsets.UTF_8));
        var targets = new ArrayList<String>();
        for (Object raw : (List<?>) config.get("models")) {
            Object ref = Values.object(raw).get("credentialRef");
            if (ref instanceof String s && s.matches("[a-f0-9]{64}"))
                targets.add(namespace + "/" + s);
        }
        var vault = new SystemCredentials();
        vault.update(Map.of(), targets);
        for (String target : targets)
            if (!vault.read(target).isEmpty())
                throw new IllegalStateException("Fixture credential remains");
        System.out.println("FIXTURE_CREDENTIALS_CLEAN");
    }
}
