package io.jharness.workspace;

import java.util.*;

/** A valid full replacement hunk, avoiding quadratic LCS memory on large files. */
public final class UnifiedDiff {
    private UnifiedDiff() {}

    public static String between(String path, String before, String after) {
        if (before.equals(after)) return "";
        List<String> oldLines = lines(before), newLines = lines(after);
        StringBuilder diff = new StringBuilder("--- a/" + path + "\n+++ b/" + path + "\n");
        diff.append("@@ -")
                .append(oldLines.isEmpty() ? 0 : 1)
                .append(",")
                .append(oldLines.size())
                .append(" +")
                .append(newLines.isEmpty() ? 0 : 1)
                .append(",")
                .append(newLines.size())
                .append(" @@\n");
        append(diff, oldLines, '-', before);
        append(diff, newLines, '+', after);
        return diff.toString();
    }

    private static List<String> lines(String text) {
        if (text.isEmpty()) return List.of();
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        return Arrays.asList(lines)
                .subList(0, text.endsWith("\n") ? lines.length - 1 : lines.length);
    }

    private static void append(
            StringBuilder out, List<String> lines, char prefix, String original) {
        for (String line : lines) out.append(prefix).append(line).append('\n');
        if (!lines.isEmpty() && !original.endsWith("\n"))
            out.append("\\ No newline at end of file\n");
    }
}
