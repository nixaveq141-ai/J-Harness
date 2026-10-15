package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class M6ConfigurationTest {
    @Test
    void profilesPresetsModelsAndBundlesResolveWithRecordedSelection() {
        var source =
                new CliConfig(
                        Map.of(
                                "model",
                                "base-model",
                                "profiles",
                                Map.of(
                                        "java",
                                        Map.of(
                                                "modelRoute",
                                                "fast",
                                                "preset",
                                                "safe",
                                                "bundles",
                                                List.of("files"),
                                                "settings",
                                                Map.of("maxSteps", 7))),
                                "presets",
                                Map.of("safe", Map.of("settings", Map.of("policy", "read-only"))),
                                "models",
                                Map.of(
                                        "fast",
                                        Map.of(
                                                "model",
                                                "fast-model",
                                                "baseUrl",
                                                "http://127.0.0.1:12345",
                                                "apiKeyEnv",
                                                "FAST_KEY",
                                                "streamSupported",
                                                true)),
                                "bundles",
                                Map.of(
                                        "files",
                                        Map.of(
                                                "enabledTools",
                                                List.of("read_file"),
                                                "prompts",
                                                List.of("Use recorded file facts.")))));
        var result =
                ConfigurationSelection.resolve(
                        source,
                        new Arguments(new String[] {"--profile", "java", "--bundle", "files"}));
        assertEquals("fast-model", result.string("model", ""));
        assertEquals(7, result.steps());
        assertEquals("read-only", result.string("policy", ""));
        assertEquals(List.of("read_file"), result.values.get("enabledTools"));
        assertEquals("java", result.selection.get("profile"));
        assertEquals(List.of("files"), result.selection.get("bundles"));
        assertTrue(result.selection.containsKey("resolvedSha256"));
        assertTrue(result.modelConfig("fixture-only").capabilities().streaming());
    }

    @Test
    void conflictingRoutesPoliciesUnknownSelectionsAndInlineKeysAreRejected() {
        var source =
                new CliConfig(
                        Map.of(
                                "models",
                                Map.of("a", Map.of("model", "A"), "b", Map.of("model", "B")),
                                "profiles",
                                Map.of("p", Map.of("modelRoute", "a", "preset", "x")),
                                "presets",
                                Map.of("x", Map.of("modelRoute", "b")),
                                "bundles",
                                Map.of(
                                        "readonly",
                                        Map.of("policy", "read-only"),
                                        "normal",
                                        Map.of("policy", "default"))));
        assertEquals(
                "CONFIG_CONFLICT",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ConfigurationSelection.resolve(
                                                source,
                                                new Arguments(new String[] {"--profile", "p"})))
                        .code());
        assertEquals(
                "A",
                ConfigurationSelection.resolve(
                                source,
                                new Arguments(
                                        new String[] {"--profile", "p", "--model-route", "a"}))
                        .string("model", ""));
        assertThrows(
                HarnessException.class,
                () ->
                        ConfigurationSelection.resolve(
                                source,
                                new Arguments(
                                        new String[] {
                                            "--bundle", "readonly", "--bundle", "normal"
                                        })));
        assertThrows(
                HarnessException.class,
                () ->
                        ConfigurationSelection.resolve(
                                source, new Arguments(new String[] {"--profile", "missing"})));
        assertThrows(
                HarnessException.class,
                () ->
                        ConfigurationSelection.resolve(
                                new CliConfig(
                                        Map.of(
                                                "models",
                                                Map.of(
                                                        "bad",
                                                        Map.of("model", "x", "apiKey", "inline")))),
                                new Arguments(new String[] {"--model-route", "bad"})));
    }

    @Test
    void capabilityFlagsAndCompactionLimitsAreStrictAndFallbackIsExplicit() {
        assertThrows(
                HarnessException.class,
                () ->
                        ConfigurationSelection.resolve(
                                new CliConfig(
                                        Map.of(
                                                "presets",
                                                        Map.of(
                                                                "safe",
                                                                Map.of(
                                                                        "settings",
                                                                        Map.of(
                                                                                "policy",
                                                                                "read-only"))),
                                                "bundles",
                                                        Map.of(
                                                                "normal",
                                                                Map.of("policy", "default")))),
                                new Arguments(
                                        new String[] {"--preset", "safe", "--bundle", "normal"})));
        assertThrows(
                HarnessException.class,
                () ->
                        ConfigurationSelection.resolve(
                                new CliConfig(
                                        Map.of(
                                                "profiles",
                                                        Map.of(
                                                                "safe",
                                                                Map.of(
                                                                        "modelRoute",
                                                                        "bad",
                                                                        "settings",
                                                                        Map.of(
                                                                                "policy",
                                                                                "read-only"))),
                                                "models",
                                                        Map.of(
                                                                "bad",
                                                                Map.of(
                                                                        "model", "x", "policy",
                                                                        "default")))),
                                new Arguments(new String[] {"--profile", "safe"})));

        assertThrows(HarnessException.class, () -> new CliConfig(Map.of("streaming", "yes")));
        assertThrows(
                HarnessException.class,
                () -> new CliConfig(Map.of("contextTokens", 1024, "compactionCharacters", 2048)));
        assertThrows(HarnessException.class, () -> new CliConfig(Map.of("policy", "allow-all")));
        var config =
                new CliConfig(
                        Map.of(
                                "model",
                                "text",
                                "baseUrl",
                                "http://127.0.0.1:12345",
                                "toolsSupported",
                                false,
                                "streaming",
                                true));
        assertFalse(config.modelConfig("key").capabilities().tools());
        assertFalse(config.modelConfig("key").capabilities().streaming());
        assertFalse(config.parameters().containsKey("stream"));
    }
}
