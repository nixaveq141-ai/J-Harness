package io.jharness.tools;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {
    @Test
    void misplacedConstraintsAndConflictingBoundsFailClosedAndNumbersCompareSemantically() {
        for (Map<String, Object> bad :
                List.of(
                        Map.<String, Object>of("type", "string", "minimum", 1),
                        Map.<String, Object>of("type", "integer", "minLength", 1),
                        Map.<String, Object>of("type", "string", "required", List.of("x")),
                        Map.<String, Object>of("type", "number", "minimum", 3, "maximum", 1))) {
            assertEquals(
                    "INVALID_SCHEMA",
                    assertThrows(HarnessException.class, () -> validator.checkSchema(bad)).code());
        }
        validator.validate(Map.of("type", "integer", "enum", List.of(1)), 1.0);
        validator.validate(Map.of("type", "number", "const", 2), new java.math.BigDecimal("2.00"));
    }

    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void nestedRequiredPropertiesAndNumericAndArrayBoundsAreEnforced() {
        Map<String, Object> schema =
                Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "required",
                        List.of("count", "names"),
                        "properties",
                        Map.of(
                                "count",
                                Map.of("type", "integer", "minimum", 1, "maximum", 3),
                                "names",
                                Map.of(
                                        "type",
                                        "array",
                                        "minItems",
                                        1,
                                        "maxItems",
                                        2,
                                        "items",
                                        Map.of("type", "string", "minLength", 1, "maxLength", 4))));
        validator.validate(schema, Map.of("count", 2, "names", List.of("甲")));
        for (Map<String, Object> bad :
                List.of(
                        Map.<String, Object>of("count", 0, "names", List.of("a")),
                        Map.<String, Object>of("count", 1.5, "names", List.of("a")),
                        Map.<String, Object>of("count", 1, "names", List.of()),
                        Map.<String, Object>of("count", 1, "names", List.of("long-name")),
                        Map.<String, Object>of("count", 1, "names", List.of("a"), "extra", true),
                        Map.<String, Object>of("count", 1))) {
            assertEquals(
                    "SCHEMA_MISMATCH",
                    assertThrows(HarnessException.class, () -> validator.validate(schema, bad))
                            .code());
        }
    }

    @Test
    void alternativesEnumsAndNullMustMatchExactly() {
        Map<String, Object> schema =
                Map.of(
                        "oneOf",
                        List.of(
                                Map.of("type", "string", "enum", List.of("a", "b")),
                                Map.of("type", "null")));
        validator.validate(schema, "a");
        validator.validate(schema, null);
        assertThrows(HarnessException.class, () -> validator.validate(schema, "other"));
        assertThrows(
                HarnessException.class,
                () ->
                        validator.validate(
                                Map.of(
                                        "oneOf",
                                        List.of(
                                                Map.of("type", "number"),
                                                Map.of("type", "integer"))),
                                1));
        validator.validate(Map.of("type", "boolean", "const", true), true);
        assertThrows(
                HarnessException.class,
                () -> validator.validate(Map.of("type", "boolean"), "true"));
    }

    @Test
    void unsupportedConstraintsAreRejectedInsteadOfIgnored() {
        for (Map<String, Object> schema :
                List.of(
                        Map.<String, Object>of("type", "string", "pattern", ".*"),
                        Map.<String, Object>of("type", "mystery"),
                        Map.<String, Object>of("type", "array", "items", Map.of("type", "unknown")),
                        Map.<String, Object>of(
                                "type",
                                "object",
                                "additionalProperties",
                                Map.of("type", "string")))) {
            assertEquals(
                    "INVALID_SCHEMA",
                    assertThrows(HarnessException.class, () -> validator.validate(schema, "x"))
                            .code());
        }
    }
}
