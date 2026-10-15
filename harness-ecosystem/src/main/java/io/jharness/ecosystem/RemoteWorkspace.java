package io.jharness.ecosystem;

import io.jharness.api.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, String> observed = new ConcurrentHashMap<>();

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
        return value;
    }

    private Map<String, Object> call(
            String op, ExecPath path, Map<String, Object> extra, CancellationToken cancellation) {
        var request = new LinkedHashMap<>(extra);
        request.put("op", op);
        request.put("path", safe(path));
        cancellation.throwIfCancelled();
        var response = exchange.apply(Values.snapshot(request), cancellation);
        if (response.get("error") instanceof String code) throw BoundedJson.error(code);
        return Values.object(response.get("value"));
    }

    void verifyFilter() {
        call("filter-info", root(), Map.of(), CancellationToken.NONE);
    }

    @Override
    public ExecPath root() {
        return new ExecPath(".");
    }

    @Override
    public String readText(ExecPath path) {
        var value = call("read", path, Map.of(), CancellationToken.NONE);
        observed.put(safe(path), (String) value.get("version"));
        return (String) value.get("text");
    }

    @Override
    public String versionOf(ExecPath path) {
        return (String) call("version", path, Map.of(), CancellationToken.NONE).get("version");
    }

    @Override
    public FileChange writeText(ExecPath path, String text, VersionGuard guard) {
        return write(
                path,
                text,
                true,
                guard == null ? null : guard.expectedVersion(),
                CancellationToken.NONE);
    }

    @Override
    public Map<String, Object> readPage(
            ExecPath path, int start, int end, CancellationToken cancellation) {
        var value = call("page", path, Map.of("start", start, "end", end), cancellation);
        if (value.get("version") instanceof String version) observed.put(safe(path), version);
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
    public FileChange write(
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
        observed.put(safe(path), result.afterVersion());
        return result;
    }

    @Override
    public FileChange edit(
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
        observed.put(safe(path), result.afterVersion());
        return result;
    }

    @Override
    public Map<String, Object> applyPatch(
            String patch, Map<String, String> versions, CancellationToken cancellation) {
        var all = new LinkedHashMap<>(observed);
        all.putAll(versions);
        return call("patch", root(), Map.of("patch", patch, "versions", all), cancellation);
    }

    @Override
    public String gitDiff(String scope, CancellationToken cancellation) {
        return (String) call("diff", root(), Map.of("scope", scope), cancellation).get("diff");
    }

    private static FileChange change(Map<String, Object> value) {
        return new FileChange(
                new ExecPath((String) value.get("path")),
                (String) value.get("beforeVersion"),
                (String) value.get("afterVersion"),
                (String) value.get("diff"));
    }
}
