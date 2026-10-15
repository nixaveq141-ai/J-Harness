package io.jharness.workspace;

import io.jharness.api.*;
import java.util.*;

/** Immutable host configuration for discovery, never an access-control grant. */
public record WorkspaceSearchFilter(
        boolean useDefaultExcludes, boolean useGitignore, List<String> rules) {
    public WorkspaceSearchFilter {
        rules = checkedRules(rules);
    }

    public static WorkspaceSearchFilter defaults() {
        return new WorkspaceSearchFilter(true, true, List.of());
    }

    public static WorkspaceSearchFilter from(Object raw) {
        if (!(raw instanceof Map<?, ?> map)
                || !Set.of("useDefaultExcludes", "useGitignore", "rules").containsAll(map.keySet())
                || map.containsKey("useDefaultExcludes")
                        && !(map.get("useDefaultExcludes") instanceof Boolean)
                || map.containsKey("useGitignore") && !(map.get("useGitignore") instanceof Boolean))
            throw invalid();
        Object rows = map.containsKey("rules") ? map.get("rules") : List.of();
        if (!(rows instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String)))
            throw invalid();
        return new WorkspaceSearchFilter(
                !Boolean.FALSE.equals(map.get("useDefaultExcludes")),
                !Boolean.FALSE.equals(map.get("useGitignore")),
                list.stream().map(String.class::cast).toList());
    }

    public Map<String, Object> configuration() {
        return Map.of(
                "useDefaultExcludes",
                useDefaultExcludes,
                "useGitignore",
                useGitignore,
                "rules",
                rules);
    }

    public static List<String> checkedRules(List<String> rows) {
        if (rows == null) throw invalid();
        if (rows.size() > 128
                || rows.stream().filter(Objects::nonNull).mapToInt(String::length).sum() > 16384)
            throw new HarnessException("SEARCH_FILTER_LIMIT", "Search rules exceed limits");
        var result = new ArrayList<String>();
        for (String raw : rows) {
            if (raw == null) throw invalid();
            if (raw.length() > 256)
                throw new HarnessException("SEARCH_FILTER_LIMIT", "Search rule too long");
            if (raw.isBlank() || raw.startsWith("#")) continue;
            String rule = raw.startsWith("!") ? raw.substring(1) : raw;
            if (!raw.equals(raw.strip())
                    || rule.isEmpty()
                    || rule.startsWith("!")
                    || rule.contains("\\")
                    || rule.contains(":")
                    || rule.contains("[")
                    || rule.contains("]")
                    || rule.startsWith("//")
                    || rule.codePoints().anyMatch(Character::isISOControl)
                    || Arrays.stream(rule.split("/", -1))
                            .anyMatch(p -> p.equals("..") || p.equals("."))) throw invalid();
            if (rule.equals("/")) throw invalid();
            result.add(raw);
        }
        return List.copyOf(result);
    }

    private static HarnessException invalid() {
        return new HarnessException(
                "INVALID_SEARCH_FILTER", "Unsupported search filter configuration or rule");
    }
}
