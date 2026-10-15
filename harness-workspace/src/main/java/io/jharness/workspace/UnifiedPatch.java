package io.jharness.workspace;

import io.jharness.api.*;
import java.util.*;
import java.util.regex.*;

/** Bounded exact unified diff subset: create/update, no fuzz, rename, binary or deletion. */
final class UnifiedPatch {
    record FilePatch(String path, boolean create, List<String> hunks) {}

    private static final Pattern HUNK =
            Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

    private UnifiedPatch() {}

    static List<FilePatch> parse(String patch) {
        if (patch == null || patch.length() > 1024 * 1024) fail("PATCH_LIMIT");
        String[] lines = patch.replace("\r\n", "\n").split("\n", -1);
        List<FilePatch> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (int i = 0; i < lines.length; ) {
            if (lines[i].isEmpty()
                    || lines[i].startsWith("diff --git ")
                    || lines[i].startsWith("index ")) {
                i++;
                continue;
            }
            if (!lines[i].startsWith("--- ")
                    || i + 1 >= lines.length
                    || !lines[i + 1].startsWith("+++ ")) fail("INVALID_PATCH");
            String old = header(lines[i++].substring(4)), path = header(lines[i++].substring(4));
            boolean create = old.equals("/dev/null");
            if (path.equals("/dev/null") || !create && !old.equals(path)) fail("UNSUPPORTED_PATCH");
            if (!paths.add(path) || files.size() >= 64) fail("PATCH_LIMIT");
            List<String> hunks = new ArrayList<>();
            while (i < lines.length
                    && !lines[i].startsWith("--- ")
                    && !lines[i].startsWith("diff --git ")) {
                if (i == lines.length - 1 && lines[i].isEmpty()) {
                    i++;
                    break;
                }
                if (lines[i].startsWith("\\ No newline")) fail("UNSUPPORTED_PATCH");
                hunks.add(lines[i++]);
            }
            if (hunks.isEmpty() || !hunks.getFirst().startsWith("@@ ")) fail("INVALID_PATCH");
            files.add(new FilePatch(path, create, List.copyOf(hunks)));
        }
        if (files.isEmpty()) fail("INVALID_PATCH");
        return List.copyOf(files);
    }

    private static String header(String line) {
        String path = line.split("\t", 2)[0];
        if (path.startsWith("a/") || path.startsWith("b/")) path = path.substring(2);
        return path;
    }

    static String apply(FilePatch patch, String before) {
        String normalized = before.replace("\r\n", "\n");
        if (!normalized.isEmpty() && !normalized.endsWith("\n")) fail("UNSUPPORTED_PATCH");
        List<String> old =
                normalized.isEmpty()
                        ? List.of()
                        : Arrays.asList(
                                normalized.substring(0, normalized.length() - 1).split("\n", -1));
        List<String> result = new ArrayList<>();
        int cursor = 0, index = 0;
        while (index < patch.hunks().size()) {
            Matcher matcher = HUNK.matcher(patch.hunks().get(index++));
            if (!matcher.matches()) fail("INVALID_PATCH");
            int oldStart = number(matcher.group(1)),
                    oldCount = matcher.group(2) == null ? 1 : number(matcher.group(2));
            int newStart = number(matcher.group(3)),
                    newCount = matcher.group(4) == null ? 1 : number(matcher.group(4));
            int start = oldCount == 0 ? oldStart : oldStart - 1;
            if (start < cursor || start > old.size()) fail("PATCH_CONFLICT");
            result.addAll(old.subList(cursor, start));
            cursor = start;
            if (newStart != result.size() + (newCount == 0 ? 0 : 1)) fail("PATCH_CONFLICT");
            int removed = 0, added = 0;
            while (index < patch.hunks().size() && !patch.hunks().get(index).startsWith("@@ ")) {
                String line = patch.hunks().get(index++);
                if (line.isEmpty() || " +-".indexOf(line.charAt(0)) < 0) fail("INVALID_PATCH");
                char action = line.charAt(0);
                String text = line.substring(1);
                if (action != '+') {
                    if (cursor >= old.size() || !old.get(cursor).equals(text))
                        fail("PATCH_CONFLICT");
                    cursor++;
                    removed++;
                }
                if (action != '-') {
                    result.add(text);
                    added++;
                }
            }
            if (removed != oldCount || added != newCount) fail("INVALID_PATCH");
        }
        result.addAll(old.subList(cursor, old.size()));
        return result.isEmpty() ? "" : String.join("\n", result) + "\n";
    }

    private static int number(String value) {
        try {
            int n = Integer.parseInt(value);
            if (n > 1000000) fail("PATCH_LIMIT");
            return n;
        } catch (NumberFormatException ex) {
            fail("INVALID_PATCH");
            return 0;
        }
    }

    private static void fail(String code) {
        throw new HarnessException(
                code, "Exact unified patch rejected; use read/edit for unsupported formats");
    }
}
