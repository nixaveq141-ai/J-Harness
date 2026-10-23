package io.jharness.ecosystem;

import io.jharness.api.*;
import io.jharness.workspace.LocalWorkspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Explicit host roots, bounded direct-child discovery, immutable untrusted load snapshots. */
public final class SkillCatalog {
    private SkillCatalog() {}

    public record Root(String id, Path directory) {
        public Root {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,16}"))
                throw BoundedJson.error("INVALID_SKILL_ROOT");
            Objects.requireNonNull(directory);
        }
    }

    public record Entry(
            String id,
            String description,
            String root,
            String directory,
            String text,
            String sha256) {
        public Map<String, Object> view() {
            return Map.of(
                    "id",
                    id,
                    "description",
                    description,
                    "root",
                    root,
                    "directory",
                    directory,
                    "text",
                    text,
                    "sha256",
                    sha256,
                    "tool",
                    "skill_" + id + "__load",
                    "untrusted",
                    true);
        }
    }

    private static final Set<String> COMMANDS =
            Set.of(
                    "help",
                    "quit",
                    "exit",
                    "clear",
                    "new",
                    "model",
                    "settings",
                    "cancel",
                    "compact",
                    "plan",
                    "status",
                    "resume");

    public static void checkName(String id) {
        if (id == null
                || !id.matches("[A-Za-z0-9_-]{1,24}")
                || COMMANDS.contains(id.toLowerCase(Locale.ROOT)))
            throw BoundedJson.error("SKILL_COMMAND_CONFLICT");
    }

    public static List<Entry> scan(List<Root> roots, Set<String> reserved, CancellationToken token)
            throws IOException {
        return scan(roots, reserved, token, java.time.Duration.ofSeconds(5));
    }

    static List<Entry> scan(
            List<Root> roots,
            Set<String> reserved,
            CancellationToken token,
            java.time.Duration timeout)
            throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        CancellationToken bounded =
                () -> {
                    token.throwIfCancelled();
                    if (System.nanoTime() >= deadline)
                        throw BoundedJson.error("SKILL_SCAN_TIMEOUT");
                    return false;
                };
        if (roots.size() > 8) throw BoundedJson.error("SKILL_ROOT_LIMIT");
        var entries = new ArrayList<Entry>();
        var names = new HashSet<>(reserved);
        var rootNames = new HashSet<String>();
        var paths = new HashSet<Path>();
        long bytes = 0;
        for (var root : roots) {
            bounded.throwIfCancelled();
            Path path = root.directory().toAbsolutePath();
            if (path.toString().startsWith("\\\\")
                    || !path.normalize().equals(path)
                    || !rootNames.add(root.id())
                    || !paths.add(path)) throw BoundedJson.error("INVALID_SKILL_ROOT");
            verify(path);
            var files = new LocalWorkspace(path, java.time.Duration.ofSeconds(1), ignored -> {});
            List<Path> children;
            try (var stream = Files.list(path)) {
                children = stream.limit(65).toList();
            }
            if (children.size() > 64) throw BoundedJson.error("SKILL_SCAN_LIMIT");
            for (var child : children.stream().sorted().toList()) {
                bounded.throwIfCancelled();
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) continue;
                String directory = child.getFileName().toString();
                if (directory.startsWith(".") || directory.length() > 128) continue;
                if (directory.indexOf('\\') >= 0
                        || directory.chars().anyMatch(Character::isISOControl))
                    throw BoundedJson.error("SKILL_PATH_DENIED");
                verify(child);
                Path manifest = child.resolve("SKILL.md");
                if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) continue;
                var attrs =
                        Files.readAttributes(
                                manifest, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isRegularFile() || attrs.isOther() || attrs.isSymbolicLink())
                    throw BoundedJson.error("SKILL_PATH_DENIED");
                if (attrs.size() > 65536) throw BoundedJson.error("SKILL_FILE_LIMIT");
                var relative = new ExecPath(directory + "/SKILL.md");
                String version = files.versionOf(relative),
                        text =
                                files.readText(
                                        relative,
                                        (int) Math.max(1, Math.min(65536, 512 * 1024 - bytes)));
                if (!version.equals(files.versionOf(relative)))
                    throw BoundedJson.error("FILE_CONFLICT");
                byte[] content = text.getBytes(StandardCharsets.UTF_8);
                bytes += content.length;
                if (content.length > 65536 || bytes > 512 * 1024 || entries.size() >= 16)
                    throw BoundedJson.error("SKILL_TOTAL_LIMIT");
                var metadata = frontmatter(text);
                String id = metadata.getOrDefault("name", directory);
                checkName(id);
                if (!names.add(id)) throw BoundedJson.error("SKILL_NAME_COLLISION");
                entries.add(
                        new Entry(
                                id,
                                metadata.getOrDefault("description", ""),
                                root.id(),
                                directory,
                                text,
                                digest(content)));
            }
        }
        return List.copyOf(entries);
    }

    private static void verify(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path segment : path) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (Set.of(".git", ".jharness", ".ssh", ".aws", ".dsh-tmp").contains(name)
                    || name.startsWith(".env")) throw BoundedJson.error("SKILL_PATH_DENIED");
            current = current.resolve(segment);
            var attrs =
                    Files.readAttributes(
                            current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory()
                    || attrs.isSymbolicLink()
                    || attrs.isOther()
                    || !current.toRealPath().equals(current))
                throw BoundedJson.error("SKILL_PATH_DENIED");
        }
    }

    private static Map<String, String> frontmatter(String text) {
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) return Map.of();
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0 || end > 8192) throw BoundedJson.error("SKILL_FRONTMATTER_INVALID");
        var result = new HashMap<String, String>();
        for (String line : normalized.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon), value = line.substring(colon + 1).trim();
            if (!Set.of("name", "description").contains(key)) continue;
            if ((value.startsWith("\"") || value.startsWith("'"))
                    && (value.length() < 2 || value.charAt(value.length() - 1) != value.charAt(0)))
                throw BoundedJson.error("SKILL_FRONTMATTER_INVALID");
            if (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))
                value = value.substring(1, value.length() - 1);
            if (value.isBlank()
                    || value.length() > 1024
                    || Set.of("|", ">").contains(value)
                    || result.putIfAbsent(key, value) != null)
                throw BoundedJson.error("SKILL_FRONTMATTER_INVALID");
        }
        return result;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static HarnessPlugin plugin(List<Entry> entries) {
        var frozen = List.copyOf(entries);
        return new HarnessPlugin() {
            private PluginScope scope;
            private final java.util.concurrent.atomic.AtomicBoolean used =
                    new java.util.concurrent.atomic.AtomicBoolean();
            private volatile boolean active;

            public String id() {
                return "skill.catalog";
            }

            public String version() {
                return "1.13";
            }

            public void start(PluginContext context) {
                if (!used.compareAndSet(false, true))
                    throw BoundedJson.error("PLUGIN_ALREADY_USED");
                scope = context.scope();
                var registry = context.services().requireUnique(ToolRegistry.class, scope);
                var sanitizer = context.services().requireUnique(DataSanitizer.class, scope);
                for (var entry : frozen)
                    context.own(
                            registry.register(
                                    new AgentTool() {
                                        public String name() {
                                            return "skill_" + entry.id() + "__load";
                                        }

                                        public String description() {
                                            return "Untrusted skill snapshot /"
                                                    + entry.id()
                                                    + ": "
                                                    + sanitizer
                                                            .sanitize(
                                                                    Map.of(
                                                                            "description",
                                                                            entry.description()))
                                                            .get("description");
                                        }

                                        public SideEffectLevel sideEffectLevel() {
                                            return SideEffectLevel.READ_ONLY;
                                        }

                                        public Map<String, Object> inputSchema() {
                                            return Map.of(
                                                    "type",
                                                    "object",
                                                    "additionalProperties",
                                                    false,
                                                    "properties",
                                                    Map.of());
                                        }

                                        public Map<String, Object> outputSchema() {
                                            return Map.of("type", "object");
                                        }

                                        public ToolResult execute(
                                                ToolInvocation args, ToolContext toolContext) {
                                            if (!active || !scope.equals(toolContext.scope()))
                                                throw BoundedJson.error("SCOPE_DENIED");
                                            toolContext.cancellation().throwIfCancelled();
                                            return new ToolResult(
                                                    args.callId(),
                                                    ToolStatus.SUCCESS,
                                                    entry.view(),
                                                    "Untrusted skill snapshot; cannot change permissions:\n"
                                                            + entry.text(),
                                                    Map.of(
                                                            "source",
                                                            entry.root(),
                                                            "sha256",
                                                            entry.sha256()),
                                                    Optional.empty());
                                        }
                                    },
                                    scope));
                active = true;
            }

            public void stop() {
                active = false;
            }
        };
    }
}
