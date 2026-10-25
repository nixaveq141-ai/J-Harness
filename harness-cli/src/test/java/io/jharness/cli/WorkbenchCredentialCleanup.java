package io.jharness.cli;

import io.jharness.api.Values;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Cleanup only synthetic credentials referenced by a bounded browser fixture workspace. */
public final class WorkbenchCredentialCleanup {
    private WorkbenchCredentialCleanup() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected fixture workspace");
        Path requested = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = requested.toRealPath();
        if (!root.equals(requested)) throw new IllegalArgumentException("Linked fixture rejected");
        Path allowed = Path.of(".build-cache").toRealPath();
        if (!root.startsWith(allowed) || !root.getFileName().toString().startsWith("M10-settings-"))
            throw new IllegalArgumentException("Not a fixture");
        Path file = root.resolve(".jharness/settings/workbench.json");
        for (Path component : List.of(root.resolve(".jharness"), file.getParent()))
            if (!Files.isDirectory(component, LinkOption.NOFOLLOW_LINKS)
                    || !component.toRealPath().equals(component))
                throw new IllegalArgumentException("Linked metadata rejected");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Fixture settings missing");
        var config = Json.read(file, 32768);
        String namespace =
                "J-Harness/" + Json.hash(root.toString().getBytes(StandardCharsets.UTF_8));
        var targets =
                targets(
                        config,
                        Files.exists(
                                        file.resolveSibling("credential-cleanup.json"),
                                        LinkOption.NOFOLLOW_LINKS)
                                ? Json.read(file.resolveSibling("credential-cleanup.json"), 32768)
                                : Map.of("refs", List.of()),
                        namespace);
        var vault = new SystemCredentials();
        for (int offset = 0; offset < targets.size(); offset += 32)
            vault.update(Map.of(), targets.subList(offset, Math.min(offset + 32, targets.size())));
        for (String target : targets)
            if (!vault.read(target).isEmpty())
                throw new IllegalStateException("Fixture credential remains");
        System.out.println("FIXTURE_CREDENTIALS_CLEAN");
    }

    static List<String> targets(
            Map<String, Object> config, Map<String, Object> cleanup, String namespace) {
        var targets = new LinkedHashSet<String>();
        if (!(config.get("models") instanceof List<?> models)
                || models.isEmpty()
                || models.size() > 128)
            throw new IllegalArgumentException("Invalid fixture settings");
        for (Object raw : models) {
            Object ref = Values.object(raw).get("credentialRef");
            if (!(ref instanceof String s) || !s.matches("(?:[a-f0-9]{64})?"))
                throw new IllegalArgumentException("Invalid fixture reference");
            if (!s.isEmpty()) targets.add(namespace + "/" + s);
        }
        if (!(cleanup.get("refs") instanceof List<?> refs) || refs.size() > 128)
            throw new IllegalArgumentException("Invalid fixture cleanup journal");
        for (Object ref : refs) {
            if (!(ref instanceof String value) || !value.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid fixture cleanup reference");
            targets.add(namespace + "/" + value);
        }
        return List.copyOf(targets);
    }
}
