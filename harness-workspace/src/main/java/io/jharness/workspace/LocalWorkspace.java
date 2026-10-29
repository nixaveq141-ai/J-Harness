package io.jharness.workspace;

import io.jharness.api.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Trusted-host filesystem provider. It rejects link/reparse traversal, not an OS sandbox. */
public final class LocalWorkspace implements WorkspaceFiles {
    @Override
    public WorkspaceFiles isolatedView() throws IOException {
        return new LocalWorkspace(this);
    }

    private LocalWorkspace(LocalWorkspace source) throws IOException {
        root = source.root;
        rootKey = source.rootKey;
        timeout = source.timeout;
        hook = source.hook;
        searchFilter = source.searchFilter;
        revoked = source.revoked;
        ensureOpen();
        checkSegments(root);
    }

    public static final String MISSING = "MISSING";
    private static final int WRITE_LIMIT = 1024 * 1024;
    private static final int PAGE_LIMIT = 256 * 1024;
    private static final int SCAN_LIMIT = 32 * 1024 * 1024;
    private static final Set<String> PROTECTED = Set.of(".git", ".jharness");
    private static final Set<String> DEFAULT_EXCLUDED = Set.of("target", "node_modules");
    private final Path root;
    private final Object rootKey;
    private final java.util.concurrent.atomic.AtomicBoolean revoked;

    /** Revokes all borrowed views; an operation reaching its next check then stops. */
    public void revoke() {
        revoked.set(true);
    }

    private void ensureOpen() {
        if (revoked.get()) throw new HarnessException("ENVIRONMENT_CLOSED", "Environment closed");
    }

    private final Duration timeout;
    private final CommitHook hook;
    private final WorkspaceSearchFilter searchFilter;
    private final Map<String, String> observed = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<DirectoryCapability>> traversal =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** Fault/race injection seam, outside model-visible interfaces. */
    @FunctionalInterface
    public interface CommitHook {
        void beforeCommit(Path path) throws IOException;
    }

    public LocalWorkspace(Path root) throws IOException {
        this(root, Duration.ofSeconds(5), path -> {});
    }

    public LocalWorkspace(Path root, Duration timeout, CommitHook hook) throws IOException {
        this(root, timeout, hook, WorkspaceSearchFilter.defaults());
    }

    public LocalWorkspace(Path root, WorkspaceSearchFilter searchFilter) throws IOException {
        this(root, Duration.ofSeconds(5), path -> {}, searchFilter);
    }

    public LocalWorkspace(
            Path root, Duration timeout, CommitHook hook, WorkspaceSearchFilter searchFilter)
            throws IOException {
        this.revoked = new java.util.concurrent.atomic.AtomicBoolean();
        this.root = root.toRealPath();
        if (!Files.isDirectory(this.root)) throw new IOException("Workspace must be a directory");
        rootKey = DirectoryCapability.identity(this.root);
        this.timeout = Objects.requireNonNull(timeout);
        this.hook = Objects.requireNonNull(hook);
        this.searchFilter = Objects.requireNonNull(searchFilter);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout");
    }

    @Override
    public ExecPath root() {
        ensureOpen();
        return new ExecPath(".");
    }

    private CancellationToken bounded(CancellationToken token) {
        ensureOpen();
        long start = System.nanoTime(), budget = timeout.toNanos();
        return () -> {
            ensureOpen();
            if (System.nanoTime() - start >= budget)
                throw new HarnessException(
                        "TIMEOUT", "File operation timed out; inspect version before retry");
            return token.isCancelled();
        };
    }

    @Override
    public synchronized Map<String, Object> applyPatch(
            String patchText, Map<String, String> expectedVersions, CancellationToken cancellation)
            throws IOException {
        boolean[] mutationAttempted = {false};
        try {
            return applyPatchTracked(patchText, expectedVersions, cancellation, mutationAttempted);
        } catch (HarnessException failure) {
            if (!mutationAttempted[0]) throw HarnessException.rejectedBeforeMutation(failure);
            throw failure;
        }
    }

