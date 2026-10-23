package io.jharness.ecosystem;

import io.jharness.api.*;
import java.time.Instant;
import java.util.*;

/** One writer (the executing tool) persists questions and answers; HTTP only proposes decisions. */
public final class UserQuestionsPlugin implements HarnessPlugin, UserQuestionService {
    private PluginScope scope;
    private SessionReader reader;
    private SessionWriter writer;
    private final DataSanitizer sanitizer;
    private volatile boolean active;
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    public UserQuestionsPlugin(DataSanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    public String id() {
        return "user-questions";
    }

    public String version() {
        return "1.11";
    }

    public void start(PluginContext context) {
        scope = context.scope();
        reader = context.services().requireUnique(SessionReader.class, scope);
        writer = context.services().requireUnique(SessionWriter.class, scope);
        context.register(UserQuestionService.class, "default", this);
        context.own(
                context.services()
                        .requireUnique(ToolRegistry.class, scope)
                        .register(new AskTool(), scope));
        active = true;
    }

    public synchronized void stop() {
        active = false;
        notifyAll();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        boolean interrupted = Thread.interrupted();
        while (!pending.isEmpty() && System.nanoTime() < deadline) {
            try {
                wait(
                        Math.max(
                                1,
                                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                        deadline - System.nanoTime())));
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (!pending.isEmpty()) throw error("QUESTION_SETTLEMENT_PENDING");
    }

    private static final class Pending {
        final Map<String, Object> view;
        final long deadline;
        Map<String, Object> answer;
        Map<String, Object> draft = Map.of();
        String fingerprint = "";
        long draftRevision;

        Pending(Map<String, Object> view, long deadline) {
            this.view = view;
            this.deadline = deadline;
        }
    }

    public Map<String, Object> ask(ToolInvocation call, ToolContext context) {
        if (!active || !scope.equals(context.scope())) throw error("QUESTION_SCOPE_CLOSED");
        var spec = validateSpec(sanitizer.sanitize(call.arguments()));
        var events = reader.events();
        if (events.stream().filter(e -> e.type().equals("question.requested")).count() >= 64)
            throw error("QUESTION_LIMIT");
        var started =
                events.reversed().stream()
                        .filter(
                                e ->
                                        e.type().equals("tool.started")
                                                && call.callId().equals(e.callId()))
                        .findFirst()
                        .orElseThrow(() -> error("QUESTION_ASSOCIATION_REQUIRED"));
        if (!call.tool().equals("ask_user")
                || events.stream()
                        .anyMatch(
                                e ->
                                        call.callId().equals(e.callId())
                                                && Set.of("question.requested", "tool.completed")
                                                        .contains(e.type()))
                || !Objects.equals(started.payload().get("arguments"), call.arguments()))
            throw error("QUESTION_ASSOCIATION_REQUIRED");
        String id = UUID.randomUUID().toString();
        long timeout = ((Number) spec.get("timeoutSeconds")).longValue();
        var view = new LinkedHashMap<String, Object>(spec);
        view.put("questionId", id);
        view.put("sessionId", scope.sessionId());
        view.put("callId", call.callId());
        view.put("turnId", started.turnId());
        view.put("stepId", started.stepId());
        view.put("state", "PENDING");
        view.put("expiresAt", Instant.now().plusSeconds(timeout).toString());
        var wait = new Pending(Values.snapshot(view), System.nanoTime() + timeout * 1_000_000_000L);
        // Publication follows durable request. No HTTP thread writes to the session.
        synchronized (this) {
            if (!active) throw error("QUESTION_SCOPE_CLOSED");
            save("question.requested", started, view);
            pending.put(id, wait);
        }
        Map<String, Object> resolution;
        try {
            while (true) {
                synchronized (this) {
                    if (wait.answer != null) {
                        resolution = wait.answer;
                        break;
                    }
                    if (!active || context.cancellation().isCancelled()) {
                        resolution = Map.of("state", "CANCELLED");
                        break;
                    }
                    if (System.nanoTime() >= wait.deadline) {
                        resolution = Map.of("state", "TIMED_OUT");
                        break;
                    }
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    synchronized (this) {
                        resolution =
                                wait.answer != null ? wait.answer : Map.of("state", "CANCELLED");
                    }
                    break;
                }
            }
            var fact = new LinkedHashMap<>(view);
            fact.putAll(resolution);
            if (!active
                    || context.cancellation().isCancelled()
                    || Thread.currentThread().isInterrupted())
                fact.put("cancellationObserved", true);
            synchronized (this) {
                if (!wait.fingerprint.isEmpty()) fact.put("answerHash", wait.fingerprint);
                if (!wait.fingerprint.isEmpty()) fact.put("answerFingerprintVersion", 2);
            }
            boolean interrupted = Thread.interrupted();
            try {
                save("question.resolved", started, fact);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
            if ("CANCELLED".equals(fact.get("state"))) throw error("CANCELLED");
            return Values.snapshot(fact);
        } finally {
            synchronized (this) {
                pending.remove(id);
                notifyAll();
            }
        }
    }

    private void save(String type, SessionEvent association, Map<String, Object> payload) {
        writer.append(
                new SessionEvent(
                        UUID.randomUUID().toString(),
                        scope.sessionId(),
                        type,
                        Instant.now(),
                        1,
                        EventLevel.TOOL,
                        association.turnId(),
                        association.stepId(),
                        association.callId(),
                        sanitizer.sanitize(payload)));
        writer.flush();
    }

    public synchronized List<Map<String, Object>> list() {
        if (!active) throw error("QUESTION_SCOPE_CLOSED");
        var rows = new LinkedHashMap<String, Map<String, Object>>();
        for (var view : project(reader.events())) rows.put((String) view.get("questionId"), view);
        for (var entry : pending.entrySet()) {
            var p = entry.getValue();
            // Once a resolution is durable, it wins even before pending cleanup completes.
            if (!"INTERRUPTED".equals(rows.get(entry.getKey()).get("state"))) continue;
            var view = new LinkedHashMap<>(p.view);
            view.put(
                    "state",
                    p.answer != null
                            ? "SUBMITTED"
                            : System.nanoTime() >= p.deadline ? "TIMED_OUT" : "PENDING");
            view.put("draft", p.draft);
            view.put("draftRevision", p.draftRevision);
            if (p.answer != null) view.put("requestId", p.answer.get("requestId"));
            rows.put(entry.getKey(), Values.snapshot(view));
        }
        return List.copyOf(rows.values());
    }

    public synchronized Map<String, Object> draft(Map<String, Object> input) {
        if (!input.keySet()
                .equals(
                        Set.of(
                                "questionId",
                                "sessionId",
                                "turnId",
                                "callId",
                                "selected",
                                "text",
                                "revision"))) throw error("INVALID_ANSWER");
        var p = pending.get(input.get("questionId"));
        if (!active || p == null || p.answer != null || System.nanoTime() >= p.deadline)
            throw error("QUESTION_CLOSED");
        for (String key : List.of("sessionId", "turnId", "callId"))
            if (!Objects.equals(input.get(key), p.view.get(key)))
                throw error("QUESTION_ASSOCIATION_MISMATCH");
        if (!(input.get("revision") instanceof Number revision)
                || revision.doubleValue() != revision.longValue()
                || revision.longValue() != p.draftRevision) throw error("DRAFT_CONFLICT");
        var candidate = new LinkedHashMap<>(input);
        candidate.put("requestId", "draft");
        candidate.put(
                "skip", List.of().equals(input.get("selected")) && "".equals(input.get("text")));
        validateAnswer(candidate, p.view);
        p.draft =
                Values.snapshot(
                        sanitizer.sanitize(
                                Map.of(
                                        "selected",
                                        input.get("selected"),
                                        "text",
                                        input.get("text"))));
        p.draftRevision++;
        var saved = new LinkedHashMap<>(p.draft);
        saved.put("revision", p.draftRevision);
        return Values.snapshot(saved);
    }

    public static Map<String, Object> recorded(
            List<SessionEvent> events, Map<String, Object> input) {
        return recorded(events, input, new SecretRedactor());
    }

    public static Map<String, Object> recorded(
            List<SessionEvent> events, Map<String, Object> input, DataSanitizer sanitizer) {
        if (!input.keySet()
                .equals(
                        Set.of(
                                "questionId",
                                "sessionId",
                                "turnId",
                                "callId",
                                "requestId",
                                "selected",
                                "text",
                                "skip"))) throw error("INVALID_ANSWER");
        if (!(input.get("requestId") instanceof String requestId)
                || !requestId.matches("[A-Za-z0-9_-]{1,64}")) throw error("INVALID_ANSWER");
        var row =
                project(events).stream()
                        .filter(v -> Objects.equals(v.get("questionId"), input.get("questionId")))
                        .findFirst()
                        .orElseThrow(() -> error("QUESTION_NOT_FOUND"));
        for (String key : List.of("sessionId", "turnId", "callId"))
            if (!Objects.equals(input.get(key), row.get(key)))
                throw error("QUESTION_ASSOCIATION_MISMATCH");
        validateAnswer(input, row);
        if (Objects.equals(input.get("requestId"), row.get("requestId"))
                && (row.get("answerFingerprintVersion") instanceof Number version
                                && version.intValue() == 2
                        ? fingerprint(input).equals(row.get("answerHash"))
                                && sameText(input, row, sanitizer)
                        : legacyFingerprint(input).equals(row.get("answerHash")))) return row;
        throw error(row.containsKey("requestId") ? "ANSWER_CONFLICT" : "QUESTION_CLOSED");
    }

    public synchronized Map<String, Object> respond(Map<String, Object> input) {
        if (!input.keySet()
                .equals(
                        Set.of(
                                "questionId",
                                "sessionId",
                                "turnId",
                                "callId",
                                "requestId",
                                "selected",
                                "text",
                                "skip"))) throw error("INVALID_ANSWER");
        if (!(input.get("questionId") instanceof String id)
                || id.isBlank()
                || id.length() > 64
                || !(input.get("requestId") instanceof String requestId))
            throw error("INVALID_ANSWER");
        if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw error("INVALID_ANSWER");
        var row =
                list().stream()
                        .filter(v -> id.equals(v.get("questionId")))
                        .findFirst()
                        .orElseThrow(() -> error("QUESTION_NOT_FOUND"));
        for (String key : List.of("sessionId", "turnId", "callId"))
            if (!Objects.equals(input.get(key), row.get(key)))
                throw error("QUESTION_ASSOCIATION_MISMATCH");
        var answer = validateAnswer(input, row);
        String hash = fingerprint(input);
        if (row.containsKey("requestId")) {
            if (requestId.equals(row.get("requestId"))
                    && hash.equals(row.get("answerHash"))
                    && sameText(input, row, sanitizer)) return row;
            var p = pending.get(id);
            if (p != null
                    && p.answer != null
                    && requestId.equals(p.answer.get("requestId"))
                    && hash.equals(p.fingerprint)
                    && sameText(input, p.answer, sanitizer)) return row;
            throw error("ANSWER_CONFLICT");
        }
        var wait = pending.get(id);
        if (!active
                || wait == null
                || System.nanoTime() >= wait.deadline
                || !"PENDING".equals(row.get("state"))) throw error("QUESTION_CLOSED");
        if (pending.values().stream()
                .anyMatch(p -> p.answer != null && requestId.equals(p.answer.get("requestId"))))
            throw error("ANSWER_CONFLICT");
        if (reader.events().stream()
                .anyMatch(
                        e ->
                                e.type().equals("question.resolved")
                                        && requestId.equals(e.payload().get("requestId"))))
            throw error("ANSWER_CONFLICT");
        wait.answer = Values.snapshot(sanitizer.sanitize(answer));
        wait.fingerprint = hash;
        return list().stream()
                .filter(v -> id.equals(v.get("questionId")))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> validateAnswer(
            Map<String, Object> input, Map<String, Object> question) {
        if (!(input.get("requestId") instanceof String requestId)
                || !requestId.matches("[A-Za-z0-9_-]{1,64}")) throw error("INVALID_ANSWER");
        if (!(input.get("selected") instanceof List<?> selected)
                || selected.size() > 8
                || new HashSet<>(selected).size() != selected.size()
                || selected.stream().anyMatch(v -> !(v instanceof String))
                || !(input.get("text") instanceof String text)
                || text.length() > 4096
                || !(input.get("skip") instanceof Boolean skip)) throw error("INVALID_ANSWER");
        var allowed =
                ((List<?>) question.get("options"))
                        .stream().map(v -> Values.object(v).get("id")).toList();
        if (!allowed.containsAll(selected)
                || "single".equals(question.get("mode")) && selected.size() > 1
                || "text".equals(question.get("mode")) && !selected.isEmpty()
                || !Boolean.TRUE.equals(question.get("allowText")) && !text.isEmpty())
            throw error("INVALID_ANSWER");
        if (skip && (!selected.isEmpty() || !text.isEmpty())
                || !skip && selected.isEmpty() && text.isBlank()) throw error("INVALID_ANSWER");
        return Map.of(
                "requestId",
                input.get("requestId"),
                "state",
                skip ? "SKIPPED" : "ANSWERED",
                "selected",
                selected,
                "text",
                text);
    }

    static Map<String, Object> validateSpec(Map<String, Object> input) {
        if (!input.keySet()
                .equals(Set.of("question", "mode", "options", "allowText", "timeoutSeconds")))
            throw error("INVALID_QUESTION");
        string(input, "question", 2048);
        if (!Set.of("single", "multiple", "text").contains(input.get("mode"))
                || !(input.get("allowText") instanceof Boolean)
                || !(input.get("timeoutSeconds") instanceof Number seconds)
                || seconds.doubleValue() != seconds.intValue()
                || seconds.intValue() < 1
                || seconds.intValue() > 300
                || !(input.get("options") instanceof List<?> options)
                || options.size() > 8) throw error("INVALID_QUESTION");
        var ids = new HashSet<String>();
        int recommended = 0;
        for (Object raw : options) {
            var option = Values.object(raw);
            if (!option.keySet().equals(Set.of("id", "label", "description", "recommended")))
                throw error("INVALID_QUESTION");
            String id = string(option, "id", 32);
            string(option, "label", 128);
            if (!id.matches("[A-Za-z0-9_-]+")
                    || !ids.add(id)
                    || !(option.get("description") instanceof String description)
                    || description.length() > 512
                    || !(option.get("recommended") instanceof Boolean rec))
                throw error("INVALID_QUESTION");
            if (rec) recommended++;
        }
        if ("text".equals(input.get("mode"))) {
            if (!options.isEmpty() || !Boolean.TRUE.equals(input.get("allowText")))
                throw error("INVALID_QUESTION");
        } else if (options.size() < 2 || "single".equals(input.get("mode")) && recommended > 1)
            throw error("INVALID_QUESTION");
        return Values.snapshot(input);
    }

    public static List<Map<String, Object>> project(List<SessionEvent> events) {
        var rows = new LinkedHashMap<String, Map<String, Object>>();
        for (var event : events)
            if (Set.of("question.requested", "question.resolved").contains(event.type())) {
                var row = new LinkedHashMap<>(event.payload());
                if (!Objects.equals(row.get("sessionId"), event.sessionId())
                        || !Objects.equals(row.get("turnId"), event.turnId())
                        || !Objects.equals(row.get("stepId"), event.stepId())
                        || !Objects.equals(row.get("callId"), event.callId()))
                    throw error("QUESTION_ASSOCIATION_MISMATCH");
                if (!(row.get("questionId") instanceof String id)
                        || id.isBlank()
                        || id.length() > 64) throw error("INVALID_QUESTION_FACT");
                var spec = new LinkedHashMap<String, Object>();
                for (String key :
                        List.of("question", "mode", "options", "allowText", "timeoutSeconds"))
                    spec.put(key, row.get(key));
                validateSpec(spec);
                try {
                    Instant.parse((String) row.get("expiresAt"));
                } catch (RuntimeException ex) {
                    throw error("INVALID_QUESTION_FACT");
                }
                if (event.type().equals("question.requested")) {
                    if (rows.containsKey(id) || !"PENDING".equals(row.get("state")))
                        throw error("QUESTION_FACT_CONFLICT");
                    row.put("state", "INTERRUPTED");
                } else {
                    var prior = rows.get(id);
                    if (prior == null || !"INTERRUPTED".equals(prior.get("state")))
                        throw error("QUESTION_FACT_CONFLICT");
                    for (String key :
                            List.of(
                                    "sessionId",
                                    "turnId",
                                    "stepId",
                                    "callId",
                                    "question",
                                    "mode",
                                    "options",
                                    "allowText",
                                    "timeoutSeconds",
                                    "expiresAt"))
                        if (!Objects.equals(prior.get(key), row.get(key)))
                            throw error("QUESTION_FACT_CONFLICT");
                    if (!Set.of("ANSWERED", "SKIPPED", "CANCELLED", "TIMED_OUT")
                            .contains(row.get("state"))) throw error("INVALID_QUESTION_FACT");
                    if (Set.of("ANSWERED", "SKIPPED").contains(row.get("state"))) {
                        var answer = new LinkedHashMap<>(row);
                        answer.put("skip", "SKIPPED".equals(row.get("state")));
                        validateAnswer(answer, row);
                        if (!(row.get("answerHash") instanceof String hash)
                                || !hash.matches("[a-f0-9]{64}"))
                            throw error("INVALID_QUESTION_FACT");
                    }
                }
                rows.put(id, Values.snapshot(row));
                if (rows.size() > 64) throw error("QUESTION_LIMIT");
            }
        return List.copyOf(rows.values());
    }

    private static String string(Map<String, Object> input, String key, int limit) {
        if (!(input.get(key) instanceof String value) || value.isBlank() || value.length() > limit)
            throw error("INVALID_QUESTION");
        return value;
    }

    private static String fingerprint(Map<String, Object> input) {
        var fields = new TreeMap<>(input);
        fields.remove("text");
        return legacyFingerprint(fields);
    }

    private static boolean sameText(
            Map<String, Object> input, Map<String, Object> row, DataSanitizer sanitizer) {
        return Objects.equals(
                sanitizer.sanitize(Map.of("text", input.get("text"))).get("text"), row.get("text"));
    }

    private static String legacyFingerprint(Map<String, Object> input) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(BoundedJson.encode(new TreeMap<>(input))));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static HarnessException error(String code) {
        return new HarnessException(code, code);
    }

    private final class AskTool implements AgentTool {
        public boolean requiresSerialExecution() {
            return true;
        }

        public String name() {
            return "ask_user";
        }

        public String description() {
            return "Ask one bounded single/multiple/text question; wait for explicit user input. Recommended options are not submitted answers. Answer/skip/timeout never approves tools or plans.";
        }

        public SideEffectLevel sideEffectLevel() {
            return SideEffectLevel.READ_ONLY;
        }

        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                            "question",
                            Map.of("type", "string", "minLength", 1, "maxLength", 2048),
                            "mode",
                            Map.of("type", "string", "enum", List.of("single", "multiple", "text")),
                            "options",
                            Map.of(
                                    "type",
                                    "array",
                                    "maxItems",
                                    8,
                                    "items",
                                    Map.of(
                                            "type",
                                            "object",
                                            "properties",
                                            Map.of(
                                                    "id",
                                                    Map.of("type", "string", "maxLength", 32),
                                                    "label",
                                                    Map.of("type", "string", "maxLength", 128),
                                                    "description",
                                                    Map.of("type", "string", "maxLength", 512),
                                                    "recommended",
                                                    Map.of("type", "boolean")),
                                            "required",
                                            List.of("id", "label", "description", "recommended"),
                                            "additionalProperties",
                                            false)),
                            "allowText",
                            Map.of("type", "boolean"),
                            "timeoutSeconds",
                            Map.of("type", "integer", "minimum", 1, "maximum", 300)),
                    "required",
                    List.of("question", "mode", "options", "allowText", "timeoutSeconds"),
                    "additionalProperties",
                    false);
        }

        public Map<String, Object> outputSchema() {
            return Map.of("type", "object");
        }

        public ToolResult execute(ToolInvocation call, ToolContext context) {
            var value = new LinkedHashMap<>(ask(call, context));
            value.keySet()
                    .retainAll(
                            Set.of(
                                    "questionId",
                                    "sessionId",
                                    "turnId",
                                    "callId",
                                    "state",
                                    "selected",
                                    "text"));
            return new ToolResult(
                    call.callId(),
                    ToolStatus.SUCCESS,
                    value,
                    BoundedJson.text(value),
                    Map.of(),
                    Optional.empty());
        }
    }
}
