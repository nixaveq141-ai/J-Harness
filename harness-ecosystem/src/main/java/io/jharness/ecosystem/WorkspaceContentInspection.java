package io.jharness.ecosystem;

import io.jharness.api.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Uses fresh isolated views; previewing never changes the model's version guard cache. */
public final class WorkspaceContentInspection implements ContentInspection {
    private final WorkspaceFiles files;
    private final ArtifactStore artifacts;
    private final DataSanitizer sanitizer;
    private final PolicyEngine policy;
    private final RootCheck check;
    private final String session;

    @FunctionalInterface
    public interface RootCheck {
        void verify() throws IOException;
    }

    public WorkspaceContentInspection(
            WorkspaceFiles files,
            ArtifactStore artifacts,
            DataSanitizer sanitizer,
            PolicyEngine policy,
            RootCheck check) {
        this(files, artifacts, sanitizer, policy, check, "");
    }

    public WorkspaceContentInspection(
            WorkspaceFiles files,
            ArtifactStore artifacts,
            DataSanitizer sanitizer,
            PolicyEngine policy,
            RootCheck check,
            String session) {
        this.session = Objects.requireNonNull(session);
        this.files = Objects.requireNonNull(files);
        this.artifacts = Objects.requireNonNull(artifacts);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.policy = Objects.requireNonNull(policy);
        this.check = Objects.requireNonNull(check);
    }

    private void allow(String tool, Map<String, Object> args, CancellationToken token)
            throws IOException {
        token.throwIfCancelled();
        check.verify();
        if (policy.evaluate(
                        new ToolInvocation(
                                "host-inspection", tool, args, SideEffectLevel.READ_ONLY),
                        new ToolContext(new PluginScope("host-inspection", "inspection"), token))
                != PolicyEngine.Decision.ALLOW)
            throw new HarnessException(
                    "INSPECTION_DENIED", "Read policy does not allow inspection");
    }

    private static void pathAllowed(String path) {
        if (path.length() > 512) throw new HarnessException("PATH_DENIED", "Path too long");
        for (String part : path.replace('\\', '/').split("/")) {
            String name = part.toLowerCase(Locale.ROOT);
            if (Set.of(
                                    ".git",
                                    ".jharness",
                                    ".ssh",
                                    ".aws",
                                    ".codex",
                                    ".agents",
                                    ".dsh-tmp",
                                    ".build-cache")
                            .contains(name)
                    || name.startsWith(".env"))
                throw new HarnessException("PATH_DENIED", "Protected metadata");
        }
    }

    public Map<String, Object> list(String path, CancellationToken token) throws IOException {
        pathAllowed(path);
        allow("list_directory", Map.of("path", path), token);
        var out =
                new LinkedHashMap<>(
                        sanitizer.sanitize(
                                files.isolatedView().list(new ExecPath(path), 1, 200, token)));
        if (out.get("entries") instanceof List<?> entries)
            out.put(
                    "entries",
                    entries.stream()
                            .filter(
                                    v -> {
                                        try {
                                            pathAllowed((String) Values.object(v).get("path"));
                                            return true;
                                        } catch (HarnessException denied) {
                                            return false;
                                        }
                                    })
                            .toList());
        out.put("source", "current-workspace");
        out.put("path", path);
        out.put("readAt", Instant.now().toString());
        return sanitizer.sanitize(out);
    }

    public Map<String, Object> file(
            String path, int offset, int line, String version, CancellationToken token)
            throws IOException {
        pathAllowed(path);
        allow("read_file", Map.of("path", path), token);
        if (path.length() > 512 || line < 0 || line > 1000000)
            throw new HarnessException("INVALID_RANGE", "Invalid preview range");
        var view = files.isolatedView();
        var p = new ExecPath(path);
        var snapshot = view.readSnapshot(p);
        String before = snapshot.version(), text = snapshot.text();
        token.throwIfCancelled();
        if (!before.equals(view.versionOf(p)))
            throw new HarnessException("FILE_CONFLICT", "File changed during read");
        String clean = (String) sanitizer.sanitize(Map.of("text", text)).get("text");
        if (line > 0) {
            if (offset != 0) throw new HarnessException("INVALID_RANGE", "Choose line or offset");
            for (int i = 1; i < line; i++) {
                int next = clean.indexOf('\n', offset);
                if (next < 0) {
                    offset = clean.length();
                    break;
                }
                offset = next + 1;
            }
        }
        if (!version.isEmpty() && !before.equals(version))
            throw new HarnessException("FILE_CONFLICT", "File version changed");
        var out = new LinkedHashMap<>(ContentPages.page(clean, offset, ""));
        out.put("source", "current-workspace");
        out.put("path", path);
        out.put("fileVersion", before);
        out.put("readAt", Instant.now().toString());
        return sanitizer.sanitize(out);
    }

    public Map<String, Object> artifact(
            String session, String id, int offset, String version, CancellationToken token)
            throws IOException {
        if (!this.session.isEmpty() && !this.session.equals(session))
            throw new HarnessException("ARTIFACT_DENIED", "Artifact outside bound session");
        if (id == null
                || !id.matches("[0-9a-f-]{36}-[0-9a-f]{64}")
                || offset < 0
                || offset > 4194304)
            throw new HarnessException("ARTIFACT_DENIED", "Invalid artifact reference or offset");
        allow("read_artifact", Map.of("id", id), token);
        token.throwIfCancelled();
        String text = artifacts.readText(session, id);
        token.throwIfCancelled();
        String clean = (String) sanitizer.sanitize(Map.of("text", text)).get("text");
        var out = new LinkedHashMap<>(ContentPages.page(clean, offset, version));
        out.put("source", "recorded-artifact");
        out.put("sessionId", session);
        out.put("artifactId", id);
        out.put("artifactSha256", id.substring(37));
        return sanitizer.sanitize(out);
    }
}
