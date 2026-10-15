package io.jharness.process;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRunControlTest {
    @TempDir Path root;

    @Test
    void separateJvmCancelAuthenticatesAndExitStateIsDiscoverable() throws Exception {
        String id;
        try (var run = new LocalRunControl(root.resolve("runs"))) {
            id = run.runId();
            assertEquals(List.of(id), LocalRunControl.discover(root.resolve("runs")));
            assertFalse(run.cancellation().isCancelled());
            Process client =
                    new ProcessBuilder(
                                    ProcessProbe.java(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    CancelMain.class.getName(),
                                    "--state-dir",
                                    root.resolve("runs").toString(),
                                    "--run",
                                    id)
                            .start();
            try {
                assertTrue(client.waitFor(5, TimeUnit.SECONDS));
                assertEquals(0, client.exitValue());
                assertEquals(
                        "REQUESTED", new String(client.getInputStream().readAllBytes()).trim());
                assertTrue(run.cancellation().isCancelled());
                assertEquals(
                        LocalRunControl.CancelResult.REQUESTED,
                        LocalRunControl.cancel(root.resolve("runs"), id));
            } finally {
                client.destroyForcibly();
            }
        }
        assertEquals(
                LocalRunControl.CancelResult.ALREADY_EXITED,
                LocalRunControl.cancel(root.resolve("runs"), id));
        String state = Files.readString(root.resolve("runs").resolve(id + ".run"));
        assertTrue(state.contains("status=EXITED"));
        assertTrue(state.contains("token=\n"));
    }

    @Test
    void invalidCredentialDoesNotCancelAndUnreachableIsDifferentFromExited() throws Exception {
        String original, id;
        Path state;
        try (var run = new LocalRunControl(root.resolve("runs"))) {
            id = run.runId();
            state = run.stateFile();
            original = Files.readString(state);
            Properties properties = new Properties();
            properties.load(new StringReader(original));
            try (Socket socket =
                    new Socket("127.0.0.1", Integer.parseInt(properties.getProperty("port")))) {
                socket.setSoTimeout(1000);
                new DataOutputStream(socket.getOutputStream()).writeUTF("CANCEL wrong");
                assertEquals("DENIED", new DataInputStream(socket.getInputStream()).readUTF());
                assertFalse(run.cancellation().isCancelled());
            }
        }
        Files.writeString(state, original);
        assertEquals(
                LocalRunControl.CancelResult.UNREACHABLE,
                LocalRunControl.cancel(root.resolve("runs"), id));
        assertEquals(
                LocalRunControl.CancelResult.NOT_FOUND,
                LocalRunControl.cancel(root.resolve("runs"), UUID.randomUUID().toString()));
        assertEquals(
                LocalRunControl.CancelResult.INVALID_STATE,
                LocalRunControl.cancel(root.resolve("runs"), "../escape"));
        Files.writeString(state, original.replace("version=1", "version=2"));
        assertEquals(
                LocalRunControl.CancelResult.INVALID_STATE,
                LocalRunControl.cancel(root.resolve("runs"), id));
    }
}
