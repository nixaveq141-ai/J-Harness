package io.jharness.tools;

import io.jharness.api.*;
import java.math.BigDecimal;
import java.util.*;

/** Explicit bounded subset. Unsupported schema constraints fail closed. */
public final class JsonSchemaValidator {
    private static final Set<String> KEYS =
            Set.of(
                    "type",
                    "properties",
                    "required",
                    "additionalProperties",
                    "items",
                    "enum",
                    "const",
                    "minimum",
                    "maximum",
                    "minLength",
                    "maxLength",
                    "minItems",
                    "maxItems",
                    "description",
                    "title",
                    "oneOf");
    private static final Set<String> TYPES =
            Set.of("object", "array", "string", "number", "integer", "boolean", "null");

    public void validate(Map<String, Object> schema, Object value) {
        checkSchema(schema, 0);
        checkValue(schema, value, 0);
    }

    public void checkSchema(Map<String, Object> schema) {
        checkSchema(schema, 0);
    }

    private void checkSchema(Map<String, Object> schema, int depth) {
        if (depth > 32 || !KEYS.containsAll(schema.keySet()))
            invalid("Unsupported schema keyword or depth");
        Object type = schema.get("type");
        if (type != null && (!(type instanceof String text) || !TYPES.contains(text)))
            invalid("Invalid type");
        if (type == null
                && !schema.containsKey("oneOf")
                && !schema.containsKey("enum")
                && !schema.containsKey("const"))
            invalid("Schema must declare a type or alternative");
        if (schema.containsKey("properties")) {
            if (!"object".equals(type)) invalid("properties requires object");
            Values.object(schema.get("properties"))
                    .values()
                    .forEach(child -> checkSchema(Values.object(child), depth + 1));
        }
        if (schema.containsKey("items")) {
            if (!"array".equals(type)) invalid("items requires array");
            checkSchema(Values.object(schema.get("items")), depth + 1);
        }
        if (schema.containsKey("oneOf")) {
            if (!(schema.get("oneOf") instanceof List<?> options) || options.isEmpty())
                invalid("Invalid oneOf");
            for (Object option : (List<?>) schema.get("oneOf"))
                checkSchema(Values.object(option), depth + 1);
        }
        if (schema.containsKey("required")
                && (!(schema.get("required") instanceof List<?> required)
                        || required.stream().anyMatch(x -> !(x instanceof String))))
            invalid("Invalid required");
        if ((schema.containsKey("required") || schema.containsKey("additionalProperties"))
                && !"object".equals(type)) invalid("Object constraint on another type");
        if (schema.containsKey("additionalProperties")
                && !(schema.get("additionalProperties") instanceof Boolean))
            invalid("Only boolean additionalProperties is supported");
        if (schema.containsKey("enum")
                && (!(schema.get("enum") instanceof List<?> list) || list.isEmpty()))
            invalid("Invalid enum");
        for (String key :
                List.of("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems")) {
            if (!schema.containsKey(key)) continue;
            if (!(schema.get(key) instanceof Number number)) invalid("Invalid numeric constraint");
            if ((key.equals("minimum") || key.equals("maximum"))
                    && !"number".equals(type)
                    && !"integer".equals(type)) invalid("Numeric constraint on another type");
            if ((key.equals("minLength") || key.equals("maxLength")) && !"string".equals(type))
                invalid("String constraint on another type");
            if ((key.equals("minItems") || key.equals("maxItems")) && !"array".equals(type))
                invalid("Array constraint on another type");
            BigDecimal limit = decimal((Number) schema.get(key));
            if (!key.equals("minimum")
                    && !key.equals("maximum")
                    && (limit.signum() < 0 || limit.stripTrailingZeros().scale() > 0))
                invalid("Length bounds must be nonnegative integers");
        }
        for (List<String> pair :
                List.of(
                        List.of("minimum", "maximum"),
                        List.of("minLength", "maxLength"),
                        List.of("minItems", "maxItems"))) {
            if (schema.containsKey(pair.getFirst())
                    && schema.containsKey(pair.getLast())
                    && decimal((Number) schema.get(pair.getFirst()))
                                    .compareTo(decimal((Number) schema.get(pair.getLast())))
                            > 0) invalid("Conflicting bounds");
        }
    }

