package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.util.*;
import org.junit.jupiter.api.Test;

class M8FilterConfigTest {
    @Test
    void namedSettingsReplaceFilterAsOneValidatedObject() {
        var source =
                new CliConfig(
                        Map.of(
                                "searchFilter",
                                Map.of("rules", List.of("base/")),
                                "presets",
                                Map.of(
                                        "p",
                                        Map.of(
                                                "settings",
                                                Map.of(
                                                        "searchFilter",
                                                        Map.of(
                                                                "useGitignore",
                                                                false,
                                                                "rules",
                                                                List.of("preset/"))))),
                                "profiles",
                                Map.of(
                                        "inspect",
                                        Map.of(
                                                "preset",
                                                "p",
                                                "settings",
                                                Map.of(
                                                        "searchFilter",
                                                        Map.of("rules", List.of("profile/")))))));
        var result =
                ConfigurationSelection.resolve(
                        source, new Arguments(new String[] {"--profile", "inspect"}));
        assertEquals(
                new WorkspaceSearchFilter(true, true, List.of("profile/")), result.searchFilter());
        assertEquals(List.of("base/"), source.searchFilter().rules());
        assertTrue(result.selection.containsKey("resolvedSha256"));
    }

    @Test
    void badFilterIsRejectedBeforeModelOrEnvironmentAssembly() {
        assertEquals(
                "INVALID_SEARCH_FILTER",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new CliConfig(
                                                Map.of(
                                                        "searchFilter",
                                                        Map.of("rules", List.of("../secret")))))
                        .code());
        assertEquals(
                "INVALID_SEARCH_FILTER",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        new CliConfig(
                                                Map.of("searchFilter", Map.of("unknown", true))))
                        .code());
        assertEquals(WorkspaceSearchFilter.defaults(), new CliConfig(Map.of()).searchFilter());
    }
}