    private Map<String, Object> applyPatchTracked(
            String patchText,
            Map<String, String> expectedVersions,
            CancellationToken cancellation,
            boolean[] mutationAttempted)
            throws IOException {
        var patches = UnifiedPatch.parse(patchText);
        Map<String, String> versions = new LinkedHashMap<>(), contents = new LinkedHashMap<>();
        long totalCharacters = 0;
        Set<Path> targets = new HashSet<>();
        for (var patch : patches) {
            cancellation.throwIfCancelled();
            ExecPath path = new ExecPath(patch.path());
            Path target = resolve(path);
            if (!targets.add(target))
                throw new HarnessException("INVALID_PATCH", "Duplicate canonical patch target");
            String version = versionOf(path),
                    expected =
                            expectedVersions.getOrDefault(
                                    patch.path(), observed.get(relative(target)));
            if (patch.create() && version.equals(MISSING) && expected == null) expected = MISSING;
            if (expected == null
                    || !expected.equals(version)
                    || patch.create() != version.equals(MISSING))
                throw new HarnessException(
                        "FILE_CONFLICT", "Patch requires observed or explicit current version");
            String before = version.equals(MISSING) ? "" : text(target, bounded(cancellation));
            String after = UnifiedPatch.apply(patch, before);
            totalCharacters += before.length() + after.length();
            if (totalCharacters > 4 * 1024 * 1024)
                throw new HarnessException(
                        "PATCH_LIMIT", "Combined patch text snapshot exceeds limit");
            versions.put(patch.path(), version);
            contents.put(patch.path(), after);
        }
        List<Map<String, Object>> changes = new ArrayList<>();
        for (var patch : patches) {
            try {
                mutationAttempted[0] = true;
                var change =
                        write(
                                new ExecPath(patch.path()),
                                contents.get(patch.path()),
                                !patch.create(),
                                versions.get(patch.path()),
                                cancellation);
                changes.add(
                        Map.of(
                                "path",
                                change.path().value(),
                                "beforeVersion",
                                change.beforeVersion(),
                                "afterVersion",
                                change.afterVersion(),
                                "metadata",
                                change.metadata(),
                                "diff",
                                change.diff().substring(0, Math.min(change.diff().length(), 8192)),
                                "diffTruncated",
                                change.diff().length() > 8192));
            } catch (IOException | RuntimeException ex) {
                if (changes.isEmpty()) throw ex;
                String code = ex instanceof HarnessException h ? h.code() : "PATCH_IO";
                return Map.of(
                        "changes",
                        changes,
                        "partial",
                        true,
                        "sideEffectUncertain",
                        true,
                        "error",
                        code,
                        "failedPath",
                        patch.path(),
                        "failedPathState",
                        "REQUIRES_INSPECTION",
                        "pendingPaths",
                        patches.stream()
                                .skip(changes.size() + 1)
                                .map(UnifiedPatch.FilePatch::path)
                                .toList());
            }
        }
        return Map.of("changes", changes, "partial", false);
    }

