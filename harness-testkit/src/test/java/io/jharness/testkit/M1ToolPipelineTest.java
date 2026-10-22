package io.jharness.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import io.jharness.events.*;
import io.jharness.plugin.*;
import io.jharness.session.*;
import io.jharness.tools.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class M1ToolPipelineTest {
    @Test
    void simulatedDurableIntentIsFlushedBeforeWriteBodyAndFailureRemainsUnknown() {
        try (Fixture f = new Fixture()) {
            var flushedPrefix = new AtomicReference<List<SessionEvent>>();
            SessionWriter simulated =
                    new SessionWriter() {
                        @Override
                        public boolean durable() {
                            return true;
                        } // Mechanism simulation, no physical durability claim.

                        @Override
                        public String append(SessionEvent event) {
                            return f.writer.append(event);
                        }

                        @Override
                        public void flush() {
                            flushedPrefix.set(List.copyOf(f.events()));
                        }

                        @Override
                        public void close() {}
                    };
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            call -> {
                                assertNotNull(flushedPrefix.get());
                                assertEquals("tool.intent", flushedPrefix.get().getLast().type());
                                assertEquals(call.callId(), flushedPrefix.get().getLast().callId());
                                assertFalse(
                                        flushedPrefix.get().stream()
                                                .anyMatch(e -> e.type().equals("tool.started")));
                                throw new HarnessException(
                                        "TIMEOUT", "a private-sentinel in an exception");
                            }),
                    SCOPE);
            ToolResult result =
                    f.pipeline.execute(
                            call(Map.of("text", "write")),
                            f.context(
                                    simulated,
                                    allow(),
                                    ApprovalService.rejectAll(),
                                    CancellationToken.NONE));
            assertEquals(ToolStatus.UNKNOWN, result.status());
            assertFalse(result.toString().contains("private-sentinel"));
        }
    }

    private static final PluginScope SCOPE = new PluginScope("s", "agent");

    @Test
    void successRecordsApprovedIntentAndBodyOrderAndIgnoresModelSideEffectClaim() {
        try (Fixture f = new Fixture()) {
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                assertEquals(
                                        List.of(
                                                "tool.requested",
                                                "approval.decided",
                                                "tool.intent",
                                                "tool.started"),
                                        f.types());
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            ToolResult result =
                    f.run(
                            Map.of("text", "input"),
                            (call, context) -> PolicyEngine.Decision.ALLOW,
                            ApprovalService.rejectAll());
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertEquals(
                    List.of(
                            "tool.requested",
                            "approval.decided",
                            "tool.intent",
                            "tool.started",
                            "tool.completed"),
                    f.types());
            assertEquals("READ_ONLY", f.events().getFirst().payload().get("sideEffectLevel"));
            assertEquals(Boolean.TRUE, f.events().getLast().payload().get("bodyEntered"));
            assertTrue(f.events().getLast().payload().containsKey("durationMs"));
        }
    }

    @Test
    void invalidParametersMissingToolsAndWrongScopeNeverEnterBody() {
        try (Fixture f = new Fixture()) {
            AtomicInteger bodies = new AtomicInteger();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                bodies.incrementAndGet();
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    new PluginScope("other", "agent"));
            assertEquals(
                    "UNKNOWN_TOOL",
                    f.run(Map.of("text", "x"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                bodies.incrementAndGet();
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            assertEquals(
                    "SCHEMA_MISMATCH",
                    f.run(Map.of("text", 7), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            assertEquals(0, bodies.get());
            var missing =
                    f.pipeline.execute(
                            new ToolInvocation(
                                    "missing",
                                    "never-registered",
                                    Map.of("text", "x"),
                                    SideEffectLevel.READ_ONLY),
                            f.context(
                                    f.writer,
                                    allow(),
                                    ApprovalService.rejectAll(),
                                    CancellationToken.NONE));
            assertEquals("UNKNOWN_TOOL", missing.error().orElseThrow().code());
            assertEquals(
                    ToolStatus.SUCCESS,
                    f.run(Map.of("text", "valid"), allow(), ApprovalService.rejectAll()).status());
            assertEquals(1, bodies.get());
        }
    }

    @Test
    void askRejectionIsRecordedAndNeverExecutes() {
        try (Fixture f = new Fixture()) {
            AtomicBoolean body = new AtomicBoolean();
            f.tools.register(
                    tool(
                            SideEffectLevel.SYSTEM_IMPACT,
                            call -> {
                                body.set(true);
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            ToolResult result =
                    f.run(
                            Map.of("text", "delete"),
                            (call, c) -> PolicyEngine.Decision.ASK,
                            (call, c) -> {
                                assertEquals(
                                        TaskStatus.WAITING_APPROVAL,
                                        new EventSessionProjection(f.events()).taskStatus());
                                return new ApprovalService.ApprovalDecision(
                                        false, "user rejected", "user");
                            });
            assertEquals(ToolStatus.DENIED, result.status());
            assertFalse(body.get());
            assertEquals(
                    List.of(
                            "tool.requested",
                            "approval.requested",
                            "approval.decided",
                            "tool.completed"),
                    f.types());
        }
    }

    @Test
    void readOnlyBypassAndParameterRewriteUseExtensionsWithoutModifyingPolicy() {
        try (Fixture f = new Fixture()) {
            AtomicInteger asks = new AtomicInteger();
            AtomicReference<String> parameter = new AtomicReference<>();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                parameter.set((String) call.arguments().get("text"));
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            f.bus.intercept(
                    StandardExtensionPoints.BEFORE_TOOL,
                    (call, next) ->
                            next.proceed(
                                    new ToolInvocation(
                                            call.callId(),
                                            call.tool(),
                                            Map.of("text", "rewritten"),
                                            call.sideEffectLevel())),
                    0);
            PolicyEngine base = (call, c) -> PolicyEngine.Decision.ASK;
            ExtensionPoint<PolicyEngine> point = PolicyExtensionPoints.EVALUATE;
            f.bus.intercept(
                    point,
                    (policy, next) -> {
                        PolicyEngine wrapped =
                                (call, c) ->
                                        call.sideEffectLevel() == SideEffectLevel.READ_ONLY
                                                ? PolicyEngine.Decision.ALLOW
                                                : policy.evaluate(call, c);
                        return next.proceed(wrapped);
                    },
                    0);
            ToolResult result =
                    f.run(
                            Map.of("text", "original"),
                            base,
                            (call, c) -> {
                                asks.incrementAndGet();
                                return new ApprovalService.ApprovalDecision(false, "no", "user");
                            });
            assertEquals("rewritten", parameter.get());
            assertEquals(0, asks.get());
            assertEquals(ToolStatus.SUCCESS, result.status());
            assertEquals(
                    ToolResults.event(result).get("value"),
                    f.events().getLast().payload().get("value"));
            assertEquals("SUCCESS", f.events().getLast().payload().get("status"));
        }
    }

    @Test
    void memoryWriterCannotAuthorizeSideEffectsEvenWithMemoryPolicy() {
        try (Fixture f = new Fixture()) {
            AtomicBoolean body = new AtomicBoolean();
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            call -> {
                                body.set(true);
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            ToolResult result =
                    f.run(Map.of("text", "write"), allow(), ApprovalService.rejectAll());
            assertEquals("DURABILITY_REQUIRED", result.error().orElseThrow().code());
            assertFalse(body.get());
            assertFalse(f.types().contains("tool.started"));
        }
    }

    @Test
    void flushFailureWithSimulatedDurableWriterPreventsBodyEntry() {
        try (Fixture f = new Fixture()) {
            var observed = new java.util.concurrent.CopyOnWriteArrayList<SessionEvent>();
            f.bus.notify("checkpoint.failed", observed::add);
            AtomicBoolean body = new AtomicBoolean();
            f.tools.register(
                    tool(
                            SideEffectLevel.WORKSPACE_WRITE,
                            call -> {
                                body.set(true);
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            SessionWriter fault =
                    new SessionWriter() {
                        @Override
                        public boolean durable() {
                            return true;
                        } // Simulation only; no real file operations.

                        @Override
                        public String append(SessionEvent event) {
                            return f.writer.append(event);
                        }

                        @Override
                        public void flush() {
                            throw new HarnessException("CHECKPOINT_FAILED", "injected");
                        }

                        @Override
                        public void close() {}
                    };
            ToolResult result =
                    f.pipeline.execute(
                            call(Map.of("text", "write")),
                            f.context(
                                    fault,
                                    allow(),
                                    ApprovalService.rejectAll(),
                                    CancellationToken.NONE));
            assertEquals("CHECKPOINT_FAILED", result.error().orElseThrow().code());
            assertFalse(body.get());
            assertFalse(f.types().contains("tool.started"));
            f.bus.awaitNotifications(java.time.Duration.ofSeconds(2));
            assertEquals(1, observed.size());
            assertEquals("CHECKPOINT_FAILED", observed.getFirst().payload().get("code"));
        }
    }

    @Test
    void executionWrapperCannotChangeApprovedArgumentsOrStripCancellation() {
        try (Fixture f = new Fixture()) {
            AtomicInteger body = new AtomicInteger();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                body.incrementAndGet();
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            Registration rewrite =
                    f.bus.intercept(
                            StandardExtensionPoints.TOOL_EXECUTION,
                            (executor, next) ->
                                    next.proceed(
                                            (call, cancellation) ->
                                                    executor.execute(
                                                            new ToolInvocation(
                                                                    call.callId(),
                                                                    call.tool(),
                                                                    Map.of("text", "unapproved"),
                                                                    call.sideEffectLevel()),
                                                            cancellation)),
                            0);
            assertEquals(
                    "APPROVAL_MISMATCH",
                    f.run(Map.of("text", "approved"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            rewrite.close();
            AtomicBoolean cancelled = new AtomicBoolean();
            f.bus.intercept(
                    StandardExtensionPoints.TOOL_EXECUTION,
                    (executor, next) ->
                            next.proceed(
                                    (call, cancellation) -> {
                                        cancelled.set(true);
                                        return executor.execute(call, CancellationToken.NONE);
                                    }),
                    0);
            ToolResult result =
                    f.pipeline.execute(
                            call(Map.of("text", "approved")),
                            f.context(
                                    f.writer,
                                    allow(),
                                    ApprovalService.rejectAll(),
                                    cancelled::get));
            assertEquals(ToolStatus.CANCELLED, result.status());
            assertEquals(0, body.get());
        }
    }

    @Test
    void revokedToolIsRecheckedAfterApproval() {
        try (Fixture f = new Fixture()) {
            Registration handle =
                    f.tools.register(
                            tool(
                                    SideEffectLevel.READ_ONLY,
                                    call -> {
                                        fail("revoked body");
                                        return null;
                                    }),
                            SCOPE);
            ToolResult result =
                    f.run(
                            Map.of("text", "x"),
                            (call, c) -> PolicyEngine.Decision.ASK,
                            (call, c) -> {
                                handle.close();
                                return new ApprovalService.ApprovalDecision(
                                        true, "approved", "user");
                            });
            assertEquals("UNKNOWN_TOOL", result.error().orElseThrow().code());
        }
    }

    @Test
    void wrongCallIdInvalidOutputAndPostprocessorStatusChangesCannotReturnSuccess() {
        try (Fixture f = new Fixture()) {
            Registration wrong =
                    f.tools.register(
                            tool(
                                    SideEffectLevel.READ_ONLY,
                                    call -> success("other", Map.of("answer", "ok"))),
                            SCOPE);
            assertEquals(
                    "RESULT_CALL_MISMATCH",
                    f.run(Map.of("text", "x"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            wrong.close();
            Registration bad =
                    f.tools.register(
                            tool(
                                    SideEffectLevel.READ_ONLY,
                                    call -> success(call.callId(), Map.of("wrong", 1))),
                            SCOPE);
            assertEquals(
                    "INVALID_TOOL_OUTPUT",
                    f.run(Map.of("text", "x"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            bad.close();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call ->
                                    ToolResults.failure(
                                            call.callId(), ToolStatus.FAILED, "real-failure")),
                    SCOPE);
            f.bus.intercept(
                    StandardExtensionPoints.AFTER_TOOL,
                    (result, next) -> success(result.callId(), Map.of("answer", "lie")),
                    0);
            assertEquals(
                    "RESULT_STATUS_CHANGED",
                    f.run(Map.of("text", "x"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
        }
    }

    @Test
    void repeatedBodyEntryIsPreventedAndErrorsAreRedacted() {
        try (Fixture f = new Fixture()) {
            AtomicInteger body = new AtomicInteger();
            f.tools.register(
                    tool(
                            SideEffectLevel.READ_ONLY,
                            call -> {
                                body.incrementAndGet();
                                return success(call.callId(), Map.of("answer", "ok"));
                            }),
                    SCOPE);
            f.bus.intercept(
                    StandardExtensionPoints.TOOL_EXECUTION,
                    (executor, next) ->
                            next.proceed(
                                    (call, cancellation) -> {
                                        executor.execute(call, cancellation);
                                        return executor.execute(call, cancellation);
                                    }),
                    0);
            assertEquals(
                    "REPEATED_EXECUTION",
                    f.run(Map.of("text", "x"), allow(), ApprovalService.rejectAll())
                            .error()
                            .orElseThrow()
                            .code());
            assertEquals(1, body.get());
        }
    }

    private static PolicyEngine allow() {
        return (call, context) -> PolicyEngine.Decision.ALLOW;
    }

    private static ToolInvocation call(Map<String, Object> args) {
        return new ToolInvocation(
                UUID.randomUUID().toString(), "echo", args, SideEffectLevel.SYSTEM_IMPACT);
    }

    private static ToolResult success(String id, Map<String, Object> value) {
        return new ToolResult(id, ToolStatus.SUCCESS, value, "ok", Map.of(), Optional.empty());
    }

    private static AgentTool tool(
            SideEffectLevel effect, Function<ToolInvocation, ToolResult> body) {
        return new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "echo fixture";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of(
                        "type",
                        "object",
                        "required",
                        List.of("text"),
                        "properties",
                        Map.of("text", Map.of("type", "string")),
                        "additionalProperties",
                        false);
            }

            @Override
            public Map<String, Object> outputSchema() {
                return Map.of(
                        "type",
                        "object",
                        "required",
                        List.of("answer"),
                        "properties",
                        Map.of("answer", Map.of("type", "string")));
            }

            @Override
            public SideEffectLevel sideEffectLevel() {
                return effect;
            }

            @Override
            public ToolResult execute(ToolInvocation args, ToolContext context) {
                return body.apply(args);
            }
        };
    }

    private static final class Fixture implements AutoCloseable {
        final InMemorySessionRepository repo = new InMemorySessionRepository();
        final SessionWriter writer = repo.openWriter("s", WriteOwnership.EXCLUSIVE);
        final DefaultEventBus bus = new DefaultEventBus();
        final ScopedToolRegistry tools = new ScopedToolRegistry();
        final DefaultToolPipeline pipeline =
                new DefaultToolPipeline(new JsonSchemaValidator(), new SecretRedactor());

        List<SessionEvent> events() {
            return repo.openReader("s").events();
        }

        List<String> types() {
            return events().stream().map(SessionEvent::type).toList();
        }

        ToolPipelineContext context(
                SessionWriter storage,
                PolicyEngine policy,
                ApprovalService approvals,
                CancellationToken cancellation) {
            return new ToolPipelineContext(
                    SCOPE,
                    "t",
                    "p",
                    tools,
                    storage,
                    bus,
                    policy,
                    approvals,
                    new MemoryCheckpointPolicy(),
                    cancellation);
        }

        ToolResult run(
                Map<String, Object> arguments, PolicyEngine policy, ApprovalService approvals) {
            return pipeline.execute(
                    call(arguments), context(writer, policy, approvals, CancellationToken.NONE));
        }

        @Override
        public void close() {
            writer.close();
            bus.close();
        }
    }
}
