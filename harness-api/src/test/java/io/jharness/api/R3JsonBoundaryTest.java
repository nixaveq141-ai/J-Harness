package io.jharness.api;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3JsonBoundaryTest {
    @Test
    void jsonKeysAndDeepSnapshotsPreserveJsonDomain() {
        var list = new ArrayList<Object>(List.of("original"));
        var input = new LinkedHashMap<String, Object>();
        input.put("", list);
        input.put(" ", null);
        var result = Values.snapshot(input);
        list.set(0, "changed");
        assertEquals(List.of("original"), result.get(""));
        assertTrue(result.containsKey(" "));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) result.get("")).clear());
    }

    @Test
    void cyclicListsAndSharedDagAreBoundedBeforeRedaction() {
        var cycle = new ArrayList<Object>();
        cycle.add(cycle);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SecretRedactor().sanitize(Map.of("x", cycle)));
        Object dag = "leaf";
        for (int i = 0; i < 18; i++) dag = List.of(dag, dag);
        Object oversized = dag;
        assertThrows(IllegalArgumentException.class, () -> Values.snapshot(Map.of("x", oversized)));
    }

    @Test
    void exactDepthAndEventLevelsHaveSingleFactorNegatives() {
        Object nested = "leaf";
        for (int i = 0; i < 63; i++) nested = List.of(nested);
        Object accepted = nested;
        assertDoesNotThrow(() -> Values.snapshot(Map.of("x", accepted)));
        Object rejected = List.of(nested);
        assertThrows(IllegalArgumentException.class, () -> Values.snapshot(Map.of("x", rejected)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SessionEvent(
                                "e",
                                "s",
                                "tool.started",
                                java.time.Instant.EPOCH,
                                1,
                                EventLevel.STEP,
                                "t",
                                "p",
                                null,
                                Map.of()));
        assertDoesNotThrow(
                () ->
                        new SessionEvent(
                                "e",
                                "s",
                                "user.message",
                                java.time.Instant.EPOCH,
                                1,
                                EventLevel.TURN,
                                "t",
                                null,
                                null,
                                Map.of()));
        assertDoesNotThrow(() -> SessionEvent.session("s", "user.message", Map.of()));
    }

    @Test
    void configurationIntegersAreExactAndMissingModeCannotDiscardOptions() {
        var fraction = new BigDecimal("1.00000000000000000001");
        assertEquals(
                "INVALID_MODEL_RESILIENCE",
                assertThrows(
                                HarnessException.class,
                                () -> ModelResilienceOptions.from(Map.of("maxAttempts", fraction)))
                        .code());
        assertEquals(
                "INVALID_TOOL_SCHEDULING",
                assertThrows(
                                HarnessException.class,
                                () -> ToolSchedulingOptions.from(Map.of("timeoutSeconds", 1)))
                        .code());
        assertEquals(
                "INVALID_TOOL_SCHEDULING",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        ToolSchedulingOptions.from(
                                                Map.of(
                                                        "mode",
                                                        "readonly-parallel",
                                                        "maxConcurrency",
                                                        fraction)))
                        .code());
        var invalid = new HashMap<String, Object>();
        invalid.put(null, 1);
        assertEquals(
                "INVALID_MODEL_RESILIENCE",
                assertThrows(HarnessException.class, () -> ModelResilienceOptions.from(invalid))
                        .code());
    }
}
