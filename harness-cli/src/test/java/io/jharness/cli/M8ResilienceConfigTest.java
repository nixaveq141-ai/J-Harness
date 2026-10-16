package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class M8ResilienceConfigTest {
    @Test
    void rejectsUnknownFieldsTypesAndBoundsBeforeAssembly() {
        for (Object bad :
                List.of(
                        Map.of("enabled", "yes"),
                        Map.of("maxAttempts", 5),
                        Map.of("maxAttempts", 0),
                        Map.of("backoffMillis", -1),
                        Map.of("totalTimeoutMillis", 49),
                        Map.of("minIntervalMillis", 60001),
                        Map.of("maxWaiters", 33),
                        Map.of("maxAttempts", 1.5),
                        Map.of("foo", true),
                        List.of()))
            assertEquals(
                    "INVALID_MODEL_RESILIENCE",
                    assertThrows(
                                    HarnessException.class,
                                    () -> new CliConfig(Map.of("modelResilience", bad)))
                            .code());
        assertThrows(
                HarnessException.class,
                () ->
                        new CliConfig(
                                Map.of(
                                        "modelResilience",
                                        Map.of("enabled", true),
                                        "enabledPlugins",
                                        List.of("registry"))));
    }

    @Test
    void profileReplacesWholeObjectAndDefaultIsDisabled() {
        var config =
                new CliConfig(
                        Map.of(
                                "modelResilience",
                                Map.of("enabled", true, "maxAttempts", 4),
                                "profiles",
                                Map.of(
                                        "safe",
                                        Map.of(
                                                "settings",
                                                Map.of(
                                                        "modelResilience",
                                                        Map.of("enabled", false))))));
        var selected =
                ConfigurationSelection.resolve(
                        config, new Arguments(new String[] {"--profile", "safe"}));
        assertFalse(selected.resilience().enabled());
        assertEquals(1, selected.resilience().maxAttempts());
        assertFalse(new CliConfig(Map.of()).resilience().enabled());
    }
}
