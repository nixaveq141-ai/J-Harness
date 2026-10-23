package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.policy.DefaultPolicyEngine;
import io.jharness.session.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2SdkPersistenceTest {
    @TempDir Path root;

    private SessionRepository faulting(
            JsonlSessionRepository repo, java.util.function.Predicate<SessionEvent> reject) {
        return new SessionRepository() {
            public SessionReader openReader(String id) {
                return repo.openReader(id);
            }

            public List<String> list() {
                return repo.list();
            }

            public String fork(String id, String event) {
                return repo.fork(id, event);
            }

            public SessionWriter openWriter(String id, WriteOwnership ownership) {
                var writer = repo.openWriter(id, ownership);
                return new SessionWriter() {
                    public boolean durable() {
                        return writer.durable();
                    }

                    public String append(SessionEvent event) {
                        if (reject.test(event))
                            throw new HarnessException(
                                    "CHECKPOINT_FAILED", "injected append failure");
                        return writer.append(event);
                    }

                    public void flush() {
                        writer.flush();
                    }

                    public void close() {
                        writer.close();
                    }
                };
            }
        };
    }

    @Test
    void halfInitializationIsDiagnosedAndOnlySameFingerprintCanResumeBeforePlugins()
            throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var descriptor = Map.<String, Object>of("kind", "fixture");
        assertEquals(
                "CHECKPOINT_FAILED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        open(
                                                faulting(
                                                        repo,
                                                        event ->
                                                                event.type().equals("sdk.created")),
                                                descriptor,
                                                new SecretRedactor()))
                        .code());
        assertEquals(1, repo.openReader("s").events().size());
        assertEquals(
                List.of("sdk-initialization"),
                SessionRecovery.inspect(repo.openReader("s").events()).uncertainCalls());
        assertEquals(
                "SDK_SESSION_BINDING_REQUIRED",
                assertThrows(
                                HarnessException.class,
                                () -> open(repo, Map.of("kind", "other"), new SecretRedactor()))
                        .code());
        try (var sdk = open(repo, descriptor, new SecretRedactor())) {
            assertEquals("s", sdk.snapshot().sessionId());
        }
        assertEquals(
                1,
                repo.openReader("s").events().stream()
                        .filter(e -> e.type().equals("session.created"))
                        .count());
        assertTrue(SessionRecovery.inspect(repo.openReader("s").events()).ready());
    }

    @Test
    void inputCheckpointFailureCannotLeaveDanglingUserAssociationOrExecuteModel() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var input = Map.of("requestHash", "a".repeat(64), "kind", "message");
        try (var sdk =
                open(
                        faulting(repo, event -> event.type().equals("sdk.input.applied")),
                        Map.of("kind", "fixture"),
                        new SecretRedactor())) {
            assertEquals(
                    "CHECKPOINT_FAILED",
                    assertThrows(
                                    HarnessException.class,
                                    () ->
                                            sdk.run(
                                                    "submitted message",
                                                    CancellationToken.NONE,
                                                    input))
                            .code());
            var events = repo.openReader("s").events();
            assertTrue(
                    events.stream()
                            .noneMatch(
                                    e ->
                                            Set.of(
                                                            "user.message",
                                                            "sdk.input.applied",
                                                            "model.requested",
                                                            "turn.started")
                                                    .contains(e.type())));
        }
    }

    @Test
    void futureSdkProtocolRefusesReopenWithoutChangingFacts() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        try (var sdk = open(repo, Map.of("kind", "fixture"), new SecretRedactor())) {
            sdk.snapshot();
        }
        Path file = root.resolve("sessions/s.jsonl");
        Files.writeString(
                file,
                Files.readString(file)
                        .replace("\"protocolVersion\":\"1.14\"", "\"protocolVersion\":\"9.0\""));
        byte[] before = Files.readAllBytes(file);
        assertEquals(
                "UNSUPPORTED_SDK_PROTOCOL",
                assertThrows(
                                HarnessException.class,
                                () -> open(repo, Map.of("kind", "fixture"), new SecretRedactor()))
                        .code());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    private HarnessSdk open(
            SessionRepository repo, Map<String, Object> descriptor, DataSanitizer sanitizer)
            throws Exception {
        var local =
                new LocalExecutionEnvironment(
                        "local", Files.createDirectories(root.resolve("workspace")), List.of());
        ExecutionEnvironment environment =
                (ExecutionEnvironment)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ExecutionEnvironment.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("descriptor")) return descriptor;
                                    try {
                                        return method.invoke(local, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getCause();
                                    }
                                });
        try {
            return new HarnessSdk(
                    "s",
                    "binding",
                    repo,
                    environment,
                    new FakeLlm(List.of()),
                    new LocalArtifactStore(root.resolve("artifacts"), sanitizer),
                    sanitizer,
                    new DefaultPolicyEngine(),
                    M7EcosystemFixture.approve(),
                    new Budget(2, Duration.ofSeconds(5)),
                    (response, projection) ->
                            new CompletionEvaluator.CompletionDecision(false, "fixture"),
                    ApiCostGuard.unavailable(),
                    List.of());
        } catch (RuntimeException ex) {
            local.close();
            throw ex;
        }
    }

    @Test
    void invalidGoalAndSubmissionLeaveExactJournalBytesUntouched() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        try (var sdk = open(repo, Map.of("id", "one", "kind", "fixture"), new SecretRedactor())) {
            byte[] before = Files.readAllBytes(root.resolve("sessions/s.jsonl"));
            assertEquals(
                    "INVALID_GOAL",
                    assertThrows(HarnessException.class, () -> sdk.run(" ", CancellationToken.NONE))
                            .code());
            var invalid = new HashMap<String, String>();
            invalid.put("requestHash", null);
            invalid.put("kind", "message");
            assertEquals(
                    "INVALID_SUBMISSION",
                    assertThrows(
                                    HarnessException.class,
                                    () -> sdk.run("valid", CancellationToken.NONE, invalid))
                            .code());
            assertArrayEquals(before, Files.readAllBytes(root.resolve("sessions/s.jsonl")));
        }
    }

    @Test
    void recursiveRandomIdsAndNewSecretConfigurationDoNotChangeStableBinding() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        var first =
                Map.<String, Object>of(
                        "id",
                        "child-one",
                        "kind",
                        "read-only-child",
                        "parent",
                        Map.of(
                                "id",
                                "docker-random-one",
                                "image",
                                "synthetic-image-secret",
                                "workspace",
                                "/work"));
        var sanitizer = new SecretRedactor(List.of("synthetic-image-secret"));
        try (var sdk = open(repo, first, sanitizer)) {
            assertEquals("s", sdk.snapshot().sessionId());
        }
        assertFalse(
                Files.readString(root.resolve("sessions/s.jsonl"))
                        .contains("synthetic-image-secret"));
        var second =
                Map.<String, Object>of(
                        "parent",
                        Map.of(
                                "workspace",
                                "/work",
                                "image",
                                "synthetic-image-secret",
                                "id",
                                "docker-random-two"),
                        "kind",
                        "read-only-child",
                        "id",
                        "child-two");
        try (var sdk =
                open(
                        repo,
                        second,
                        new SecretRedactor(List.of("synthetic-image-secret", "new-secret")))) {
            assertEquals("s", sdk.snapshot().sessionId());
        }
        var different =
                Map.<String, Object>of(
                        "id",
                        "child-three",
                        "kind",
                        "read-only-child",
                        "parent",
                        Map.of("id", "random", "image", "different-image", "workspace", "/work"));
        assertEquals(
                "ENVIRONMENT_MISMATCH",
                assertThrows(HarnessException.class, () -> open(repo, different, sanitizer))
                        .code());
    }

    @Test
    void environmentVerificationCannotCreateMissingOrAlterArchivedSession() throws Exception {
        var repo = new JsonlSessionRepository(root.resolve("sessions"));
        assertEquals(
                "SESSION_NOT_FOUND",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        HarnessSdk.verifyEnvironment(
                                                repo,
                                                "missing",
                                                "env",
                                                "inspected",
                                                new SecretRedactor()))
                        .code());
        assertFalse(Files.exists(root.resolve("sessions/missing.jsonl")));
        try (var sdk = open(repo, Map.of("kind", "fixture"), new SecretRedactor())) {
            sdk.snapshot();
        }
        SessionLifecycle.setArchived(repo, "s", true, new SecretRedactor());
        byte[] before = Files.readAllBytes(root.resolve("sessions/s.jsonl"));
        assertEquals(
                "SESSION_ARCHIVED",
                assertThrows(
                                HarnessException.class,
                                () ->
                                        HarnessSdk.verifyEnvironment(
                                                repo,
                                                "s",
                                                "env",
                                                "inspected",
                                                new SecretRedactor()))
                        .code());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("sessions/s.jsonl")));
    }
}
