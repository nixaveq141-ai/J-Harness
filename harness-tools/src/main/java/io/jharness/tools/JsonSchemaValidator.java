package io.jharness.tools;

import io.jharness.api.JsonSchema;
import java.util.Map;

/** Compatibility facade for the shared declaration and value contract. */
public final class JsonSchemaValidator {
    private final JsonSchema schema = new JsonSchema();

    public void validate(Map<String, Object> declaration, Object value) {
        schema.validate(declaration, value);
    }

    public void checkSchema(Map<String, Object> declaration) {
        schema.checkSchema(declaration);
    }
}
