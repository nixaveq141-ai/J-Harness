package io.jharness.cli;

import io.jharness.api.*;
import io.jharness.web.HarnessWebServer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Operator-owned roots. Registering a directory never loads its model or extension config. */
final class WorkbenchWorkspaces implements HarnessWebServer.Workspaces {
    record RootCheck(Path root, String owner) {
        RootCheck(Path root) throws IOException {
            this(root, Files.getOwner(root).getName());
        }

        void verify() throws IOException {
            Path current = root.getRoot();
            for (Path part : root) {
                current = current.resolve(part);
                var attrs =
                        Files.readAttributes(
                                current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isDirectory()
                        || attrs.isSymbolicLink()
                        || attrs.isOther()
                        || !current.toRealPath().equals(current.toAbsolutePath().normalize()))
                    throw error("WORKSPACE_PATH_DENIED");
            }
            if (!Files.getOwner(root).getName().equals(owner))
                throw error("WORKSPACE_OWNER_MISMATCH");
        }
    }

    @FunctionalInterface
    interface Factory {
        HarnessWebServer.Profile create(Path path) throws IOException;
    }

    private final Metadata metadata;
    private final DataSanitizer sanitizer;
    private final Factory factory;
    private final Path file;
    private final String owner;
    private final List<Map<String, Object>> entries = new ArrayList<>();
    private final Map<String, HarnessWebServer.Profile> profiles = new LinkedHashMap<>();

    WorkbenchWorkspaces(Metadata metadata, DataSanitizer sanitizer, Factory factory)
            throws IOException {
        this.metadata = metadata;
        this.sanitizer = sanitizer;
        this.factory = factory;
        owner = Files.getOwner(metadata.workspace()).getName();
        file = metadata.directory("settings").resolve("workspaces.json");
        entries.add(
                Map.of(
                        "id",
                        "default",
                        "name",
                        metadata.workspace().getFileName().toString(),
                        "path",
                        metadata.workspace().toString()));
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            checkFile();
            var state = Json.read(file, 32768);
            if (!state.keySet().equals(Set.of("version", "workspaces"))
                    || !Integer.valueOf(1).equals(state.get("version"))
                    || !(state.get("workspaces") instanceof List<?> list)
                    || list.size() > 15) throw error("INVALID_WORKSPACE_REGISTRY");
            for (Object raw : list) entries.add(Values.object(raw));
        }
        var roots = new HashSet<Path>();
        for (var entry : entries) {
            if (!entry.keySet().equals(Set.of("id", "name", "path"))
                    || !(entry.get("id") instanceof String id)
                    || !id.matches("[A-Za-z0-9_-]{1,32}")
                    || profiles.containsKey(id)) throw error("INVALID_WORKSPACE_REGISTRY");
            name(entry.get("name"));
            Path root = validate((String) entry.get("path"));
            if (!roots.add(root)) throw error("WORKSPACE_EXISTS");
            profiles.put(id, factory.create(root));
        }
    }

    Map<String, HarnessWebServer.Profile> profiles() {
        return Map.copyOf(profiles);
    }

    @Override
    public synchronized Map<String, Object> read() {
        return Map.of("workspaces", List.copyOf(entries));
    }

    @Override
    public synchronized Map.Entry<String, HarnessWebServer.Profile> register(
            String path, String label) throws IOException {
        if (entries.size() >= 16) throw error("WORKSPACE_LIMIT");
        name(label);
        Path root = validate(path);
        if (entries.stream().anyMatch(e -> Path.of((String) e.get("path")).equals(root)))
            throw error("WORKSPACE_EXISTS");
        var profile = factory.create(root);
        try (var probe =
                profile.sessions().openWriter("web-workspace-owner", WriteOwnership.EXCLUSIVE)) {
            probe.flush();
        }
        String id = "ws-" + UUID.randomUUID().toString().substring(0, 12);
        var entry = Map.<String, Object>of("id", id, "name", label, "path", root.toString());
        var next = new ArrayList<>(entries.subList(1, entries.size()));
        next.add(entry);
        var state = Map.<String, Object>of("version", 1, "workspaces", next);
        if (!sanitizer.sanitize(state).equals(state)) throw error("SECRET_CONFIG");
        byte[] bytes = Json.text(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32768) throw error("WORKSPACE_LIMIT");
        metadata.directory("settings");
        checkFile();
        Path temp = Files.createTempFile(file.getParent(), "workspaces-", ".tmp");
        try {
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            Files.move(
                    temp,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        entries.add(entry);
        profiles.put(id, profile);
        return Map.entry(id, profile);
    }

    Path validate(String input) throws IOException {
        if (input == null
                || input.length() > 1024
                || input.startsWith("\\\\")
                || input.startsWith("//")) throw error("WORKSPACE_PATH_DENIED");
        Path path = Path.of(input);
        if (!path.isAbsolute() || !path.normalize().equals(path) || path.getParent() == null)
            throw error("WORKSPACE_PATH_DENIED");
        Path current = path.getRoot();
        for (Path part : path) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (Set.of(".git", ".jharness", ".ssh", ".aws", ".codex", ".agents", ".dsh-tmp")
                            .contains(name)
                    || name.startsWith(".env")) throw error("WORKSPACE_PATH_DENIED");
            current = current.resolve(part);
            var attrs =
                    Files.readAttributes(
                            current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory()
                    || attrs.isSymbolicLink()
                    || attrs.isOther()
                    || !current.toRealPath().equals(current.toAbsolutePath().normalize()))
                throw error("WORKSPACE_PATH_DENIED");
        }
        if (!Files.getOwner(path).getName().equals(owner)) throw error("WORKSPACE_OWNER_MISMATCH");
        Path real = path.toRealPath();
        new Metadata(real).directory("sessions");
        return real;
    }

    private static void name(Object value) {
        if (!(value instanceof String text)
                || text.isBlank()
                || text.length() > 80
                || text.chars().anyMatch(Character::isISOControl))
            throw error("INVALID_WORKSPACE_NAME");
    }

    private void checkFile() throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(file))) throw error("WORKSPACE_PATH_DENIED");
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }
}
