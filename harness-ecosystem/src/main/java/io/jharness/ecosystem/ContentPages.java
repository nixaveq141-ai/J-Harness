package io.jharness.ecosystem;

import io.jharness.api.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Complete sanitization precedes slicing, including secrets crossing page boundaries. */
public final class ContentPages {
    private ContentPages() {}

    /** One immutable sanitized version per owning inspection service, never a permission cache. */
    public static final class Cache {
        private volatile Snapshot latest;

        public Map<String, Object> page(String clean, int offset, String version) {
            Snapshot snapshot = latest;
            if (snapshot == null || !snapshot.clean.equals(clean)) {
                snapshot = new Snapshot(clean);
                latest = snapshot;
            }
            return snapshot.page(offset, version);
        }
    }

    private static final class Snapshot {
        private final String clean, sha;
        private final int[] lines;

        private Snapshot(String clean) {
            if (clean.length() > 8388608)
                throw new HarnessException("CONTENT_LIMIT", "Sanitized content exceeds bound");
            this.clean = clean;
            sha = ContentPages.sha(clean);
            // Sparse line prefixes need at most 513 integers; no per-line unbounded index.
            lines = new int[clean.length() / 16384 + 1];
            int count = 0;
            for (int i = 0; i < clean.length(); i++) {
                if (clean.charAt(i) == '\n') count++;
                if ((i + 1) % 16384 == 0) lines[(i + 1) / 16384] = count;
            }
        }

        private Map<String, Object> page(int offset, String version) {
            return slice(clean, offset, version, sha, lines);
        }
    }

    public static Map<String, Object> page(String clean, int offset, String version) {
        return new Snapshot(clean).page(offset, version);
    }

    private static Map<String, Object> slice(
            String clean, int offset, String version, String sha, int[] lines) {
        if (clean.length() > 8388608)
            throw new HarnessException("CONTENT_LIMIT", "Sanitized content exceeds bound");
        if (offset < 0 || offset > clean.length())
            throw new HarnessException("INVALID_RANGE", "Invalid content offset");
        if (offset > 0 && offset < clean.length() && Character.isLowSurrogate(clean.charAt(offset)))
            throw new HarnessException("INVALID_RANGE", "Offset splits Unicode character");
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
        int block = offset / 16384;
        long line = 1L + lines[block];
        for (int i = block * 16384; i < offset; i++) if (clean.charAt(i) == '\n') line++;
        out.put("startLine", line);
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
