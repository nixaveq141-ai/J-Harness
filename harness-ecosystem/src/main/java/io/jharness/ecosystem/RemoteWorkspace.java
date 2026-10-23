package io.jharness.ecosystem;

import io.jharness.api.*;
import java.util.*;
import java.util.function.BiFunction;

/**
 * File operations are executed by the worker inside the environment; host paths never become tool
 * paths.
 */
public final class RemoteWorkspace implements WorkspaceFiles {
    @Override
    public WorkspaceFiles isolatedView() {
        return new RemoteWorkspace(exchange);
    }

    private final BiFunction<Map<String, Object>, CancellationToken, Map<String, Object>> exchange;
    private final Map<String, String> observed = new LinkedHashMap<>();

    public RemoteWorkspace(
            BiFunction<Map<String, Object>, CancellationToken, Map<String, Object>> exchange) {
        this.exchange = Objects.requireNonNull(exchange);
    }

    static String safe(ExecPath path) {
        String value = path.value().replace('\\', '/');
        if (value.startsWith("/")
                || value.contains(":")
                || value.codePoints().anyMatch(Character::isISOControl)
                || value.length() > 4096) throw BoundedJson.error("PATH_DENIED");
        for (String part : value.split("/"))
            if (part.equals("..")
                    || part.equalsIgnoreCase(".git")
                    || part.equalsIgnoreCase(".jharness")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
                throw BoundedJson.error("PATH_DENIED");
        var parts = new ArrayList<String>();
        for (String part : value.split("/"))
            if (!part.isEmpty() && !part.equals(".")) parts.add(part);
        return parts.isEmpty() ? "." : String.join("/", parts);
    }

    private Map<String, Object> call(
            String op, ExecPath path, Map<String, Object> extra, CancellationToken cancellation) {
        var request = new LinkedHashMap<>(extra);
        request.put("op", op);
        request.put("path", safe(path));
        cancellation.throwIfCancelled();
        var response = exchange.apply(Values.snapshot(request), cancellation);
        boolean mutation = Set.of("write", "edit", "patch").contains(op);
        try {
            if (response == null || response.containsKey("error") == response.containsKey("value"))
                throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
            if (response.containsKey("error")) {
                if (!(response.get("error") instanceof String code) || code.isBlank())
                    throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
                throw BoundedJson.error(code);
            }
            BoundedJson.encode(response);
            var value = Values.object(response.get("value"));
            if (op.equals("read")
                    && (!(value.get("text") instanceof String)
                            || !(value.get("version") instanceof String)))
                throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
            if (op.equals("version") && !(value.get("version") instanceof String))
                throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
            if (op.equals("write") || op.equals("edit")) {
                var change = change(value);
                if (!safe(change.path()).equals(safe(path)))
                    throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
            }
            return value;
        } catch (RuntimeException ex) {
            if (mutation) observed.clear();
            throw ex;
        }
    }

    void verifyFilter() {
        call("filter-info", root(), Map.of(), CancellationToken.NONE);
    }

    @Override
    public ExecPath root() {
        return new ExecPath(".");
    }

    @Override
    public synchronized String readText(ExecPath path) {
        return readText(path, 1024 * 1024);
    }

    @Override
    public synchronized String readText(ExecPath path, int maxBytes) {
        if (maxBytes < 1 || maxBytes > 1024 * 1024) throw new IllegalArgumentException("maxBytes");
        var value = call("read", path, Map.of("maxBytes", maxBytes), CancellationToken.NONE);
        remember(safe(path), (String) value.get("version"));
        return (String) value.get("text");
    }

    @Override
    public synchronized String versionOf(ExecPath path) {
        return (String) call("version", path, Map.of(), CancellationToken.NONE).get("version");
    }

    @Override
    public synchronized FileChange writeText(ExecPath path, String text, VersionGuard guard) {
        return write(
                path,
                text,
                true,
                Objects.requireNonNull(guard).expectedVersion(),
                CancellationToken.NONE);
    }

    @Override
    public synchronized Map<String, Object> readPage(
            ExecPath path, int start, int end, CancellationToken cancellation) {
        var value = call("page", path, Map.of("start", start, "end", end), cancellation);
        if (value.get("version") instanceof String version) remember(safe(path), version);
        return value;
    }

    @Override
    public Map<String, Object> list(
            ExecPath path, int depth, int limit, CancellationToken cancellation) {
        return call("list", path, Map.of("depth", depth, "limit", limit), cancellation);
    }

    @Override
    public Map<String, Object> search(String glob, int limit, CancellationToken cancellation) {
        return call("search", root(), Map.of("glob", glob, "limit", limit), cancellation);
    }

    @Override
    public Map<String, Object> grep(
            String text, List<ExecPath> paths, int limit, CancellationToken cancellation) {
        return call(
                "grep",
                root(),
                Map.of(
                        "literal",
                        text,
                        "paths",
                        paths.stream().map(RemoteWorkspace::safe).toList(),
                        "limit",
                        limit),
                cancellation);
    }

    @Override
    public synchronized FileChange write(
            ExecPath path,
            String text,
            boolean overwrite,
            String expected,
            CancellationToken cancellation) {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("content", text);
        extra.put("overwrite", overwrite);
        extra.put("expectedVersion", expected == null ? observed.get(safe(path)) : expected);
        var value = call("write", path, extra, cancellation);
        FileChange result = change(value);
        remember(safe(path), result.afterVersion());
        return result;
    }

    @Override
    public synchronized FileChange edit(
            ExecPath path,
            String oldText,
            String newText,
            int count,
            String expected,
            CancellationToken cancellation) {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("oldText", oldText);
        extra.put("newText", newText);
        extra.put("occurrences", count);
        extra.put("expectedVersion", expected == null ? observed.get(safe(path)) : expected);
        var value = call("edit", path, extra, cancellation);
        FileChange result = change(value);
        remember(safe(path), result.afterVersion());
        return result;
    }

    @Override
    public synchronized Map<String, Object> applyPatch(
            String patch, Map<String, String> versions, CancellationToken cancellation) {
        var all = new LinkedHashMap<String, String>();
        for (var entry : versions.entrySet())
            all.put(safe(new ExecPath(entry.getKey())), entry.getValue());
        for (var file : io.jharness.workspace.UnifiedPatch.parse(patch)) {
            String key = safe(new ExecPath(file.path()));
            if (!all.containsKey(key) && observed.containsKey(key)) all.put(key, observed.get(key));
        }
        var result = call("patch", root(), Map.of("patch", patch, "versions", all), cancellation);
        // A partial/unknown commit must never leave implicit authority from an older observation.
        observed.clear();
        if (result.get("changes") instanceof List<?> changes)
            for (Object raw : changes) {
                var change = Values.object(raw);
                if (change.get("path") instanceof String path
                        && change.get("afterVersion") instanceof String version)
                    remember(safe(new ExecPath(path)), version);
            }
        return result;
    }

    @Override
    public String gitDiff(String scope, CancellationToken cancellation) {
        return (String) call("diff", root(), Map.of("scope", scope), cancellation).get("diff");
    }

    private void remember(String path, String version) {
        observed.remove(path);
        observed.put(path, version);
        if (observed.size() > 10000) observed.remove(observed.keySet().iterator().next());
    }

    private static FileChange change(Map<String, Object> value) {
        for (String key : List.of("path", "beforeVersion", "afterVersion", "diff"))
            if (!(value.get(key) instanceof String))
                throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
        safe(new ExecPath((String) value.get("path")));
        if (((String) value.get("afterVersion")).isBlank()
                || ((String) value.get("beforeVersion")).isBlank())
            throw BoundedJson.error("WORKER_PROTOCOL_ERROR");
        return new FileChange(
                new ExecPath((String) value.get("path")),
                (String) value.get("beforeVersion"),
                (String) value.get("afterVersion"),
                (String) value.get("diff"),
                Values.object(value.getOrDefault("metadata", Map.of())));
    }
}
