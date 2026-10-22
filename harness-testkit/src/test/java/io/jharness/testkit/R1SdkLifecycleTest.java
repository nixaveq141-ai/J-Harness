package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R1SdkLifecycleTest {
    @TempDir Path root;

    @Test
    void successfulEnvironmentCloseIsNotReclassifiedWhenItsJournalAppendFails() throws Exception {
        var repository = new JsonlSessionRepository(root.resolve("sessions"));
        var writerCloses = new AtomicInteger();
        var environmentCloses = new AtomicInteger();
        var modelCloses = new AtomicInteger();
        var failure = new HarnessException("CHECKPOINT_FAILED", "environment.closed fault");
        SessionRepository faulty =
                new SessionRepository() {
                    public SessionWriter openWriter(String session, WriteOwnership ownership) {
                        var delegate = repository.openWriter(session, ownership);
                        return new SessionWriter() {
                            public boolean durable() {
                                return delegate.durable();
                            }

                            public String append(SessionEvent event) {
                                if (event.type().equals("environment.closed")) {
                                    assertEquals("SUCCESS", event.payload().get("status"));
                                    throw failure;
                                }
                                return delegate.append(event);
                            }

                            public void flush() {
                                delegate.flush();
                            }

                            public void close() {
                                writerCloses.incrementAndGet();
                                delegate.close();
                            }
                        };
                    }

                    public SessionReader openReader(String session) {
                        return repository.openReader(session);
                    }

                    public List<String> list() {
                        return repository.list();
                    }

                    public String fork(String session, String event) {
                        return repository.fork(session, event);
                    }
                };
        var local =
                new LocalExecutionEnvironment(
                        "local",
                        java.nio.file.Files.createDirectory(root.resolve("workspace")),
                        List.of());
        ExecutionEnvironment environment =
                new ExecutionEnvironment() {
                    public String id() {
                        return local.id();
                    }

                    public WorkspaceFiles files() {
                        return local.files();
                    }

                    public ProcessRunner processes() {
                        return local.processes();
                    }

                    public TerminalSession terminal() {
                        return local.terminal();
                    }

                    public Map<String, Object> descriptor() {
                        return local.descriptor();
                    }

                    public void close() {
                        environmentCloses.incrementAndGet();
                        local.close();
                    }
                };
        var fake = new FakeLlm(List.of());
        LlmClient model =
                new LlmClient() {
                    public ModelCapabilities capabilities() {
                        return fake.capabilities();
                    }

                    public PreparedRequest prepare(ChatRequest request) {
                        return fake.prepare(request);
                    }

                    public ChatResponse complete(ChatRequest request, CancellationToken token) {
                        throw new AssertionError("no model execution");
                    }

                    public StreamHandle stream(ChatRequest request, CancellationToken token) {
                        throw new AssertionError("no stream");
                    }

                    public void close() {
                        modelCloses.incrementAndGet();
                    }
                };
        var sdk =
                new HarnessSdk(
                        "s",
                        root.toString(),
                        faulty,
                        environment,
                        model,
                        new LocalArtifactStore(root.resolve("artifacts"), new SecretRedactor()),
                        new SecretRedactor(),
                        new DefaultPolicyEngine(),
                        M7EcosystemFixture.approve(),
                        new Budget(2, Duration.ofSeconds(5)),
                        (response, projection) ->
                                new CompletionEvaluator.CompletionDecision(false, "no execution"),
                        ApiCostGuard.unavailable(),
                        List.of());
        try {
            assertSame(failure, assertThrows(HarnessException.class, sdk::close));
            assertEquals(1, environmentCloses.get());
            assertEquals(1, modelCloses.get());
            assertEquals(1, writerCloses.get());
            assertTrue(
                    repository.openReader("s").events().stream()
                            .noneMatch(event -> event.type().equals("environment.closed")));
            assertEquals(
                    "ENVIRONMENT_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> local.files().readText(new ExecPath("after-close")))
                            .code());
            assertEquals(
                    "SDK_CLOSED",
                    assertThrows(
                                    HarnessException.class,
                                    () -> sdk.run("must not reopen", CancellationToken.NONE))
                            .code());
            sdk.close();
            assertEquals(1, environmentCloses.get());
            assertEquals(1, writerCloses.get());
        } finally {
            sdk.close();
        }
    }
}
