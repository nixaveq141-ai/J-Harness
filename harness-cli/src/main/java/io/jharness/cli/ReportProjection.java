package io.jharness.cli;

import java.util.*;

/**
 * A separate output budget. Omitted data remains in the source event, never an execution verdict.
 */
final class ReportProjection {
    private int characters, nodes;
    private final int fieldCharacters;
    private boolean truncated;

    ReportProjection(int characters, int nodes, int fieldCharacters) {
        this.characters = characters;
        this.nodes = nodes;
        this.fieldCharacters = fieldCharacters;
    }

    boolean truncated() {
        return truncated;
    }

    Object project(Object value) {
        return project(value, 0);
    }

    private Object project(Object value, int depth) {
        if (nodes-- <= 0 || depth > 12 || characters <= 0) {
            truncated = true;
            return "[REPORT_OMITTED: read source event]";
        }
        if (value instanceof String text) {
            int end = Math.min(text.length(), Math.min(characters, fieldCharacters));
            if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            characters -= end;
            if (end < text.length()) {
                truncated = true;
                return text.substring(0, end) + "[REPORT_TRUNCATED]";
            }
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            var out = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                String key = (String) entry.getKey();
                if (nodes <= 0 || characters < key.length() || key.length() > 128) {
                    truncated = true;
                    out.put("reportOmittedFields", map.size() - out.size());
                    break;
                }
                characters -= key.length();
                out.put(key, project(entry.getValue(), depth + 1));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            var out = new ArrayList<Object>();
            for (var item : list) {
                if (nodes <= 0 || characters <= 0) {
                    truncated = true;
                    out.add(Map.of("reportOmittedItems", list.size() - out.size()));
                    break;
                }
                out.add(project(item, depth + 1));
            }
            return out;
        }
        return value;
    }

    static String text(String value, int limit) {
        int end = Math.min(value.length(), limit);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return end == value.length() ? value : value.substring(0, end) + "[REPORT_TRUNCATED]";
    }
}
