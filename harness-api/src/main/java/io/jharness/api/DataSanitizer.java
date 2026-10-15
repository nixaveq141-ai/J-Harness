package io.jharness.api;

import java.util.Map;

@FunctionalInterface
public interface DataSanitizer {
    Map<String, Object> sanitize(Map<String, Object> data);
}
