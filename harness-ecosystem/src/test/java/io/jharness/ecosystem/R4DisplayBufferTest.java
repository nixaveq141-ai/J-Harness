package io.jharness.ecosystem;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class R4DisplayBufferTest {
    private SessionReader reader() {
        return () ->
                List.of(
                        new SessionEvent(
                                "request",
                                "session",
                                "model.requested",
                                Instant.now(),
                                1,
                                EventLevel.STEP,
                                "turn",
                                "step",
                                null,
                                Map.of()));
    }

    @Test
    void rapidFinalSnapshotIsVisibleWithoutASecondFrame() {
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"), reader(), new SecretRedactor());
        display.accept(new ModelDisplay.Update("first", false));
        display.accept(new ModelDisplay.Update("last snapshot", false));
        assertEquals("last snapshot", display.snapshot().get("text"));
    }

    @Test
    void sanitizerAndReaderFailureClearPresentationWithoutThrowing() {
        var display =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"),
                        reader(),
                        new DataSanitizer() {
                            public Map<String, Object> sanitize(Map<String, Object> value) {
                                throw new IllegalStateException();
                            }

                            public String streamingText(String text) {
                                throw new IllegalStateException();
                            }
                        });
        assertDoesNotThrow(() -> display.accept(new ModelDisplay.Update("private content", false)));
        assertEquals("UNAVAILABLE", display.snapshot().get("state"));
        assertEquals("", display.snapshot().get("text"));
        var failedReader =
                new ModelDisplayBuffer(
                        new PluginScope("session", "agent"),
                        () -> {
                            throw new IllegalStateException();
                        },
                        new SecretRedactor());
        assertThrows(IllegalStateException.class, failedReader::snapshot);
    }
}