    private void checkValue(Map<String, Object> schema, Object value, int depth) {
        if (depth > 64) mismatch();
        if (schema.containsKey("oneOf")) {
            int matches = 0;
            for (Object candidate : (List<?>) schema.get("oneOf")) {
                try {
                    checkValue(Values.object(candidate), value, depth + 1);
                    matches++;
                } catch (HarnessException ex) {
                    if (!ex.code().equals("SCHEMA_MISMATCH")) throw ex;
                }
            }
            if (matches != 1) mismatch();
        }
        if (schema.containsKey("const") && !sameJson(schema.get("const"), value)) mismatch();
        if (schema.containsKey("enum")
                && ((List<?>) schema.get("enum"))
                        .stream().noneMatch(option -> sameJson(option, value))) mismatch();
        Object type = schema.get("type");
        if (type == null) return;
        switch ((String) type) {
            case "object" -> {
                if (!(value instanceof Map<?, ?>)) mismatch();
                Map<String, Object> object = Values.object(value);
                Map<String, Object> properties =
                        schema.containsKey("properties")
                                ? Values.object(schema.get("properties"))
                                : Map.of();
                if (schema.get("required") instanceof List<?> required)
                    for (Object key : required) if (!object.containsKey(key)) mismatch();
                for (Map.Entry<String, Object> entry : object.entrySet()) {
                    if (properties.containsKey(entry.getKey()))
                        checkValue(
                                Values.object(properties.get(entry.getKey())),
                                entry.getValue(),
                                depth + 1);
                    else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) mismatch();
                }
            }
            case "array" -> {
                if (!(value instanceof List<?>)) mismatch();
                List<?> list = (List<?>) value;
                bounds(schema, list.size(), "minItems", "maxItems");
                if (schema.containsKey("items"))
                    for (Object item : list)
                        checkValue(Values.object(schema.get("items")), item, depth + 1);
            }
            case "string" -> {
                if (!(value instanceof String)) mismatch();
                String text = (String) value;
                bounds(schema, text.codePointCount(0, text.length()), "minLength", "maxLength");
            }
            case "number", "integer" -> {
                if (!(value instanceof Number)) mismatch();
                BigDecimal number = decimal((Number) value);
                if ("integer".equals(type) && number.stripTrailingZeros().scale() > 0) mismatch();
                if (schema.containsKey("minimum")
                        && number.compareTo(decimal((Number) schema.get("minimum"))) < 0)
                    mismatch();
                if (schema.containsKey("maximum")
                        && number.compareTo(decimal((Number) schema.get("maximum"))) > 0)
                    mismatch();
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) mismatch();
            }
            case "null" -> {
                if (value != null) mismatch();
            }
            default -> invalid("Invalid schema type");
        }
    }

    private static void bounds(Map<String, Object> schema, int size, String min, String max) {
        BigDecimal n = BigDecimal.valueOf(size);
        if (schema.containsKey(min) && n.compareTo(decimal((Number) schema.get(min))) < 0)
            mismatch();
        if (schema.containsKey(max) && n.compareTo(decimal((Number) schema.get(max))) > 0)
            mismatch();
    }

    private static BigDecimal decimal(Number n) {
        try {
            return new BigDecimal(n.toString());
        } catch (NumberFormatException ex) {
            invalid("Nonfinite number");
            return BigDecimal.ZERO;
        }
    }

    private static boolean sameJson(Object a, Object b) {
        if (a instanceof Number first && b instanceof Number second)
            return decimal(first).compareTo(decimal(second)) == 0;
        if (a instanceof List<?> first && b instanceof List<?> second) {
            if (first.size() != second.size()) return false;
            for (int index = 0; index < first.size(); index++)
                if (!sameJson(first.get(index), second.get(index))) return false;
            return true;
        }
        if (a instanceof Map<?, ?> first && b instanceof Map<?, ?> second) {
            if (!first.keySet().equals(second.keySet())) return false;
            for (Object key : first.keySet())
                if (!sameJson(first.get(key), second.get(key))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    private static void invalid(String message) {
        throw new HarnessException("INVALID_SCHEMA", message);
    }

    private static void mismatch() {
        throw new HarnessException("SCHEMA_MISMATCH", "Value does not satisfy its declared schema");
    }
}
