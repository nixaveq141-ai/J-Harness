package io.jharness.web;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.Map;

final class WebJson {
    private WebJson() {}

    static final int REQUEST_LIMIT = 65536, RESPONSE_LIMIT = 524288;
    private static final ObjectMapper MAPPER =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(24)
                                                    .maxStringLength(REQUEST_LIMIT)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static byte[] encode(Object value) throws IOException {
        return encode(value, 8 * 1024 * 1024);
    }

    static byte[] encode(Object value, int limit) throws IOException {
        try {
            return io.jharness.api.JsonEncoding.encode(value, limit, 100000, 24, "RESPONSE_LIMIT");
        } catch (io.jharness.api.HarnessException invalid) {
            throw new IOException("Bounded JSON output", invalid);
        }
    }

    static byte[] encodeState(Object value) throws IOException {
        try {
            return io.jharness.api.JsonEncoding.encode(
                    value, REQUEST_LIMIT, 8192, 24, "RESPONSE_LIMIT");
        } catch (io.jharness.api.HarnessException invalid) {
            throw new IOException("Bounded state output", invalid);
        }
    }

    static Map<String, Object> decode(byte[] bytes) throws IOException {
        if (bytes.length > REQUEST_LIMIT) throw new IOException("JSON request byte limit");
        try (var parser = MAPPER.getFactory().createParser(bytes)) {
            int nodes = 0;
            while (parser.nextToken() != null)
                if (++nodes > 8192) throw new IOException("JSON node limit");
        }
        var value = MAPPER.readValue(bytes, new TypeReference<Map<String, Object>>() {});
        if (value == null) throw new IOException("Expected object");
        return value;
    }
}
