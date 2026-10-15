package io.jharness.ecosystem;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import io.jharness.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class BoundedJson {
    private BoundedJson() {}

    static final int LIMIT = 4 * 1024 * 1024;
    static final ObjectMapper MAPPER =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(64)
                                                    .maxStringLength(LIMIT)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static byte[] encode(Object value) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(value);
            if (bytes.length > LIMIT) throw error("PAYLOAD_LIMIT");
            return bytes;
        } catch (IOException ex) {
            throw error("INVALID_JSON");
        }
    }

    static Map<String, Object> decode(byte[] bytes) {
        if (bytes.length > LIMIT) throw error("PAYLOAD_LIMIT");
        try {
            try (var parser = MAPPER.getFactory().createParser(bytes)) {
                int tokens = 0;
                while (parser.nextToken() != null)
                    if (++tokens > 100000) throw error("PAYLOAD_NODE_LIMIT");
            }
            return Values.object(
                    MAPPER.readValue(bytes, new TypeReference<Map<String, Object>>() {}));
        } catch (IOException ex) {
            throw error("INVALID_JSON");
        }
    }

    static String text(Object value) {
        return new String(encode(value), StandardCharsets.UTF_8);
    }

    static HarnessException error(String code) {
        return new HarnessException(code, code);
    }
}
