package io.jharness.web;

import com.sun.net.httpserver.*;
import io.jharness.api.*;
import io.jharness.ecosystem.HarnessSdk;
import io.jharness.session.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Local single-operator HTTP adapter. All execution capabilities come from trusted profiles. */
public final class HarnessWebServer implements AutoCloseable {
    @FunctionalInterface
    public interface SdkFactory {
        HarnessSdk open(String sessionId, ApprovalService approvals, DataSanitizer sanitizer)
                throws Exception;
    }

    public record Profile(String workspaceBinding, SessionRepository sessions, SdkFactory factory) {
        public Profile {
            Values.required(workspaceBinding, "workspace binding");
            Objects.requireNonNull(sessions);
            Objects.requireNonNull(factory);
        }
    }

    private final HttpServer http;
    private final ThreadPoolExecutor requests;
    private final ScheduledThreadPoolExecutor deadlines;
    private final Map<String, Profile> profiles;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final Map<String, Task> submissions = new HashMap<>();
    private final Set<String> occupied = new HashSet<>();
    private final Semaphore streams = new Semaphore(2);
    private final SessionWriter catalog;
    private final String catalogId;
    private final byte[] credential;
    private final DataSanitizer sanitizer;
    private final Duration approvalTimeout;
    private final int maxActive, maxTasks;
    private boolean closed, broken;

