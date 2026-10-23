package io.jharness.tools;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3SchemaBoundaryTest {
    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void declarationErrorsAreAlwaysSchemaErrors() {
        for (var schema :
                List.of(
                        Map.<String, Object>of("type", "object", "properties", List.of()),
                        Map.<String, Object>of("type", "array", "items", "bad"),
                        Map.<String, Object>of("const", Double.NaN))) {
            assertEquals(
                    "INVALID_SCHEMA",
                    assertThrows(HarnessException.class, () -> validator.checkSchema(schema))
                            .code());
        }
        assertEquals(
                "INVALID_SCHEMA",
                assertThrows(HarnessException.class, () -> validator.checkSchema(null)).code());
    }

    @Test
    void nonJsonInstancesAndCyclesAreMismatchesEvenWithConst() {
        var cycle = new ArrayList<Object>();
        cycle.add(cycle);
        for (Object value : List.of(Double.NaN, new Object(), cycle, Map.of(1, "bad")))
            assertEquals(
                    "SCHEMA_MISMATCH",
                    assertThrows(
                                    HarnessException.class,
                                    () -> validator.validate(Map.of("const", "valid"), value))
                            .code());
        validator.validate(Map.of("type", "number"), 1);
    }
}
