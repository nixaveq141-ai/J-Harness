package io.jharness.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JSON values only; byte, parser-token and container-depth bounds apply during writing. */
public final class JsonEncoding {
    private JsonEncoding() {}

    public static byte[] encode(Object value, int bytes, int tokens, int depth, String code) {
        if (bytes < 1 || tokens < 1 || depth < 1) throw new IllegalArgumentException("JSON bounds");
        var writer = new Writer(bytes, tokens, depth, code);
        writer.value(value, 0);
        return writer.toByteArray();
    }

    public static String text(Object value, int bytes, String code) {
        return new String(encode(value, bytes, 100000, 64, code), StandardCharsets.UTF_8);
    }

    public static void writeTo(
            Object value, int bytes, int tokens, int depth, String code, OutputStream output)
            throws IOException {
        if (bytes < 1 || tokens < 1 || depth < 1) throw new IllegalArgumentException("JSON bounds");
        var writer = new Writer(bytes, tokens, depth, code, Objects.requireNonNull(output));
        try {
            writer.value(value, 0);
            writer.finish();
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /** Bound both the input and sanitizer-expanded text before allocating its UTF-8 snapshot. */
    public static byte[] utf8(String text, int limit, String code) {
        utf8Length(text, limit, code);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static int utf8Length(String text, int limit, String code) {
        if (text.length() > limit) throw error(code);
        long count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 128) count++;
            else if (c < 2048) count += 2;
            else if (Character.isHighSurrogate(c)
                    && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                count += 4;
                i++;
            } else count += Character.isSurrogate(c) ? 1 : 3;
            if (count > limit) throw error(code);
        }
        return (int) count;
    }

    /** Exactly the Map/List display-character metric formerly computed by full toString copies. */
    public static long displayCharacters(Object value) {
        if (value instanceof Map<?, ?> map) {
            long size = 2;
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) size += 2;
                first = false;
                size += displayCharacters(entry.getKey()) + 1 + displayCharacters(entry.getValue());
            }
            return size;
        }
        if (value instanceof List<?> list) {
            long size = 2;
            boolean first = true;
            for (Object item : list) {
                if (!first) size += 2;
                first = false;
                size += displayCharacters(item);
            }
            return size;
        }
        return value instanceof String text ? text.length() : String.valueOf(value).length();
    }

    public static String prefix(String text, int characters) {
        int end = Math.min(text.length(), characters);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, "Bounded JSON/text limit or invalid value");
    }

    private static final class Writer extends ByteArrayOutputStream {
        private final int byteLimit, maxDepth;
        private int remaining;
        private final String code;
        private final OutputStream sink;
        private int emitted;

        Writer(int bytes, int tokens, int depth, String code) {
            this(bytes, tokens, depth, code, null);
        }

        Writer(int bytes, int tokens, int depth, String code, OutputStream sink) {
            super(Math.min(bytes, 8192));
            byteLimit = bytes;
            remaining = tokens;
            maxDepth = depth;
            this.code = Objects.requireNonNull(code);
            this.sink = sink;
        }

        private void finish() {
            if (sink != null && count > 0) {
                try {
                    sink.write(buf, 0, count);
                    emitted += count;
                    count = 0;
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        }

        private void token() {
            if (--remaining < 0) throw error(code);
        }

        private void b(int value) {
            if ((long) emitted + count >= byteLimit) throw error(code);
            if (sink != null && count == buf.length) finish();
            if (count == buf.length)
                buf =
                        Arrays.copyOf(
                                buf,
                                (int)
                                        Math.min(
                                                byteLimit,
                                                Math.max((long) count + 1, (long) buf.length * 2)));
            buf[count++] = (byte) value;
        }

        private void ascii(String text) {
            for (int i = 0; i < text.length(); i++) b(text.charAt(i));
        }

        private void string(String text) {
            b('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"', '\\' -> {
                        b('\\');
                        b(c);
                    }
                    case '\n' -> ascii("\\n");
                    case '\r' -> ascii("\\r");
                    case '\t' -> ascii("\\t");
                    case '\b' -> ascii("\\b");
                    case '\f' -> ascii("\\f");
                    default -> {
                        if (c < 32
                                || Character.isSurrogate(c)
                                        && !(Character.isHighSurrogate(c)
                                                && i + 1 < text.length()
                                                && Character.isLowSurrogate(text.charAt(i + 1)))) {
                            ascii("\\u");
                            for (int shift = 12; shift >= 0; shift -= 4)
                                b("0123456789abcdef".charAt((c >> shift) & 15));
                        } else if (c < 128) b(c);
                        else if (c < 2048) {
                            b(192 | (c >> 6));
                            b(128 | (c & 63));
                        } else if (Character.isHighSurrogate(c)) {
                            int cp = Character.toCodePoint(c, text.charAt(++i));
                            b(240 | (cp >> 18));
                            b(128 | ((cp >> 12) & 63));
                            b(128 | ((cp >> 6) & 63));
                            b(128 | (cp & 63));
                        } else {
                            b(224 | (c >> 12));
                            b(128 | ((c >> 6) & 63));
                            b(128 | (c & 63));
                        }
                    }
                }
            }
            b('"');
        }

        private void value(Object value, int depth) {
            token();
            if (value instanceof Map<?, ?> map) {
                if (depth >= maxDepth) throw error(code);
                b('{');
                boolean first = true;
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw error("INVALID_JSON");
                    if (!first) b(',');
                    first = false;
                    token();
                    string(key);
                    b(':');
                    value(entry.getValue(), depth + 1);
                }
                token();
                b('}');
            } else if (value instanceof List<?> list) {
                if (depth >= maxDepth) throw error(code);
                b('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) b(',');
                    first = false;
                    value(item, depth + 1);
                }
                token();
                b(']');
            } else if (value instanceof String text) string(text);
            else if (value == null) ascii("null");
            else if (value instanceof Boolean flag) ascii(flag ? "true" : "false");
            else if (value instanceof Number number) {
                if (number instanceof Double d && !Double.isFinite(d)
                        || number instanceof Float f && !Float.isFinite(f))
                    throw error("INVALID_JSON");
                if (!(number instanceof Byte
                        || number instanceof Short
                        || number instanceof Integer
                        || number instanceof Long
                        || number instanceof Float
                        || number instanceof Double
                        || number instanceof java.math.BigInteger
                        || number instanceof java.math.BigDecimal)) throw error("INVALID_JSON");
                if (number instanceof java.math.BigInteger big
                                && (long) big.bitLength() > 4L * byteLimit
                        || number instanceof java.math.BigDecimal decimal
                                && (long) decimal.unscaledValue().bitLength() > 4L * byteLimit)
                    throw error(code);
                ascii(number.toString());
            } else throw error("INVALID_JSON");
        }
    }
}