    /**
     * Catalog is exclusively owned until close; interrupted tasks are loaded for inspection only.
     */
    public HarnessWebServer(
            int port,
            String bearerToken,
            Map<String, Profile> profiles,
            SessionRepository catalogRepository,
            String catalogId,
            DataSanitizer sanitizer,
            int maxActive,
            int maxTasks,
            Duration approvalTimeout)
            throws IOException {
        if (port < 0
                || port > 65535
                || bearerToken == null
                || !bearerToken.matches("[A-Za-z0-9_-]{32,256}")
                || profiles.isEmpty()
                || profiles.size() > 16
                || profiles.keySet().stream().anyMatch(id -> !id.matches("[A-Za-z0-9_-]{1,32}"))
                || maxActive < 1
                || maxActive > 4
                || maxTasks < maxActive
                || maxTasks > 32
                || approvalTimeout.isNegative()
                || approvalTimeout.isZero()
                || approvalTimeout.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Invalid local Web configuration");
        this.profiles = Map.copyOf(profiles);
        this.catalogId = Values.required(catalogId, "catalog id");
        this.maxActive = maxActive;
        this.maxTasks = maxTasks;
        this.approvalTimeout = approvalTimeout;
        credential = digest(bearerToken);
        var redactor = new SecretRedactor(List.of(bearerToken));
        this.sanitizer = data -> redactor.sanitize(sanitizer.sanitize(data));
        catalog =
                new SanitizingSessionWriter(
                        catalogRepository.openWriter(catalogId, WriteOwnership.EXCLUSIVE),
                        this.sanitizer);
        HttpServer server = null;
        try {
            if (!catalog.durable())
                throw new IllegalArgumentException("Durable Web catalog required");
            restore(catalogRepository.openReader(catalogId).events());
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
            http = server;
            requests =
                    new ThreadPoolExecutor(
                            8,
                            8,
                            0,
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(16),
                            daemonFactory("jharness-web-http"),
                            new ThreadPoolExecutor.AbortPolicy());
            deadlines = new ScheduledThreadPoolExecutor(1, daemonFactory("jharness-web-deadline"));
            deadlines.setRemoveOnCancelPolicy(true);
            http.setExecutor(requests);
            http.createContext("/", this::handle);
            http.start();
        } catch (IOException | RuntimeException ex) {
            if (server != null) server.stop(0);
            catalog.close();
            throw ex;
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return job -> {
            var thread = new Thread(job, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    public URI address() {
        return URI.create("http://127.0.0.1:" + http.getAddress().getPort());
    }

    private void restore(List<SessionEvent> events) {
        for (var event : events) {
            var payload = event.payload();
            if (event.type().equals("web.task.accepted")) {
                var task =
                        new Task(
                                (String) payload.get("taskId"),
                                (String) payload.get("profile"),
                                (String) payload.get("requestHash"),
                                (String) payload.get("goalHash"),
                                (String) payload.get("workspaceBinding"));
                task.phase = "UNKNOWN";
                task.recovered = true;
                if (!profiles.containsKey(task.profile)
                        || !profiles.get(task.profile).workspaceBinding().equals(task.binding)
                        || tasks.putIfAbsent(task.id, task) != null
                        || submissions.putIfAbsent(task.requestHash, task) != null)
                    throw new IllegalArgumentException("Catalog/profile mismatch");
            } else if (event.type().equals("web.task.verified")) {
                var task = tasks.get(payload.get("taskId"));
                if (task == null || !task.phase.equals("UNKNOWN"))
                    throw new IllegalArgumentException("Invalid Web verification");
                task.phase = "VERIFIED";
                task.error = "EXTERNALLY_VERIFIED_NO_REPLAY";
            } else if (event.type().equals("web.task.finished")) {
                var task = tasks.get(payload.get("taskId"));
                if (task == null) throw new IllegalArgumentException("Invalid Web catalog");
                task.phase = (String) payload.get("phase");
                task.status = (String) payload.get("status");
                task.error = (String) payload.get("error");
            } else throw new IllegalArgumentException("Not a Web catalog");
        }
        if (tasks.size() > maxTasks)
            throw new IllegalArgumentException("Catalog exceeds task bound");
        // Unknown ownership must be externally inspected before this binding can be used again.
        tasks.values().stream()
                .filter(t -> t.phase.equals("UNKNOWN"))
                .forEach(t -> occupied.add(t.binding));
    }

    private void handle(HttpExchange exchange) {
        var deadline = deadlines.schedule(exchange::close, 10, TimeUnit.SECONDS);
        try (exchange) {
            try {
                if (!exchange.getRequestHeaders()
                                .getOrDefault("Host", List.of())
                                .equals(List.of(address().getRawAuthority()))
                        || !trustedBrowser(exchange)) throw fault(403, "LOCAL_CLIENT_REQUIRED");
                if (exchange.getRequestURI().getRawPath().contains("%"))
                    throw fault(400, "INVALID_PATH");
                if (!exchange.getRequestURI().getPath().startsWith("/v1/")) {
                    UiResources.serve(exchange);
                    return;
                }
                if (!authorized(exchange)) throw fault(401, "UNAUTHORIZED");
                route(exchange);
            } catch (WebFault ex) {
                respond(exchange, ex.status, Map.of("error", ex.code));
            } catch (Exception ex) {
                // Provider messages and request URLs may contain secrets. Never echo them.
                respond(exchange, 500, Map.of("error", "WEB_OPERATION_FAILED"));
            }
        } catch (IOException ignored) {
            // Disconnect never resubmits execution or implies rollback.
        } finally {
            deadline.cancel(false);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        var headers = exchange.getRequestHeaders().getOrDefault("Authorization", List.of());
        return headers.size() == 1
                && headers.getFirst().startsWith("Bearer ")
                && MessageDigest.isEqual(credential, digest(headers.getFirst().substring(7)));
    }

    private boolean trustedBrowser(HttpExchange exchange) {
        var origins = exchange.getRequestHeaders().getOrDefault("Origin", List.of());
        var sites = exchange.getRequestHeaders().getOrDefault("Sec-Fetch-Site", List.of());
        return (origins.isEmpty() || origins.equals(List.of(address().toString())))
                && (sites.isEmpty()
                        || sites.size() == 1
                                && Set.of("same-origin", "same-site", "none")
                                        .contains(sites.getFirst()));
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        var path = exchange.getRequestURI().getPath().split("/", -1);
        var query = query(exchange);
        if (method.equals("GET") && query.isEmpty()) {
            if (Arrays.equals(path, new String[] {"", "v1", "info"})) {
                respond(
                        exchange,
                        200,
                        Map.of(
                                "profiles",
                                profiles.keySet().stream().sorted().toList(),
                                "protocolVersion",
                                HarnessSdk.PROTOCOL_VERSION));
                return;
            }
            if (path.length == 4 && path[1].equals("v1") && path[2].equals("submissions")) {
                if (!path[3].matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
                synchronized (this) {
                    Task task = submissions.get(hash(path[3]));
                    if (task == null) throw fault(404, "SUBMISSION_NOT_FOUND");
                    respond(exchange, 200, summary(task));
                }
                return;
            }
        }
        if (path.length < 3 || !path[1].equals("v1") || !path[2].equals("tasks"))
            throw fault(404, "NOT_FOUND");
        if (path.length == 3 && query.isEmpty()) {
            if (method.equals("POST")) {
                var body = body(exchange, Set.of("profile", "goal", "requestId"));
                respond(
                        exchange,
                        202,
                        submit(
                                text(body, "profile", 32),
                                text(body, "goal", 16384),
                                text(body, "requestId", 64)));
                return;
            }
            if (method.equals("GET")) {
                Map<String, Object> response;
                synchronized (this) {
                    response = Map.of("tasks", tasks.values().stream().map(this::summary).toList());
                }
                respond(exchange, 200, response);
                return;
            }
        }
        if (path.length < 4) throw fault(405, "METHOD_NOT_ALLOWED");
        Task task;
        synchronized (this) {
            task = tasks.get(path[3]);
            if (task == null) throw fault(404, "NOT_FOUND");
        }
        if (path.length == 4 && method.equals("GET") && query.isEmpty()) {
            Map<String, Object> response;
            synchronized (this) {
                response = summary(task);
            }
            respond(exchange, 200, response);
        } else if (path.length == 5
                && path[4].equals("report")
                && method.equals("GET")
                && query.isEmpty()) {
            var recorded = events(task);
            var counts = new TreeMap<String, Integer>();
            for (var e : recorded) counts.merge(e.type(), 1, Integer::sum);
            var facts =
                    recorded.stream()
                            .filter(
                                    e ->
                                            Set.of(
                                                            "tool.completed",
                                                            "tool.verified",
                                                            "delivery.registered",
                                                            "artifact.created",
                                                            "turn.ended")
                                                    .contains(e.type()))
                            .toList();
            var rows = new ArrayList<Map<String, Object>>();
            int reportBytes = 4096;
            for (int i = Math.max(0, facts.size() - 12); i < facts.size(); i++) {
                var row = event(facts.get(i));
                int length = WebJson.encode(row).length;
                if (reportBytes + length > WebJson.RESPONSE_LIMIT - 1024) break;
                rows.add(row);
                reportBytes += length;
            }
            respond(
                    exchange,
                    200,
                    Map.of(
                            "source",
                            "session-events",
                            "task",
                            summary(task),
                            "eventCount",
                            recorded.size(),
                            "eventTypes",
                            counts,
                            "facts",
                            rows,
                            "factsLimited",
                            facts.size() > rows.size()));
        } else if (path.length == 5
                && path[4].equals("search")
                && method.equals("POST")
                && query.isEmpty()) {
            var input = body(exchange, Set.of("text", "after", "limit"));
            String needle = text(input, "text", 256).toLowerCase(Locale.ROOT);
            if (!(input.get("after") instanceof String after)
                    || after.length() > 64
                    || !(input.get("limit") instanceof Number number)
                    || number.intValue() < 1
                    || number.intValue() > 50
                    || number.doubleValue() != number.intValue())
                throw fault(400, "INVALID_SEARCH");
            var recorded = events(task);
            int index = 0;
            if (!after.isEmpty()) {
                while (index < recorded.size() && !recorded.get(index).eventId().equals(after))
                    index++;
                if (index == recorded.size()) throw fault(400, "UNKNOWN_CURSOR");
                index++;
            }
            var matches = new ArrayList<Map<String, Object>>();
            String next = after;
            int bytes = 256;
            for (; index < recorded.size(); index++) {
                var e = recorded.get(index);
                var safe = sanitizer.sanitize(e.payload());
                if ((e.type() + new String(WebJson.encode(safe), StandardCharsets.UTF_8))
                        .toLowerCase(Locale.ROOT)
                        .contains(needle)) {
                    var row = event(e);
                    int length = WebJson.encode(row).length;
                    if (matches.size() >= number.intValue()
                            || bytes + length > WebJson.RESPONSE_LIMIT - 1024) break;
                    matches.add(row);
                    bytes += length;
                }
                next = e.eventId();
            }
            respond(
                    exchange,
                    200,
                    Map.of(
                            "events",
                            matches,
                            "next",
                            next,
                            "more",
                            index < recorded.size(),
                            "source",
                            "sanitized-session-events"));
        } else if (path.length == 5
                && path[4].equals("cancel")
                && method.equals("POST")
                && query.isEmpty()) {
            body(exchange, Set.of());
            Map<String, Object> response;
            synchronized (this) {
                cancel(task);
                response = summary(task);
            }
            respond(exchange, 200, response);
        } else if (path.length == 5 && path[4].equals("events") && method.equals("GET")) {
            if (exchange.getRequestHeaders()
                    .getOrDefault("Accept", List.of())
                    .equals(List.of("text/event-stream"))) stream(exchange, task, query);
            else {
                if (exchange.getRequestHeaders().containsKey("Last-Event-ID"))
                    throw fault(400, "SSE_REQUIRED");
                respond(exchange, 200, eventPage(task, query));
            }
        } else if (path.length == 5
                && path[4].equals("diff")
                && method.equals("GET")
                && query.isEmpty()) {
            var events = events(task);
            var diff =
                    events.reversed().stream()
                            .filter(
                                    e ->
                                            e.type().equals("tool.completed")
                                                    && "git_diff".equals(e.payload().get("tool")))
                            .findFirst();
            if (diff.isEmpty()) throw fault(404, "DIFF_NOT_RECORDED");
            respond(
                    exchange,
                    200,
                    Map.of("source", "recorded-tool-result", "event", event(diff.get())));
        } else if (path.length == 5
                && path[4].equals("approvals")
                && method.equals("GET")
                && query.isEmpty()) {
            Map<String, Object> response;
            synchronized (this) {
                response =
                        Map.of(
                                "approvals",
                                task.approvals.values().stream()
                                        .filter(p -> !p.decision.isDone())
                                        .map(p -> p.view)
                                        .toList());
            }
            respond(exchange, 200, response);
        } else if (path.length == 6
                && path[4].equals("approvals")
                && method.equals("POST")
                && query.isEmpty()) {
            var body = body(exchange, Set.of("approved", "reason", "callId"));
            if (!(body.get("approved") instanceof Boolean approved))
                throw fault(400, "INVALID_BODY");
            String reason = clean(text(body, "reason", 1024));
            String callId = text(body, "callId", 256);
            synchronized (this) {
                Pending pending = task.approvals.get(path[5]);
                if (pending == null
                        || pending.decision.isDone()
                        || task.cancelled
                        || !callId.equals(pending.callId)) throw fault(409, "STALE_APPROVAL");
                pending.decision.complete(
                        new ApprovalService.ApprovalDecision(approved, reason, "web-operator"));
            }
            respond(exchange, 200, Map.of("accepted", true));
        } else throw fault(405, "METHOD_OR_QUERY_NOT_ALLOWED");
    }

    private synchronized Map<String, Object> submit(String profile, String goal, String requestId) {
        if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
        if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
        String requestHash = hash(requestId), goalHash = hash(goal);
        var prior = submissions.get(requestHash);
        if (prior != null) {
            if (!prior.profile.equals(profile) || !prior.goalHash.equals(goalHash))
                throw fault(409, "REQUEST_ID_CONFLICT");
            return summary(prior);
        }
        var selected = profiles.get(profile);
        if (selected == null) throw fault(400, "UNKNOWN_PROFILE");
        if (tasks.size() >= maxTasks || occupied.size() >= maxActive)
            throw fault(429, "TASK_LIMIT");
        if (occupied.contains(selected.workspaceBinding())) throw fault(409, "WORKSPACE_BUSY");
        String sanitizedGoal = clean(goal);
        var task =
                new Task(
                        "web-" + UUID.randomUUID(),
                        profile,
                        requestHash,
                        goalHash,
                        selected.workspaceBinding());
        tasks.put(task.id, task);
        submissions.put(requestHash, task);
        occupied.add(task.binding);
        try {
            catalog.append(
                    SessionEvent.session(
                            catalogId,
                            "web.task.accepted",
                            Map.of(
                                    "taskId",
                                    task.id,
                                    "profile",
                                    profile,
                                    "requestHash",
                                    requestHash,
                                    "goalHash",
                                    goalHash,
                                    "workspaceBinding",
                                    task.binding)));
            catalog.flush();
        } catch (RuntimeException ex) {
            broken = true;
            task.phase = "UNKNOWN";
            throw fault(503, "SUBMISSION_NOT_CONFIRMED");
        }
        task.worker =
                daemonFactory("jharness-web-task").newThread(() -> execute(task, sanitizedGoal));
        task.worker.start();
        return summary(task);
    }

    private void execute(Task task, String goal) {
        String phase = "FINISHED", status = "UNKNOWN", error = "";
        HarnessSdk sdk = null;
        try {
            synchronized (this) {
                if (task.cancelled || closed) throw new CancellationException();
            }
            sdk =
                    profiles.get(task.profile)
                            .factory()
                            .open(
                                    task.id,
                                    (call, context) -> approve(task, call, context),
                                    sanitizer);
            synchronized (this) {
                task.sdk = sdk;
                if (task.cancelled || closed) sdk.cancel();
                task.phase = "RUNNING";
                task.status = "RUNNING";
            }
            if (!sdk.snapshot().sessionId().equals(task.id)) throw new IllegalStateException();
            status = sdk.run(goal, () -> task.cancelled).status().name();
        } catch (CancellationException ex) {
            status = "CANCELLED";
        } catch (Exception ex) {
            phase = "UNKNOWN";
            error = "TASK_EXECUTION_UNCERTAIN";
        } finally {
            if (sdk != null) {
                synchronized (this) {
                    task.phase = "CLOSING";
                }
                try {
                    sdk.close();
                    if (!SessionRecovery.inspect(events(task)).ready()) {
                        phase = "UNKNOWN";
                        error = "RECOVERY_REQUIRED";
                    }
                } catch (RuntimeException ex) {
                    phase = "UNKNOWN";
                    error = "TASK_CLOSE_UNCERTAIN";
                }
            }
            synchronized (this) {
                task.sdk = null;
                task.phase = phase;
                task.status = status;
                task.error = error;
                task.approvals.clear();
                if (!closed) {
                    try {
                        catalog.append(
                                SessionEvent.session(
                                        catalogId,
                                        "web.task.finished",
                                        Map.of(
                                                "taskId", task.id, "phase", phase, "status", status,
                                                "error", error)));
                        catalog.flush();
                    } catch (RuntimeException ex) {
                        broken = true;
                        task.phase = "UNKNOWN";
                    }
                }
                if (task.phase.equals("FINISHED")) occupied.remove(task.binding);
                notifyAll();
            }
        }
    }

    private ApprovalService.ApprovalDecision approve(
            Task task, ToolInvocation call, ToolContext context) {
        String id = UUID.randomUUID().toString();
        var pending =
                new Pending(
                        call.callId(),
                        Map.of(
                                "approvalId",
                                id,
                                "callId",
                                call.callId(),
                                "sessionId",
                                context.scope().sessionId(),
                                "agentId",
                                context.scope().agentId(),
                                "tool",
                                call.tool(),
                                "arguments",
                                call.arguments(),
                                "sideEffectLevel",
                                call.sideEffectLevel().name()));
        synchronized (this) {
            if (closed || task.cancelled || task.approvals.size() >= 8)
                return new ApprovalService.ApprovalDecision(
                        false, "Approval unavailable", "web-server");
            task.approvals.put(id, pending);
        }
        long end = System.nanoTime() + approvalTimeout.toNanos();
        try {
            while (System.nanoTime() < end) {
                context.cancellation().throwIfCancelled();
                synchronized (this) {
                    if (closed || task.cancelled) break;
                }
                try {
                    return pending.decision.get(20, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    /* bounded cancellation polling */
                }
            }
            return new ApprovalService.ApprovalDecision(
                    false, "Approval expired or cancelled", "web-server");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ApprovalService.ApprovalDecision(
                    false, "Approval interrupted", "web-server");
        } catch (ExecutionException ex) {
            return new ApprovalService.ApprovalDecision(
                    false, "Approval unavailable", "web-server");
        } finally {
            synchronized (this) {
                task.approvals.remove(id);
            }
        }
    }

    private void cancel(Task task) {
        if (task.worker == null || !task.worker.isAlive()) return;
        task.cancelled = true;
        if (task.sdk != null) task.sdk.cancel();
        task.approvals
                .values()
                .forEach(
                        p ->
                                p.decision.complete(
                                        new ApprovalService.ApprovalDecision(
                                                false, "Task cancelled", "web-server")));
    }

    private Map<String, Object> summary(Task task) {
        return Map.of(
                "taskId",
                task.id,
                "profile",
                task.profile,
                "phase",
                task.phase,
                "status",
                task.status,
                "error",
                task.error,
                "cancelRequested",
                task.cancelled,
                "recovered",
                task.recovered,
                "protocolVersion",
                HarnessSdk.PROTOCOL_VERSION);
    }

    private List<SessionEvent> events(Task task) {
        var repository = profiles.get(task.profile).sessions();
        if (!repository.list().contains(task.id)) return List.of();
        return repository.openReader(task.id).events();
    }

    private Map<String, Object> eventPage(Task task, Map<String, String> query) throws IOException {
        if (!Set.of("after", "limit").containsAll(query.keySet()))
            throw fault(400, "INVALID_QUERY");
        int limit;
        try {
            limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        } catch (NumberFormatException ex) {
            throw fault(400, "INVALID_QUERY");
        }
        if (limit < 1 || limit > 100) throw fault(400, "INVALID_QUERY");
        var events = events(task);
        int start = 0;
        String cursor = query.getOrDefault("after", "");
        if (!cursor.isEmpty()) {
            while (start < events.size() && !events.get(start).eventId().equals(cursor)) start++;
            if (start == events.size()) throw fault(400, "UNKNOWN_CURSOR");
            start++;
        }
        var page = new ArrayList<Map<String, Object>>();
        int bytes = 256, index = start;
        for (; index < events.size() && page.size() < limit; index++) {
            var row = event(events.get(index));
            int length = WebJson.encode(sanitizer.sanitize(row)).length;
            if (bytes + length > WebJson.RESPONSE_LIMIT - 1024) break;
            bytes += length;
            page.add(row);
            cursor = events.get(index).eventId();
        }
        return Map.of("events", page, "next", cursor, "more", index < events.size());
    }

    private void stream(HttpExchange exchange, Task task, Map<String, String> query)
            throws IOException {
        var cursorQuery = new HashMap<>(query);
        var last = exchange.getRequestHeaders().getOrDefault("Last-Event-ID", List.of());
        if (last.size() > 1
                || !last.isEmpty()
                        && (cursorQuery.containsKey("after")
                                || !last.getFirst().matches("[A-Za-z0-9_-]{1,64}")))
            throw fault(400, "INVALID_CURSOR");
        if (!last.isEmpty()) cursorQuery.put("after", last.getFirst());
        var page = eventPage(task, cursorQuery); // Validate before committing HTTP headers.
        int limit = Integer.parseInt(cursorQuery.getOrDefault("limit", "50"));
        if (!streams.tryAcquire()) throw fault(429, "STREAM_LIMIT");
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Connection", "close");
            exchange.sendResponseHeaders(200, 0);
            long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            int count = 0, bytes = 0;
            while (System.nanoTime() < end && count < limit) {
                for (Object item : (List<?>) page.get("events")) {
                    var row = Values.object(item);
                    String id = (String) row.get("eventId");
                    byte[] data = WebJson.encode(sanitizer.sanitize(row));
                    byte[] frame =
                            ("id: "
                                            + id
                                            + "\nevent: session\ndata: "
                                            + new String(data, StandardCharsets.UTF_8)
                                            + "\n\n")
                                    .getBytes(StandardCharsets.UTF_8);
                    if (bytes + frame.length > WebJson.RESPONSE_LIMIT) return;
                    exchange.getResponseBody().write(frame);
                    exchange.getResponseBody().flush();
                    cursorQuery.put("after", id);
                    bytes += frame.length;
                    if (++count >= limit) return;
                }
                synchronized (this) {
                    if (closed
                            || (task.worker == null || !task.worker.isAlive())
                                    && !(Boolean) page.get("more")) break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
                cursorQuery.put("limit", Integer.toString(limit - count));
                page = eventPage(task, cursorQuery);
            }
        } finally {
            streams.release();
        }
    }

    private Map<String, Object> event(SessionEvent event) throws IOException {
        var row = new LinkedHashMap<String, Object>();
        row.put("eventId", event.eventId());
        row.put("sessionId", event.sessionId());
        row.put("type", event.type());
        row.put("timestamp", event.timestamp().toString());
        row.put("schemaVersion", event.schemaVersion());
        row.put("level", event.level().name());
        row.put("turnId", event.turnId());
        row.put("stepId", event.stepId());
        row.put("callId", event.callId());
        byte[] payload = WebJson.encode(sanitizer.sanitize(event.payload()));
        row.put(
                "payload",
                payload.length <= 65536
                        ? sanitizer.sanitize(event.payload())
                        : Map.of(
                                "omitted",
                                true,
                                "reason",
                                "EVENT_PAYLOAD_LIMIT",
                                "sha256",
                                hashBytes(payload)));
        return row;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return Map.of();
        if (raw.length() > 512) throw fault(400, "INVALID_QUERY");
        var result = new HashMap<String, String>();
        for (String item : raw.split("&", -1)) {
            var pair = item.split("=", -1);
            if (pair.length != 2
                    || !pair[0].matches("[a-z]+")
                    || !pair[1].matches("[A-Za-z0-9_-]{1,64}")
                    || result.putIfAbsent(pair[0], pair[1]) != null)
                throw fault(400, "INVALID_QUERY");
        }
        return result;
    }

    private static Map<String, Object> body(HttpExchange exchange, Set<String> keys)
            throws IOException {
        if (!exchange.getRequestHeaders()
                        .getOrDefault("Content-Type", List.of())
                        .equals(List.of("application/json"))
                || exchange.getRequestHeaders().containsKey("Transfer-Encoding"))
            throw fault(415, "JSON_REQUIRED");
        var lengths = exchange.getRequestHeaders().getOrDefault("Content-Length", List.of());
        int length;
        try {
            length = lengths.size() == 1 ? Integer.parseInt(lengths.getFirst()) : -1;
        } catch (NumberFormatException ex) {
            throw fault(400, "INVALID_LENGTH");
        }
        if (length < 2 || length > WebJson.REQUEST_LIMIT) throw fault(413, "BODY_LIMIT");
        var bytes = exchange.getRequestBody().readNBytes(length);
        if (bytes.length != length) throw fault(400, "INVALID_BODY");
        Map<String, Object> value;
        try {
            value = WebJson.decode(bytes);
        } catch (IOException | RuntimeException ex) {
            throw fault(400, "INVALID_JSON");
        }
        if (!value.keySet().equals(keys)) throw fault(400, "INVALID_BODY");
        return value;
    }

    private static String text(Map<String, Object> body, String key, int limit) {
        if (!(body.get(key) instanceof String text) || text.isBlank() || text.length() > limit)
            throw fault(400, "INVALID_BODY");
        return text;
    }

    private String clean(String value) {
        return (String) sanitizer.sanitize(Map.of("text", value)).get("text");
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> value)
            throws IOException {
        byte[] bytes = WebJson.encode(sanitizer.sanitize(value));
        if (bytes.length > WebJson.RESPONSE_LIMIT) {
            status = 413;
            bytes = WebJson.encode(Map.of("error", "RESPONSE_LIMIT"));
        }
        var headers = exchange.getResponseHeaders();
        headers.set("Connection", "close");
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        if (status == 401) headers.set("WWW-Authenticate", "Bearer");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String hash(String text) {
        return HexFormat.of().formatHex(digest(text));
    }

    private static String hashBytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static WebFault fault(int status, String code) {
        return new WebFault(status, code);
    }

    private static final class WebFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;
        final String code;

        WebFault(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }
    }

    private static final class Pending {
        final String callId;
        final Map<String, Object> view;
        final CompletableFuture<ApprovalService.ApprovalDecision> decision =
                new CompletableFuture<>();

        Pending(String callId, Map<String, Object> view) {
            this.callId = callId;
            this.view = view;
        }
    }

    private static final class Task {
        final String id, profile, requestHash, goalHash, binding;
        final Map<String, Pending> approvals = new LinkedHashMap<>();
        String phase = "STARTING", status = "CREATED", error = "";
        volatile boolean cancelled;
        boolean recovered;
        HarnessSdk sdk;
        Thread worker;

        Task(String id, String profile, String requestHash, String goalHash, String binding) {
            this.id = id;
            this.profile = profile;
            this.requestHash = requestHash;
            this.goalHash = goalHash;
            this.binding = binding;
        }
    }

    /**
     * Offline operator fact only. Stops neither processes nor threads and never replays requests.
     */
    public static void verifyTask(
            SessionRepository catalogRepository,
            String catalogId,
            SessionRepository sessions,
            String taskId,
            String workspaceBinding,
            String reason,
            DataSanitizer sanitizer) {
        if (reason == null || reason.isBlank() || reason.length() > 4096)
            throw new HarnessException(
                    "VERIFICATION_REASON_REQUIRED", "Provide externally checked facts");
        try (var writer =
                new SanitizingSessionWriter(
                        catalogRepository.openWriter(catalogId, WriteOwnership.EXCLUSIVE),
                        sanitizer)) {
            var events = catalogRepository.openReader(catalogId).events();
            var accepted =
                    events.stream()
                            .filter(
                                    e ->
                                            e.type().equals("web.task.accepted")
                                                    && taskId.equals(e.payload().get("taskId")))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new HarnessException(
                                                    "TASK_NOT_FOUND", "Unknown Web task"));
            if (!workspaceBinding.equals(accepted.payload().get("workspaceBinding")))
                throw new HarnessException("ENVIRONMENT_MISMATCH", "Wrong workspace binding");
            var last =
                    events.stream()
                            .filter(e -> taskId.equals(e.payload().get("taskId")))
                            .reduce((a, b) -> b)
                            .orElseThrow();
            if (last.type().equals("web.task.verified")
                    || last.type().equals("web.task.finished")
                            && !"UNKNOWN".equals(last.payload().get("phase")))
                throw new HarnessException("VERIFICATION_NOT_REQUIRED", "Task already settled");
            if (sessions.list().contains(taskId)) {
                try (var child = sessions.openWriter(taskId, WriteOwnership.EXCLUSIVE)) {
                    var history = sessions.openReader(taskId).events();
                    if (history.stream()
                            .filter(e -> e.type().equals("sdk.created"))
                            .anyMatch(
                                    e ->
                                            !workspaceBinding.equals(
                                                    Values.object(
                                                                    e.payload()
                                                                            .getOrDefault(
                                                                                    "binding",
                                                                                    Map.of()))
                                                            .get("workspaceBinding"))))
                        throw new HarnessException(
                                "ENVIRONMENT_MISMATCH", "Wrong underlying SDK binding");
                    if (!SessionRecovery.inspect(history).ready())
                        throw new HarnessException(
                                "UNKNOWN_SIDE_EFFECT",
                                "First verify underlying SDK requests/resources");
                    child.flush();
                }
            }
            writer.append(
                    SessionEvent.session(
                            catalogId,
                            "web.task.verified",
                            Map.of(
                                    "taskId",
                                    taskId,
                                    "reason",
                                    reason,
                                    "actor",
                                    "operator",
                                    "resolution",
                                    "external inspection; no replay or completion claim")));
            writer.flush();
        }
    }

    @Override
    public void close() {
        List<Thread> workers;
        synchronized (this) {
            if (closed) return;
            closed = true;
            tasks.values().forEach(this::cancel);
            workers = tasks.values().stream().map(t -> t.worker).filter(Objects::nonNull).toList();
        }
        http.stop(0);
        requests.shutdownNow();
        deadlines.shutdownNow();
        long end = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        for (var worker : workers) {
            try {
                long left = end - System.nanoTime();
                if (left > 0) worker.join(Math.max(1, left / 1000000));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (worker.isAlive()) worker.interrupt();
        }
        synchronized (this) {
            catalog.close();
        }
    }
}
