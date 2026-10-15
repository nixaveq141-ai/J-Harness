package io.jharness.session;

import io.jharness.api.*;
import java.nio.file.Path;
import java.util.Map;

/** Child JVM used only for cross-process ownership tests. */
public final class WriterProbe {
    private WriterProbe() {}

    public static void main(String[] args) throws Exception {
        JsonlSessionRepository repo = new JsonlSessionRepository(Path.of(args[0]));
        try (SessionWriter writer = repo.openWriter(args[1], WriteOwnership.EXCLUSIVE)) {
            writer.append(SessionEvent.session(args[1], "probe.message", Map.of()));
            System.out.println("APPENDED");
        } catch (HarnessException ex) {
            System.out.println(ex.code());
            System.exit(23);
        }
    }
}