    private Path resolve(ExecPath path) throws IOException {
        ensureOpen();
        String text = path.value().replace('\\', '/');
        if (text.startsWith("/")
                || text.contains(":")
                || text.codePoints().anyMatch(Character::isISOControl)
                || Arrays.asList(text.split("/", -1)).contains("..")) deny();
        Path relative;
        try {
            relative = Path.of(text);
        } catch (InvalidPathException ex) {
            deny();
            return root;
        }
        if (relative.isAbsolute()) deny();
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".")) continue;
            String trimmed = name.replaceAll("[ .]+$", "");
            if (PROTECTED.contains(trimmed.toLowerCase(Locale.ROOT))
                    || !name.equals(trimmed)
                    || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) deny();
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) deny();
        checkSegments(target);
        return target;
    }

    /** Host-only provider seam; model tools continue to receive ExecPath, never this Path. */
    public Path commandDirectory(ExecPath path) throws IOException {
        Path directory = resolve(path);
        if (!directory(directory))
            throw new HarnessException("INVALID_CWD", "Command directory must exist");
        return directory;
    }

    private void checkSegments(Path target) throws IOException {
        checkSegments(target, true);
    }

    private void checkSegments(Path target, boolean checkRoot) throws IOException {
        if (checkRoot
                && (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        || !root.toRealPath().equals(root))) deny();
        Path at = root;
        for (Path part : root.relativize(target)) {
            at = at.resolve(part);
            if (!Files.exists(at, LinkOption.NOFOLLOW_LINKS)) break;
            BasicFileAttributes attributes =
                    Files.readAttributes(at, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink()
                    || attributes.isOther()
                    || attributes.isDirectory()
                            && !at.toRealPath().equals(at.toAbsolutePath().normalize())) deny();
        }
    }

    private static void deny() {
        throw new HarnessException(
                "PATH_DENIED", "Path is outside the supported workspace boundary or is protected");
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private DirectoryCapability capability(Path directory, boolean write) throws IOException {
        ensureOpen();
        return DirectoryCapability.open(root, rootKey, directory, write);
    }

    private BasicFileAttributes attributes(Path path) throws IOException {
        for (var held : traversal.get())
            if (held.directory().equals(path.getParent()))
                return held.attributes(path.getFileName());
        try (var cap = capability(path.equals(root) ? root : path.getParent(), false)) {
            return path.equals(root) ? cap.self() : cap.attributes(path.getFileName());
        }
    }

    private boolean exists(Path path) throws IOException {
        try {
            attributes(path);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    private boolean regular(Path path) throws IOException {
        try {
            return attributes(path).isRegularFile();
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    private boolean directory(Path path) throws IOException {
        try {
            return attributes(path).isDirectory();
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    private InputStream input(Path path) throws IOException {
        ensureOpen();
        return java.nio.channels.Channels.newInputStream(
                DirectoryCapability.openFile(
                        root,
                        rootKey,
                        path,
                        Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
    }

    private String version(Path path, CancellationToken token) throws IOException {
        if (!exists(path)) return MISSING;
        checkSegments(path);
        if (!regular(path)) throw new HarnessException("NOT_TEXT_FILE", "Expected a regular file");
        long size = attributes(path).size();
        if (size > SCAN_LIMIT)
            throw new HarnessException("FILE_LIMIT", "File exceeds bounded scan size");
        MessageDigest digest = sha();
        try (InputStream input = input(path)) {
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) >= 0) {
                token.throwIfCancelled();
                total += count;
                if (total > SCAN_LIMIT)
                    throw new HarnessException("FILE_LIMIT", "File grew beyond scan bound");
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public synchronized String versionOf(ExecPath path) throws IOException {
        return version(resolve(path), bounded(CancellationToken.NONE));
    }

    private String text(Path path, CancellationToken token) throws IOException {
        return text(path, token, WRITE_LIMIT);
    }

    private String text(Path path, CancellationToken token, int limit) throws IOException {
        checkSegments(path);
        if (!exists(path)) throw new HarnessException("FILE_NOT_FOUND", "File not found");
        if (!regular(path)) throw new HarnessException("NOT_TEXT_FILE", "Expected a regular file");
        byte[] bytes;
        try (InputStream input = input(path)) {
            bytes = input.readNBytes(limit + 1);
        }
        token.throwIfCancelled();
        if (bytes.length > limit)
            throw new HarnessException(
                    "FILE_LIMIT", "Use paginated read; full text/write limit is 1 MiB");
        if (containsZero(bytes))
            throw new HarnessException("BINARY_FILE", "Binary file is not editable text");
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new HarnessException(
                    "UNSUPPORTED_ENCODING", "Only valid UTF-8 text is supported");
        }
    }

    private static boolean containsZero(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return true;
        return false;
    }

    @Override
    public synchronized String readText(ExecPath path) throws IOException {
        return readText(path, WRITE_LIMIT);
    }

    @Override
    public synchronized String readText(ExecPath path, int maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes > WRITE_LIMIT) throw new IllegalArgumentException("maxBytes");
        CancellationToken token = bounded(CancellationToken.NONE);
        Path target = resolve(path);
        String content = text(target, token, maxBytes);
        String before =
                HexFormat.of().formatHex(sha().digest(content.getBytes(StandardCharsets.UTF_8)));
        if (!before.equals(version(target, token)))
            throw new HarnessException("FILE_CONFLICT", "File changed during read");
        observed.put(relative(target), before);
        return content;
    }

    @Override
    public synchronized Map<String, Object> readPage(
            ExecPath path, int startLine, int endLine, CancellationToken cancellation)
            throws IOException {
        if (startLine < 1 || endLine < startLine || endLine - startLine >= 2000)
            throw new HarnessException("INVALID_RANGE", "At most 2000 lines per page");
        CancellationToken token = bounded(cancellation);
        Path target = resolve(path);
        checkSegments(target);
        if (!exists(target)) throw new HarnessException("FILE_NOT_FOUND", "File not found");
        if (!regular(target)) throw new HarnessException("NOT_TEXT_FILE", "Expected regular file");
        long size = attributes(target).size();
        if (size > SCAN_LIMIT) throw new HarnessException("FILE_LIMIT", "Read scan bound exceeded");
        byte[] snapshot = new byte[(int) size];
        try (InputStream input = input(target)) {
            int offset = 0;
            while (offset < snapshot.length) {
                token.throwIfCancelled();
                int count = input.read(snapshot, offset, Math.min(8192, snapshot.length - offset));
                if (count < 0)
                    throw new HarnessException("FILE_CONFLICT", "File changed during snapshot");
                offset += count;
            }
            token.throwIfCancelled();
            if (input.read() != -1)
                throw new HarnessException("FILE_CONFLICT", "File changed during snapshot");
        }
        String before = HexFormat.of().formatHex(sha().digest(snapshot));
        StringBuilder out = new StringBuilder();
        int line = 1, lastIncludedLine = 0;
        boolean truncated = false;
        boolean beginning = true;
        CharsetDecoder decoder =
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(snapshot), decoder)) {
            char[] buffer = new char[4096];
            int count;
            outer:
            while ((count = reader.read(buffer)) >= 0) {
                token.throwIfCancelled();
                for (int index = 0; index < count; index++) {
                    if (line > endLine) {
                        truncated = true;
                        break outer;
                    }
                    char value = buffer[index];
                    if (value == 0)
                        throw new HarnessException("BINARY_FILE", "Binary file is not text");
                    if (beginning && value == '\uFEFF') continue;
                    beginning = false;
                    if (line >= startLine && line <= endLine) {
                        if (out.length() >= PAGE_LIMIT) {
                            truncated = true;
                            break outer;
                        }
                        out.append(value);
                        lastIncludedLine = line;
                    }
                    if (value == '\n') line++;
                }
            }
        } catch (CharacterCodingException ex) {
            throw new HarnessException("UNSUPPORTED_ENCODING", "Only UTF-8 is supported");
        }
        if (!before.equals(version(target, token)))
            throw new HarnessException("FILE_CONFLICT", "File changed during read");
        synchronized (this) {
            observed.put(relative(target), before);
        }
        String[] lines = out.toString().replace("\r\n", "\n").split("\n", -1);
        StringBuilder numbered = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index == lines.length - 1 && lines[index].isEmpty()) break;
            numbered.append(startLine + index).append(": ").append(lines[index]).append('\n');
        }
        return Map.of(
                "path",
                relative(target),
                "text",
                numbered.toString(),
                "version",
                before,
                "startLine",
                startLine,
                "nextLine",
                Math.max(startLine, lastIncludedLine > 0 ? lastIncludedLine + 1 : line),
                "more",
                truncated,
                "eof",
                !truncated,
                "truncated",
                truncated);
    }

    @Override
    public FileChange writeText(ExecPath path, String content, VersionGuard guard)
            throws IOException {
        return write(
                path,
                content,
                true,
                Objects.requireNonNull(guard).expectedVersion(),
                CancellationToken.NONE);
    }

    @Override
    public synchronized FileChange write(
            ExecPath path,
            String content,
            boolean overwrite,
            String expectedVersion,
            CancellationToken cancellation)
            throws IOException {
        boolean[] mutationAttempted = {false};
        try {
            return writeTracked(
                    path, content, overwrite, expectedVersion, cancellation, mutationAttempted);
        } catch (HarnessException failure) {
            if (!mutationAttempted[0]) throw HarnessException.rejectedBeforeMutation(failure);
            throw failure;
        }
    }

    private FileChange writeTracked(
            ExecPath path,
            String content,
            boolean overwrite,
            String expectedVersion,
            CancellationToken cancellation,
            boolean[] mutationAttempted)
            throws IOException {
        CancellationToken token = bounded(cancellation);
        token.throwIfCancelled();
        Path target = resolve(path);
        String beforeVersion = version(target, token);
        if (!beforeVersion.equals(MISSING) && !overwrite)
            throw new HarnessException(
                    "FILE_EXISTS", "Overwrite requires explicit permission and version");
        String expected =
                expectedVersion != null ? expectedVersion : observed.get(relative(target));
        if (beforeVersion.equals(MISSING) && expected == null) expected = MISSING;
        if (expected == null || !expected.equals(beforeVersion))
            throw new HarnessException(
                    "FILE_CONFLICT", "Read/version guard required; file changed");
        String before = beforeVersion.equals(MISSING) ? "" : text(target, token);
        String after = lineStyle(content, before);
        byte[] bytes = after.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > WRITE_LIMIT || after.indexOf('\0') >= 0)
            throw new HarnessException("FILE_LIMIT", "Write must be bounded UTF-8 text");
        Path parent = target.getParent();
        mutationAttempted[0] = true;
        createParents(parent, token);
        checkSegments(target);
        token.throwIfCancelled();
        Path temporary = parent.resolve(".jharness-write-" + UUID.randomUUID() + ".tmp");
        boolean committed = false;
        Map<String, Object> metadata = Map.of();
        Throwable primary = null;
        try (var held = capability(parent, true)) {
            try {
                try (FileChannel channel =
                        held.channel(
                                temporary.getFileName(),
                                Set.of(
                                        StandardOpenOption.CREATE_NEW,
                                        StandardOpenOption.WRITE,
                                        LinkOption.NOFOLLOW_LINKS),
                                0666)) {
                    ByteBuffer data = ByteBuffer.wrap(bytes);
                    while (data.hasRemaining()) {
                        token.throwIfCancelled();
                        channel.write(data);
                    }
                    channel.force(true);
                }
                if (!beforeVersion.equals(MISSING))
                    metadata = held.copyMetadata(target.getFileName(), temporary.getFileName());
                hook.beforeCommit(target);
                checkSegments(target);
                token.throwIfCancelled();
                if (!expected.equals(version(target, token)))
                    throw new HarnessException("FILE_CONFLICT", "File changed before commit");
                if (beforeVersion.equals(MISSING))
                    held.move(temporary.getFileName(), target.getFileName(), false);
                else {
                    try {
                        held.move(temporary.getFileName(), target.getFileName(), true);
                    } catch (AtomicMoveNotSupportedException ex) {
                        throw new HarnessException(
                                "ATOMIC_MOVE_UNSUPPORTED", "Refuse nonatomic replacement");
                    }
                }
                committed = true;
                String afterVersion = version(target, token);
                observed.put(relative(target), afterVersion);
                return new FileChange(
                        new ExecPath(relative(target)),
                        beforeVersion,
                        afterVersion,
                        UnifiedDiff.between(relative(target), before, after),
                        metadata);
            } catch (IOException | RuntimeException | Error ex) {
                primary =
                        committed
                                ? new HarnessException(
                                        "UNKNOWN_SIDE_EFFECT",
                                        "Commit may have completed; reread/version verification required",
                                        ex)
                                : ex;
                if (primary instanceof IOException io) throw io;
                if (primary instanceof Error fatal) throw fatal;
                throw (RuntimeException) primary;
            } finally {
                if (!committed) {
                    try {
                        if (held.exists(temporary.getFileName()))
                            held.delete(
                                    temporary.getFileName(),
                                    held.attributes(temporary.getFileName()).isDirectory());
                    } catch (IOException | RuntimeException cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                }
            }
        }
    }

    private void createParents(Path parent, CancellationToken token) throws IOException {
        Path at = root;
        for (Path part : root.relativize(parent)) {
            token.throwIfCancelled();
            at = at.resolve(part);
            checkSegments(at);
            if (!exists(at))
                try (var held = capability(at.getParent(), true)) {
                    held.createDirectory(at.getFileName(), 0777);
                }
        }
    }

    private static String lineStyle(String content, String before) {
        String value = content;
        if (before.startsWith("\uFEFF") && !value.startsWith("\uFEFF")) value = "\uFEFF" + value;
        if (before.contains("\r\n") && !before.replace("\r\n", "").contains("\n"))
            value = value.replace("\r\n", "\n").replace("\n", "\r\n");
        return value;
    }

    @Override
    public synchronized FileChange edit(
            ExecPath path,
            String oldText,
            String newText,
            int expectedOccurrences,
            String expectedVersion,
            CancellationToken cancellation)
            throws IOException {
        String replacement;
        try {
            if (oldText.isEmpty() || expectedOccurrences < 1)
                throw new HarnessException(
                        "INVALID_EDIT", "Nonempty oldText and positive occurrence count required");
            String original = readTextForEdit(path, cancellation);
            String needle =
                    original.contains("\r\n")
                            ? oldText.replace("\r\n", "\n").replace("\n", "\r\n")
                            : oldText;
            int matches = 0, index = 0;
            while ((index = original.indexOf(needle, index)) >= 0) {
                matches++;
                index += needle.length();
            }
            if (matches != expectedOccurrences)
                throw new HarnessException(
                        "EDIT_MATCH_CONFLICT",
                        "Actual match count differs from expectedOccurrences");
            replacement = original.replace(needle, newText);
        } catch (HarnessException failure) {
            throw HarnessException.rejectedBeforeMutation(failure);
        }
        return write(path, replacement, true, expectedVersion, cancellation);
    }

    private String readTextForEdit(ExecPath path, CancellationToken cancellation)
            throws IOException {
        // Do not overwrite the version remembered by the earlier user-visible read.
        return text(resolve(path), bounded(cancellation));
    }

    @Override
    public Map<String, Object> list(
            ExecPath path, int depth, int limit, CancellationToken cancellation)
            throws IOException {
        if (depth < 1 || depth > 16 || limit < 1 || limit > 5000)
            throw new HarnessException("INVALID_LIMIT", "Directory bounds invalid");
        CancellationToken token = bounded(cancellation);
        Path directory = resolve(path);
        List<Map<String, Object>> entries = new ArrayList<>();
        boolean[] truncated = {false};
        List<String> ignores = ignoreRules(token);
        if (!directory.equals(root) && ignored(directory, ignores))
            return Map.of("entries", entries, "truncated", false);
        walk(
                directory,
                depth,
                token,
                candidate -> {
                    if (ignored(candidate, ignores)) return false;
                    if (entries.size() >= limit) {
                        truncated[0] = true;
                        return false;
                    }
                    entries.add(
                            Map.of(
                                    "path",
                                    relative(candidate),
                                    "type",
                                    directory(candidate) ? "directory" : "file"));
                    return true;
                });
        return Map.of("entries", entries, "truncated", truncated[0]);
    }

    @FunctionalInterface
    private interface Visit {
        boolean accept(Path path) throws IOException;
    }

    private void walk(Path directory, int depth, CancellationToken token, Visit visitor)
            throws IOException {
        walk(directory, depth, token, visitor, () -> false);
    }

    private void walk(
            Path directory,
            int depth,
            CancellationToken token,
            Visit visitor,
            java.util.function.BooleanSupplier stopped)
            throws IOException {
        if (depth <= 0 || stopped.getAsBoolean()) return;
        checkSegments(directory);
        try (var held = capability(directory, false);
                DirectoryStream<Path> stream = held.entries()) {
            traversal.get().push(held);
            try {
                for (Path candidate : stream) {
                    if (stopped.getAsBoolean()) break;
                    token.throwIfCancelled();
                    try {
                        checkSegments(candidate, false);
                    } catch (HarnessException ex) {
                        if (ex.code().equals("PATH_DENIED")) continue;
                        throw ex;
                    }
                    if (visitor.accept(candidate) && directory(candidate))
                        walk(candidate, depth - 1, token, visitor, stopped);
                }
            } finally {
                traversal.get().pop();
                if (traversal.get().isEmpty()) traversal.remove();
            }
        }
        checkSegments(directory);
    }

    private List<String> ignoreRules(CancellationToken token) throws IOException {
        var rules = new ArrayList<String>();
        Path ignore = root.resolve(".gitignore");
        if (searchFilter.useGitignore() && regular(ignore)) {
            checkSegments(ignore);
            String content = text(ignore, token);
            rules.addAll(WorkspaceSearchFilter.checkedGitignoreRules(content.lines().toList()));
        }
        rules.addAll(searchFilter.rules());
        token.throwIfCancelled();
        return rules;
    }

    private boolean ignored(Path path, List<String> rules) throws IOException {
        for (Path part : root.relativize(path))
            if (PROTECTED.contains(part.toString().toLowerCase(Locale.ROOT))) return true;
        String relative = relative(path);
        String[] parts = relative.split("/");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) prefix.append('/');
            prefix.append(parts[i]);
            boolean directory = i < parts.length - 1 || !rules.isEmpty() && directory(path);
            boolean ignored =
                    searchFilter.useDefaultExcludes()
                            && DEFAULT_EXCLUDED.contains(parts[i].toLowerCase(Locale.ROOT));
            for (String raw : rules) {
                boolean negate = raw.startsWith("!");
                String rule = negate ? raw.substring(1) : raw;
                boolean anchored = rule.startsWith("/");
                if (anchored) rule = rule.substring(1);
                boolean directoryRule = rule.endsWith("/");
                if (directoryRule) rule = rule.substring(0, rule.length() - 1);
                if (directoryRule && !directory) continue;
                if (glob(rule, anchored || rule.contains("/") ? prefix.toString() : parts[i]))
                    ignored = !negate;
            }
            // Match walk pruning even for explicit grep/list paths below an excluded ancestor.
            if (ignored) return true;
        }
        return false;
    }

    private static boolean glob(String glob, String path) {
        boolean[] previous = new boolean[path.length() + 1];
        previous[0] = true;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            boolean[] current = new boolean[path.length() + 1];
            if (c == '*') {
                boolean recursive = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (recursive) i++;
                boolean directory = recursive && i + 1 < glob.length() && glob.charAt(i + 1) == '/';
                if (directory) i++;
                current[0] = previous[0];
                boolean reachable = previous[0];
                for (int j = 1; j <= path.length(); j++) {
                    reachable |= previous[j - 1];
                    current[j] =
                            directory
                                    ? previous[j] || reachable && path.charAt(j - 1) == '/'
                                    : previous[j]
                                            || current[j - 1]
                                                    && (recursive || path.charAt(j - 1) != '/');
                }
            } else
                for (int j = 1; j <= path.length(); j++)
                    current[j] =
                            previous[j - 1]
                                    && (c == '?'
                                            ? path.charAt(j - 1) != '/'
                                            : c == path.charAt(j - 1));
            previous = current;
        }
        return previous[path.length()];
    }

    @Override
    public Map<String, Object> search(String pattern, int limit, CancellationToken cancellation)
            throws IOException {
        if (pattern.length() > 256 || limit < 1 || limit > 5000)
            throw new HarnessException("INVALID_LIMIT", "Search bounds invalid");
        CancellationToken token = bounded(cancellation);
        List<String> found = new ArrayList<>();
        List<String> rules = ignoreRules(token);
        boolean[] truncated = {false};
        walk(
                root,
                16,
                token,
                candidate -> {
                    if (ignored(candidate, rules)) return false;
                    if (regular(candidate) && glob(pattern, relative(candidate))) {
                        if (found.size() < limit) found.add(relative(candidate));
                        else truncated[0] = true;
                    }
                    return true;
                },
                () -> truncated[0]);
        return Map.of("paths", found, "truncated", truncated[0]);
    }

    @Override
    public Map<String, Object> grep(
            String literal, List<ExecPath> paths, int limit, CancellationToken cancellation)
            throws IOException {
        if (literal.isEmpty()
                || literal.length() > 1024
                || limit < 1
                || limit > 1000
                || paths.size() > 1000)
            throw new HarnessException("INVALID_LIMIT", "Grep bounds invalid");
        CancellationToken token = bounded(cancellation);
        List<Map<String, Object>> matches = new ArrayList<>();
        List<String> rules = ignoreRules(token);
        boolean truncated = false;
        int scannedPaths = 0;
        for (ExecPath path : paths) {
            if (matches.size() >= limit) {
                truncated = true;
                break;
            }
            token.throwIfCancelled();
            scannedPaths++;
            Path target = resolve(path);
            if (ignored(target, rules)) continue;
            int priorCount = matches.size();
            if (!regular(target)) continue;
            if (attributes(target).size() > SCAN_LIMIT) {
                truncated = true;
                continue;
            }
            CharsetDecoder decoder =
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
            try (Reader reader =
                    new BufferedReader(new InputStreamReader(input(target), decoder))) {
                int lineNumber = 1, scanned = 0, character;
                boolean clipped = false, binary = false;
                StringBuilder line = new StringBuilder();
                while ((character = reader.read()) != -1) {
                    if (++scanned % 4096 == 0) token.throwIfCancelled();
                    if (scanned > SCAN_LIMIT) {
                        truncated = true;
                        break;
                    }
                    if (character == 0) {
                        matches.subList(priorCount, matches.size()).clear();
                        binary = true;
                        break;
                    }
                    if (character != '\n') {
                        if (line.length() < 16384) line.append((char) character);
                        else clipped = true;
                        continue;
                    }
                    if (line.indexOf(literal) >= 0) {
                        if (matches.size() >= limit) {
                            truncated = true;
                            break;
                        }
                        int at = line.indexOf(literal), from = Math.max(0, at - 80);
                        matches.add(
                                Map.of(
                                        "path",
                                        relative(target),
                                        "line",
                                        lineNumber,
                                        "text",
                                        line.substring(
                                                from,
                                                Math.min(
                                                        line.length(),
                                                        at + literal.length() + 80))));
                    }
                    truncated |= clipped;
                    line.setLength(0);
                    clipped = false;
                    lineNumber++;
                }
                if (!binary && line.indexOf(literal) >= 0 && matches.size() < limit)
                    matches.add(
                            Map.of(
                                    "path",
                                    relative(target),
                                    "line",
                                    lineNumber,
                                    "text",
                                    line.substring(0, Math.min(line.length(), 1200))));
                truncated |= clipped;
            } catch (CharacterCodingException ex) {
                matches.subList(priorCount, matches.size()).clear();
            }
        }
        return Map.of(
                "matches",
                matches,
                "truncated",
                truncated,
                "mode",
                "literal",
                "scannedPaths",
                scannedPaths);
    }

    @Override
    public String gitDiff(String scope, CancellationToken cancellation) throws IOException {
        if (!Set.of("working", "staged").contains(scope))
            throw new HarnessException("INVALID_SCOPE", "git_diff scope is working or staged");
        CancellationToken token = bounded(cancellation);
        List<String> command =
                new ArrayList<>(
                        List.of(
                                trustedGit(),
                                "--no-pager",
                                "-c",
                                "core.fsmonitor=false",
                                "-c",
                                "core.hooksPath=",
                                "-c",
                                "diff.external=",
                                "-c",
                                "core.quotePath=true",
                                "-C",
                                root.toString(),
                                "diff",
                                "--no-ext-diff",
                                "--no-textconv"));
        if (scope.equals("staged")) command.add("--cached");
        command.add("--");
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile());
        var inherited = new HashMap<>(builder.environment());
        builder.environment().clear();
        for (String name :
                List.of("SystemRoot", "WINDIR", "SYSTEMROOT", "PATH", "PATHEXT", "TEMP", "TMP"))
            if (inherited.containsKey(name)) builder.environment().put(name, inherited.get(name));
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment()
                .put(
                        "GIT_CONFIG_GLOBAL",
                        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                                ? "NUL"
                                : "/dev/null");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        var overflow = new java.util.concurrent.atomic.AtomicBoolean();
        var diagnosticOverflow = new java.util.concurrent.atomic.AtomicBoolean();
        try (ExecutorService streams = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> stdout =
                    streams.submit(
                            () ->
                                    gitOutput(
                                            process,
                                            process.getInputStream(),
                                            2 * WRITE_LIMIT,
                                            overflow));
            Future<byte[]> stderr =
                    streams.submit(
                            () ->
                                    gitOutput(
                                            process,
                                            process.getErrorStream(),
                                            64 * 1024,
                                            diagnosticOverflow));
            try {
                while (!process.waitFor(20, TimeUnit.MILLISECONDS)) {
                    token.throwIfCancelled();
                }
                byte[] output = stdout.get(1, TimeUnit.SECONDS);
                stderr.get(1, TimeUnit.SECONDS);
                if (overflow.get())
                    throw new HarnessException("OUTPUT_LIMIT", "Git diff exceeds output bound");
                if (diagnosticOverflow.get())
                    throw new HarnessException(
                            "OUTPUT_LIMIT", "Git diagnostics exceed output bound");
                if (process.exitValue() != 0)
                    throw new HarnessException(
                            "GIT_DIFF_UNAVAILABLE", "No supported Git diff for this workspace");
                return new String(output, StandardCharsets.UTF_8);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new HarnessException("CANCELLED", "Git diff cancelled");
            } catch (ExecutionException | TimeoutException ex) {
                throw new HarnessException("GIT_DIFF_FAILED", "Git diff did not settle");
            } finally {
                process.destroyForcibly();
                process.getInputStream().close();
                process.getErrorStream().close();
            }
        }
    }

    private static byte[] gitOutput(
            Process process,
            InputStream input,
            int limit,
            java.util.concurrent.atomic.AtomicBoolean overflow)
            throws IOException {
        try (input) {
            byte[] output = input.readNBytes(limit + 1);
            if (output.length > limit) {
                overflow.set(true);
                process.destroyForcibly();
            }
            return output;
        }
    }

    private static String trustedGit() throws IOException {
        String explicit = System.getenv("JHARNESS_GIT");
        if (explicit != null) {
            Path selected = Path.of(explicit);
            if (!selected.isAbsolute() || !Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS))
                throw new HarnessException(
                        "GIT_DIFF_UNAVAILABLE", "Invalid explicit trusted Git executable");
            return selected.toRealPath().toString();
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<Path> candidates = new ArrayList<>();
        if (windows) {
            for (String name : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String folder = System.getenv(name);
                if (folder != null) candidates.add(Path.of(folder, "Git", "cmd", "git.exe"));
            }
        } else {
            candidates.add(Path.of("/usr/bin/git"));
            candidates.add(Path.of("/bin/git"));
        }
        for (Path path : candidates)
            if (Files.isRegularFile(path)) return path.toRealPath().toString();
        throw new HarnessException(
                "GIT_DIFF_UNAVAILABLE", "Trusted system Git executable unavailable");
    }
}
