package io.jharness.api;

import java.util.Locale;

/** Translate an offset in ROOT-lowercased text back to the original UTF-16 coordinate system. */
public final class UnicodeSearch {
    private UnicodeSearch() {}

    public static String snippet(String original, int foldedOffset, int before, int limit) {
        int folded = 0, at = original.length();
        for (int i = 0; i < original.length(); ) {
            int cp = original.codePointAt(i), width = Character.charCount(cp);
            int lowerWidth =
                    cp < 128
                            ? 1
                            : new String(Character.toChars(cp)).toLowerCase(Locale.ROOT).length();
            if (foldedOffset < folded + lowerWidth) {
                at = i;
                break;
            }
            folded += lowerWidth;
            i += width;
        }
        int start = Math.max(0, at - before);
        if (start > 0
                && start < original.length()
                && Character.isLowSurrogate(original.charAt(start))) start--;
        int end = Math.min(original.length(), start + limit);
        if (end > start
                && end < original.length()
                && Character.isHighSurrogate(original.charAt(end - 1))) end--;
        return original.substring(start, end);
    }
}
