package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class M8SchedulingConfigTest {
    @Test
    void invalidModesBoundsAndDisabledPluginAreRejectedBeforeAssembly() {
        for (Object bad :
                List.of(
                        Map.of("mode", "automatic"),
                        Map.of("mode", "serial", "tools", List.of("read_file")),
                        Map.of("mode", "readonly-parallel", "maxConcurrency", 9),
                        Map.of("mode", "readonly-parallel", "timeoutSeconds", 61),
                        Map.of("mode", "readonly-parallel", "maxConcurrency", 1.5),
                        Map.of("mode", "readonly-parallel", "tools", List.of(1)),
                        Map.of("unknown", true)))
            assertEquals(
                    "INVALID_TOOL_SCHEDULING",
                    assertThrows(
                                    HarnessException.class,
                                    () -> new CliConfig(Map.of("toolScheduling", bad)))
                            .code());
        var enabled = new ArrayList<>(CliConfig.PLUGINS);
        enabled.remove("tool-scheduling");
        assertEquals(
                "INVALID_TOOL_SCHEDULING",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new CliConfig(
                                                Map.of(
                                                        "enabledPlugins",
                                                        enabled,
                                                        "toolScheduling",
                                                        Map.of("mode", "readonly-parallel"))))
                        .code());
    }

    @Test
    void namedProfileCanEnableBoundedSchedulingAndDefaultRemainsSerial() {
        assertEquals(1, new CliConfig(Map.of()).scheduling().maxConcurrency());
        var config =
                new CliConfig(
                        Map.of(
                                "profiles",
                                Map.of(
                                        "inspect",
                                        Map.of(
                                                "settings",
                                                Map.of(
                                                        "toolScheduling",
                                                        Map.of(
                                                                "mode",
                                                                "readonly-parallel",
                                                                "maxConcurrency",
                                                                3,
                                                                "tools",
                                                                List.of("read_file")))))));
        var selected =
                ConfigurationSelection.resolve(
                        config, new Arguments(new String[] {"--profile", "inspect"}));
        assertEquals(3, selected.scheduling().maxConcurrency());
        assertEquals(Set.of("read_file"), selected.scheduling().tools());
        assertEquals(1, config.scheduling().maxConcurrency());
    }
}
