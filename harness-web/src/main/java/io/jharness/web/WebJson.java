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
        return MAPPER.writeValueAsBytes(value);
    }

    static Map<String, Object> decode(byte[] bytes) throws IOException {
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
