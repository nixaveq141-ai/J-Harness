package io.jharness.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class R4ReportBudgetTest {
    @Test
    void completeSecretsAreRedactedBeforeAnyReportTruncation() {
        String secret = "private-boundary-" + "z".repeat(100);
        String output = "x".repeat(2040) + secret + " suffix".repeat(1000);
        var report =
                EvidenceReport.build(
                        "x".repeat(4090) + secret,
                        List.of(
                                CliContractsTest.event(
                                        "tool.completed",
                                        EventLevel.TOOL,
                                        "step",
                                        "call",
                                        Map.of(
                                                "tool",
                                                "execute_command",
                                                "status",
                                                "SUCCESS",
                                                "value",
                                                Map.of(
                                                        "argv",
                                                        List.of("fixture"),
                                                        "stdout",
                                                        output)))),
                        new SecretRedactor(List.of(secret)));
        String json = Json.text(report);
        assertFalse(json.contains("private-"));
        assertTrue(json.contains("[REDACTED]"));
        assertTrue(json.contains("REPORT_TRUNCATED"));
    }

    @Test
    void largeFactsHaveIndependentBudgetCoverageAndRetainedSourceIdentity() {
        var events = new ArrayList<SessionEvent>();
        for (int i = 0; i < 300; i++)
            events.add(
                    CliContractsTest.event(
                            "tool.preview",
                            EventLevel.TOOL,
                            "step",
                            "call" + i,
                            Map.of(
                                    "diff",
                                    "中😀".repeat(10000),
                                    "nested",
                                    List.of(Map.of("body", "x".repeat(20000))))));
        var report = EvidenceReport.build("goal", events, new SecretRedactor());
        var facts = Values.object(report.get("recordedFacts"));
        var previews = (List<?>) facts.get("previews");
        assertEquals(128, previews.size());
        assertEquals(
                events.getFirst().eventId(), Values.object(previews.getFirst()).get("eventId"));
        assertEquals(events.get(127).eventId(), Values.object(previews.getLast()).get("eventId"));
        var coverage = Values.object(report.get("reportCoverage"));
        assertEquals(false, coverage.get("complete"));
        assertEquals(
                172,
                Values.object(Values.object(coverage.get("sections")).get("previews"))
                        .get("omitted"));
        assertTrue(Json.text(report).getBytes(StandardCharsets.UTF_8).length < 1048576);
        assertTrue(EvidenceReport.markdown(report).contains("报告覆盖范围"));
        assertEquals("INCOMPLETE", report.get("status"));
    }

    @Test
    void malformedContainersAndCostsDoNotCreateFalseSuccessOrZeroCost() {
        for (Object invalid : Arrays.asList(null, "broken", List.of())) {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("value", invalid);
            payload.put("display", invalid);
            payload.put("error", null);
            payload.put("status", null);
            for (String cost : List.of("NaN", "-1", "x".repeat(200))) {
                var report =
                        EvidenceReport.build(
                                "goal",
                                List.of(
                                        CliContractsTest.event(
                                                "tool.completed",
                                                EventLevel.TOOL,
                                                "step",
                                                "call",
                                                payload),
                                        CliContractsTest.event(
                                                "turn.ended",
                                                EventLevel.TURN,
                                                null,
                                                null,
                                                Map.of("reservedApiCost", cost))),
                                new SecretRedactor());
                assertEquals("UNKNOWN", Values.object(report.get("cost")).get("upperBound"));
                assertEquals(List.of(), Values.object(report.get("recordedFacts")).get("changes"));
            }
        }
    }

    @Test
    void previewProjectionHasNodeCharacterAndSurrogateBoundaries() {
        var budget = new ReportProjection(128, 16, 65);
        var input = new LinkedHashMap<String, Object>();
        input.put("diff", "x".repeat(64) + "😀".repeat(100));
        input.put("many", Collections.nCopies(200, "nested"));
        var projected = Values.object(budget.project(input));
        assertTrue(budget.truncated());
        assertTrue(Json.text(projected).length() < 1024);
        String diff = (String) projected.get("diff");
        assertFalse(diff.contains("\ud83d[REPORT_TRUNCATED]"));
    }
}
