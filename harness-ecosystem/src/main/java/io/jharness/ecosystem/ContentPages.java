package io.jharness.ecosystem;

import io.jharness.api.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Complete sanitization precedes slicing, including secrets crossing page boundaries. */
public final class ContentPages {
    private ContentPages() {}

    public static Map<String, Object> page(String clean, int offset, String version) {
        if (clean.length() > 8388608)
            throw new HarnessException("CONTENT_LIMIT", "Sanitized content exceeds bound");
        if (offset < 0 || offset > clean.length())
            throw new HarnessException("INVALID_RANGE", "Invalid content offset");
        if (offset > 0 && offset < clean.length() && Character.isLowSurrogate(clean.charAt(offset)))
            throw new HarnessException("INVALID_RANGE", "Offset splits Unicode character");
        String sha = sha(clean);
        if (!version.isEmpty() && !sha.equals(version))
            throw new HarnessException("CONTENT_CHANGED", "Content changed; reread explicitly");
        int end = Math.min(clean.length(), offset + 16384);
        if (end < clean.length()
                && end > offset
                && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        var out = new LinkedHashMap<String, Object>();
        out.put("text", clean.substring(offset, end));
        out.put("offset", offset);
        out.put("nextOffset", end);
        out.put("more", end < clean.length());
        out.put("totalCharacters", clean.length());
        out.put("contentSha256", sha);
        out.put("sanitized", true);
        out.put("offsetUnit", "UTF-16 code units");
        out.put("startLine", 1 + clean.chars().limit(offset).filter(c -> c == '\n').count());
        return out;
    }

    public static String sha(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
