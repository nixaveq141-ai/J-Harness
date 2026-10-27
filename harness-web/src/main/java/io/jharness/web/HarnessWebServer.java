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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local single-operator HTTP adapter. All execution capabilities come from trusted profiles. */
public final class HarnessWebServer implements AutoCloseable {
    /** Trusted operator settings; mutations are serialized with task admission. */
    public interface Settings {
        Map<String, Object> read() throws IOException;

        Map<String, Object> save(Map<String, Object> input) throws IOException;

        default void prepareTask() throws IOException {}

        default Map<String, Object> skills(String workspace) throws IOException {
            return Map.of("skills", List.of(), "snapshot", true);
        }

        default Map<String, Object> capture(String modelId) throws IOException {
            prepareTask();
            if (!modelId.isEmpty())
                throw new HarnessException("UNKNOWN_MODEL", "Model selection unavailable");
            return Map.of();
        }

        default void prepareSelection(Map<String, Object> selection) throws IOException {}

        default Map<String, Object> check(String modelId) throws IOException {
            capture(modelId);
            return Map.of("valid", true, "networkChecked", false, "requestSent", false);
        }
    }

    private Settings settings;

    public interface Workspaces {
        Map<String, Object> read();

        Map.Entry<String, Profile> register(String path, String name) throws IOException;
    }

    @FunctionalInterface
    public interface ForkArtifacts {
        void copy(String source, String target) throws IOException;

        default void rollback(String target) throws IOException {}

        static ForkArtifacts local(LocalArtifactStore store) {
            return new ForkArtifacts() {
                public void copy(String source, String target) throws IOException {
                    store.copyScope(source, target);
                }

                public void rollback(String target) throws IOException {
                    store.rollbackCopy(target);
                }
            };
        }
    }

    private Workspaces workspaces;

    public synchronized void workspaces(Workspaces value) {
        workspaces = Objects.requireNonNull(value);
    }

    public synchronized void settings(Settings value) {
        if (settings != null) throw new IllegalStateException("Settings already attached");
        settings = Objects.requireNonNull(value);
    }

    @FunctionalInterface
    public interface SdkFactory {
        HarnessSdk open(String sessionId, ApprovalService approvals, DataSanitizer sanitizer)
                throws Exception;

        default HarnessSdk open(
                String sessionId,
                ApprovalService approvals,
                DataSanitizer sanitizer,
                Map<String, Object> selection)
                throws Exception {
            return open(sessionId, approvals, sanitizer);
        }
    }

    public record Profile(
            String workspaceBinding,
            SessionRepository sessions,
            SdkFactory factory,
            ForkArtifacts artifacts,
            ContentInspection content,
            ContentSearchProvider search) {
        public Profile(
                String binding,
                SessionRepository sessions,
                SdkFactory factory,
                ForkArtifacts artifacts,
                ContentInspection content) {
            this(binding, sessions, factory, artifacts, content, null);
        }

        public Profile(String workspaceBinding, SessionRepository sessions, SdkFactory factory) {
            this(workspaceBinding, sessions, factory, null, null, null);
        }

        public Profile(
                String binding,
                SessionRepository sessions,
                SdkFactory factory,
                ForkArtifacts artifacts) {
            this(binding, sessions, factory, artifacts, null, null);
        }

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
    private final DurableTaskCatalog diskCatalog;
    private final Map<String, WorkspaceLease> leases = new HashMap<>();
    private final Map<String, Cursor> cursors = new LinkedHashMap<>();
    private final WorkbenchContentSearch contentSearch = new WorkbenchContentSearch();
    private final String catalogId, catalogBinding;
    private final byte[] credential;
    private final String browserToken = UUID.randomUUID().toString() + UUID.randomUUID();
    private final DataSanitizer sanitizer;
    private final Duration approvalTimeout;
    private final int maxActive, maxTasks;
    private boolean resourcesClosed;
    private boolean settingsChanging;
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
        this.profiles = new java.util.concurrent.ConcurrentHashMap<>(profiles);
        this.catalogId = Values.required(catalogId, "catalog id");
        catalogBinding =
                hash(
                        catalogRepository instanceof JsonlSessionRepository physical
                                ? physical.directory().resolve(catalogId).toString()
                                : catalogId);
        this.maxActive = maxActive;
        this.maxTasks = maxTasks;
        this.approvalTimeout = approvalTimeout;
        credential = digest(bearerToken);
        var redactor = new SecretRedactor(List.of(bearerToken, browserToken));
        this.sanitizer = DataSanitizer.chain(sanitizer, redactor);
        var owner = catalogRepository.openWriter(catalogId, WriteOwnership.EXCLUSIVE);
        try {
            diskCatalog =
                    catalogRepository instanceof JsonlSessionRepository physical
                            ? new DurableTaskCatalog(physical, catalogId, owner, this.sanitizer)
                            : null;
            catalog =
                    diskCatalog == null
                            ? new SanitizingSessionWriter(owner, this.sanitizer)
                            : diskCatalog;
        } catch (IOException | RuntimeException ex) {
            owner.close();
            throw ex;
        }
        HttpServer server = null;
        try {
            if (!catalog.durable())
                throw new IllegalArgumentException("Durable Web catalog required");
            if (diskCatalog == null) restore(catalogRepository.openReader(catalogId).events());
            else
                diskCatalog.scan(
                        history -> {
                            var task = parseTask(history);
                            if (task.phase.equals("UNKNOWN")) {
                                cache(task);
                                occupied.add(task.binding);
                            }
                        });
            for (var profile : profiles.values()) attachLease(profile);
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
            leases.values().forEach(WorkspaceLease::close);
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
        var grouped = new LinkedHashMap<String, List<SessionEvent>>();
        for (var event : events) {
            if (!(event.payload().get("taskId") instanceof String id))
                throw new HarnessException("INVALID_WEB_CATALOG", "Missing task identity");
            grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(event);
        }
        for (var history : grouped.values()) {
            var task = parseTask(history);
            if (tasks.putIfAbsent(task.id, task) != null
                    || submissions.putIfAbsent(task.requestHash, task) != null)
                throw new HarnessException("INVALID_WEB_CATALOG", "Duplicate task identity");
        }
        if (tasks.size() > maxTasks)
            throw new IllegalArgumentException("Catalog exceeds task bound");
        // Unknown ownership must be externally inspected before this binding can be used again.
        tasks.values().stream()
                .filter(t -> t.phase.equals("UNKNOWN"))
                .forEach(t -> occupied.add(t.binding));
    }

    private Task parseTask(List<SessionEvent> history) {
        Task task = null;
        for (var event : history) {
            DurableTaskCatalog.validate(event);
            var p = event.payload();
            if (event.type().equals("web.task.accepted")) {
                if (task != null) throw new IllegalArgumentException("Repeated admission");
                task =
                        new Task(
                                (String) p.get("taskId"),
                                (String) p.get("profile"),
                                (String) p.get("requestHash"),
                                (String) p.get("goalHash"),
                                (String) p.get("workspaceBinding"));
                task.selection = Values.object(p.getOrDefault("selection", Map.of()));
                task.phase = "UNKNOWN";
                task.recovered = true;
                task.createdAt = event.timestamp().toString();
            } else {
                if (task == null || !task.id.equals(p.get("taskId")))
                    throw new IllegalArgumentException("Invalid task catalog");
                switch (event.type()) {
                    case "web.task.finished" -> {
                        task.phase = (String) p.get("phase");
                        task.status = (String) p.get("status");
                        task.error = (String) p.get("error");
                        task.cancelled = Boolean.TRUE.equals(p.get("cancelRequested"));
                    }
                    case "web.task.verified" -> {
                        task.turns.verified();
                        task.phase = "VERIFIED";
                        task.error = "EXTERNALLY_VERIFIED_NO_REPLAY";
                    }
                    case "web.task.changed" -> {
                        task.name = (String) p.get("name");
                        task.pinned = Boolean.TRUE.equals(p.get("pinned"));
                        task.archived = Boolean.TRUE.equals(p.get("archived"));
                    }
                    default -> {
                        if (!event.type().startsWith("web.turn."))
                            throw new IllegalArgumentException("Unknown task fact");
                        task.turns.apply(event);
                        if (event.type().equals("web.turn.started"))
                            task.executionSelection =
                                    task.turns.inputs.get(p.get("requestHash")).selection;
                        if (Set.of("web.turn.started", "web.turn.interrupted")
                                .contains(event.type())) task.phase = "UNKNOWN";
                    }
                }
            }
            if (event.type().equals("web.task.accepted") || event.type().equals("web.task.changed"))
                task.revision = event.eventId();
        }
        Profile registered;
        synchronized (this) {
            registered = task == null ? null : profiles.get(task.profile);
        }
        if (task == null
                || registered == null
                || !registered.workspaceBinding().equals(task.binding))
            throw new IllegalArgumentException("Catalog/profile mismatch");
        if (task.turns.inputs.values().stream().anyMatch(i -> i.state.equals("UNKNOWN")))
            task.phase = "UNKNOWN";
        reconcileArchival(task);
        return task;
    }

    private void cache(Task task) {
        tasks.remove(task.id);
        tasks.put(task.id, task);
        submissions.put(task.requestHash, task);
        while (tasks.size() > maxTasks) {
            var evicted =
                    tasks.values().stream()
                            .filter(
                                    t ->
                                            !t.phase.equals("UNKNOWN")
                                                    && t.sdk == null
                                                    && (t.phase.equals("FINISHED")
                                                            || t.phase.equals("VERIFIED")))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalStateException("Active hot set exhausted"));
            tasks.remove(evicted.id);
            submissions.remove(evicted.requestHash);
        }
    }

    private Task find(String id) {
        var task = tasks.get(id);
        if (task == null && diskCatalog != null) {
            var history = diskCatalog.byId(id);
            if (!history.isEmpty()) {
                task = parseTask(history);
                cache(task);
            }
        }
        return task;
    }

    private Task submission(String hash) {
        var task = submissions.get(hash);
        if (task == null && diskCatalog != null) {
            var history = diskCatalog.byHash(hash);
            if (!history.isEmpty()) {
                task = find((String) history.getFirst().payload().get("taskId"));
            }
        }
        return task;
    }

    private void attachLease(Profile profile) throws IOException {
        if (!(profile.sessions() instanceof JsonlSessionRepository physical)
                || leases.containsKey(profile.workspaceBinding())) return;
        var lease = new WorkspaceLease(physical);
        leases.put(profile.workspaceBinding(), lease);
        var pending = lease.pending();
        if (!pending.isEmpty()) {
            occupied.add(profile.workspaceBinding());
            if (catalogBinding.equals(pending.get("catalogKey"))) {
                var task = find((String) pending.get("taskId"));
                if (task == null && diskCatalog != null) {
                    var accepted = new LinkedHashMap<>(pending);
                    accepted.remove("catalogKey");
                    catalog.append(SessionEvent.session(catalogId, "web.task.accepted", accepted));
                    catalog.flush();
                    task = find((String) pending.get("taskId"));
                }
                if (task != null && pending.get("turnHash") instanceof String turnHash) {
                    var input = task.turns.inputs.get(turnHash);
                    if (input == null) {
                        var interrupted =
                                Map.<String, Object>of(
                                        "taskId",
                                        task.id,
                                        "requestHash",
                                        turnHash,
                                        "goalHash",
                                        pending.get("turnGoalHash"),
                                        "kind",
                                        pending.get("turnKind"),
                                        "selection",
                                        pending.getOrDefault("turnSelection", Map.of()));
                        var fact =
                                SessionEvent.session(
                                        catalogId, "web.turn.interrupted", interrupted);
                        catalog.append(fact);
                        catalog.flush();
                        task.turns.apply(fact);
                        task.phase = "UNKNOWN";
                    } else if (TurnQueue.PENDING.contains(input.state)) {
                        input.state = "UNKNOWN";
                        task.phase = "UNKNOWN";
                        // Admission exists but start may have failed after the workspace pointer.
                        var fact =
                                SessionEvent.session(
                                        catalogId,
                                        "web.turn.started",
                                        Map.of("taskId", task.id, "requestHash", turnHash));
                        catalog.append(fact);
                        catalog.flush();
                    }
                }
                if (task != null
                        && (task.phase.equals("FINISHED") || task.phase.equals("VERIFIED"))
                        && SessionRecovery.inspect(events(task)).ready()) {
                    lease.clear(task.id, catalogBinding);
                    occupied.remove(task.binding);
                }
            }
        } else {
            var uncertain =
                    tasks.values().stream()
                            .filter(
                                    t ->
                                            t.binding.equals(profile.workspaceBinding())
                                                    && t.phase.equals("UNKNOWN"))
                            .findFirst();
            if (uncertain.isPresent()) lease.accept(accepted(uncertain.get()), catalogBinding);
        }
    }

    private Map<String, Object> accepted(Task task) {
        return Map.of(
                "taskId",
                task.id,
                "profile",
                task.profile,
                "requestHash",
                task.requestHash,
                "goalHash",
                task.goalHash,
                "workspaceBinding",
                task.binding,
                "selection",
                task.selection);
    }

    private record Cursor(
            boolean pinned, String createdAt, String id, String profile, String view) {}

    private static int after(Task task, Cursor cursor) {
        int result = Boolean.compare(!task.pinned, !cursor.pinned);
        if (result == 0)
            result = Instant.parse(cursor.createdAt).compareTo(Instant.parse(task.createdAt));
        return result == 0 ? task.id.compareTo(cursor.id) : result;
    }

    private Map<String, Object> taskPage(Map<String, String> query) {
        if (!Set.of("profile", "view", "after", "limit").containsAll(query.keySet()))
            throw fault(400, "INVALID_QUERY");
        Map<String, Task> hot;
        Map<String, Profile> allowed;
        int hotCount;
        synchronized (this) {
            allowed = Map.copyOf(profiles);
            hot = new LinkedHashMap<>();
            tasks.forEach((id, task) -> hot.put(id, pageSnapshot(task)));
            hotCount = tasks.size();
        }
        String profile = query.getOrDefault("profile", ""),
                view = query.getOrDefault("view", "active");
        if (!profile.isEmpty() && !allowed.containsKey(profile)
                || !Set.of("active", "archived", "all").contains(view))
            throw fault(400, "INVALID_QUERY");
        int limit;
        try {
            limit = Integer.parseInt(query.getOrDefault("limit", "32"));
        } catch (NumberFormatException ex) {
            throw fault(400, "INVALID_QUERY");
        }
        if (limit < 1 || limit > 32) throw fault(400, "INVALID_QUERY");
        Cursor cursor = null;
        if (query.containsKey("after")) {
            synchronized (this) {
                cursor = cursors.get(query.get("after"));
            }
            if (cursor == null || !cursor.profile.equals(profile) || !cursor.view.equals(view))
                throw fault(400, "UNKNOWN_CURSOR");
        }
        var order =
                Comparator.comparing((Task t) -> !t.pinned)
                        .thenComparing(t -> Instant.parse(t.createdAt), Comparator.reverseOrder())
                        .thenComparing(t -> t.id);
        var queue = new PriorityQueue<Task>(limit + 2, order.reversed());
        Cursor boundary = cursor;
        Consumer<Task> receive =
                task -> {
                    reconcileArchival(task);
                    if (!profile.isEmpty()
                                    && !task.binding.equals(allowed.get(profile).workspaceBinding())
                            || view.equals("active") && task.archived
                            || view.equals("archived") && !task.archived
                            || boundary != null && after(task, boundary) <= 0) return;
                    queue.add(task);
                    if (queue.size() > limit + 1) queue.remove();
                };
        if (diskCatalog == null) hot.values().forEach(receive);
        else
            diskCatalog.scanSnapshot(
                    history -> {
                        var t = parseTask(history);
                        receive.accept(hot.getOrDefault(t.id, t));
                    });
        var page = new ArrayList<>(queue);
        page.sort(order);
        boolean more = page.size() > limit;
        if (more) page.removeLast();
        String next = "";
        if (more) {
            next = UUID.randomUUID().toString();
            synchronized (this) {
                cursors.put(
                        next,
                        new Cursor(
                                page.getLast().pinned,
                                page.getLast().createdAt,
                                page.getLast().id,
                                profile,
                                view));
                if (cursors.size() > 32) cursors.remove(cursors.keySet().iterator().next());
            }
        }
        return Map.of(
                "tasks",
                page.stream().map(this::summary).toList(),
                "next",
                next,
                "more",
                more,
                "hotCount",
                hotCount,
                "hotLimit",
                maxTasks,
                "historyCount",
                diskCatalog == null ? hotCount : diskCatalog.count());
    }

    private Task pageSnapshot(Task task) {
        var copy = new Task(task.id, task.profile, task.requestHash, task.goalHash, task.binding);
        copy.selection = task.selection;
        copy.executionSelection = task.executionSelection;
        copy.phase = task.phase;
        copy.status = task.status;
        copy.error = task.error;
        copy.name = task.name;
        copy.createdAt = task.createdAt;
        copy.revision = task.revision;
        copy.pinned = task.pinned;
        copy.archived = task.archived;
        copy.cancelled = task.cancelled;
        copy.recovered = task.recovered;
        copy.pendingSnapshot = task.turns.pending();
        return copy;
    }

    private void handle(HttpExchange exchange) {
        var deadline =
                deadlines.schedule(
                        exchange::close,
                        exchange.getRequestURI().getPath().equals("/v1/settings") ? 40 : 10,
                        TimeUnit.SECONDS);
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
                if (exchange.getRequestURI().getPath().equals("/v1/browser-session")) {
                    if (!exchange.getRequestMethod().equals("POST")
                            || exchange.getRequestURI().getRawQuery() != null
                            || !exchange.getRequestHeaders()
                                    .getOrDefault("Origin", List.of())
                                    .equals(List.of(address().toString()))
                            || !exchange.getRequestHeaders()
                                    .getOrDefault("Sec-Fetch-Site", List.of())
                                    .equals(List.of("same-origin")))
                        throw fault(403, "LOCAL_CLIENT_REQUIRED");
                    body(exchange, Set.of());
                    // This response is intentionally not passed through the event/output redactor.
                    byte[] bytes = WebJson.encode(Map.of("token", browserToken));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
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
                && (MessageDigest.isEqual(credential, digest(headers.getFirst().substring(7)))
                        || MessageDigest.isEqual(
                                digest(browserToken), digest(headers.getFirst().substring(7))));
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
        if (Arrays.equals(path, new String[] {"", "v1", "search"})) {
            if (!method.equals("POST") || !query.isEmpty()) throw fault(405, "METHOD_NOT_ALLOWED");
            var input =
                    body(
                            exchange,
                            Set.of(
                                    "text",
                                    "mode",
                                    "profile",
                                    "view",
                                    "sessionId",
                                    "cursor",
                                    "limit",
                                    "rebuild"));
            try {
                List<Map<String, Object>> scope = new ArrayList<>();
                Map<String, Profile> allowed;
                synchronized (this) {
                    allowed = Map.copyOf(profiles);
                    Consumer<Task> receive =
                            task ->
                                    scope.add(
                                            Map.of(
                                                    "sessionId",
                                                    task.id,
                                                    "profile",
                                                    task.profile,
                                                    "name",
                                                    task.name,
                                                    "archived",
                                                    task.archived));
                    if (diskCatalog == null) tasks.values().forEach(receive);
                }
                if (diskCatalog != null)
                    diskCatalog.scanSnapshot(
                            history -> {
                                var task = parseTask(history);
                                scope.add(
                                        Map.of(
                                                "sessionId",
                                                task.id,
                                                "profile",
                                                task.profile,
                                                "name",
                                                task.name,
                                                "archived",
                                                task.archived));
                            });
                respond(exchange, 200, contentSearch.search(input, scope, allowed));
            } catch (HarnessException ex) {
                throw fault(ex.code().equals("SEARCH_CURSOR_EXPIRED") ? 409 : 400, ex.code());
            }
            return;
        }
        if (Arrays.equals(path, new String[] {"", "v1", "skills"})) {
            if (!method.equals("GET")) throw fault(405, "METHOD_NOT_ALLOWED");
            if (!query.keySet().equals(Set.of("profile"))) throw fault(400, "INVALID_QUERY");
            Profile profile;
            Settings current;
            synchronized (this) {
                profile = profiles.get(query.get("profile"));
                current = settings;
                if (current == null || profile == null) throw fault(404, "NOT_FOUND");
            }
            try {
                respond(exchange, 200, current.skills(profile.workspaceBinding()));
            } catch (HarnessException ex) {
                throw fault(400, ex.code());
            } catch (IOException | IllegalArgumentException ex) {
                throw fault(400, "SKILL_SCAN_FAILED");
            }
            return;
        }
        if (Arrays.equals(path, new String[] {"", "v1", "settings"}) && query.isEmpty()) {
            Settings current;
            synchronized (this) {
                current = settings;
                if (current == null) throw fault(404, "NOT_FOUND");
            }
            if (method.equals("GET")) respond(exchange, 200, current.read());
            else if (method.equals("POST")) {
                var input =
                        body(
                                exchange,
                                Set.of("revision", "models", "selected", "extensions", "apiKeys"),
                                Set.of("policy"));
                synchronized (this) {
                    if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
                    if (!occupied.isEmpty() || settingsChanging) throw fault(409, "WORKSPACE_BUSY");
                    settingsChanging = true;
                }
                boolean mutationReleased = false;
                try {
                    var response = prepareResponse(200, current.save(input));
                    synchronized (this) {
                        settingsChanging = false;
                        mutationReleased = true;
                        notifyAll();
                    }
                    respond(exchange, response);
                } catch (HarnessException ex) {
                    throw fault(ex.code().equals("SETTINGS_CONFLICT") ? 409 : 400, ex.code());
                } catch (IllegalArgumentException ex) {
                    throw fault(400, "INVALID_SETTINGS");
                } finally {
                    if (!mutationReleased) {
                        synchronized (this) {
                            settingsChanging = false;
                            notifyAll();
                        }
                    }
                }
            } else throw fault(405, "METHOD_NOT_ALLOWED");
            return;
        }
        if (method.equals("GET") && query.isEmpty()) {
            if (Arrays.equals(path, new String[] {"", "v1", "info"})) {
                respond(
                        exchange,
                        200,
                        Map.of(
                                "profiles",
                                profileIds(),
                                "protocolVersion",
                                HarnessSdk.PROTOCOL_VERSION));
                return;
            }
            if (path.length == 4 && path[1].equals("v1") && path[2].equals("submissions")) {
                if (!path[3].matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
                synchronized (this) {
                    Task task = submission(hash(path[3]));
                    if (task == null) throw fault(404, "SUBMISSION_NOT_FOUND");
                    respond(exchange, 200, summary(task));
                }
                return;
            }
        }
        if (Arrays.equals(path, new String[] {"", "v1", "workspaces"}) && query.isEmpty()) {
            if (method.equals("GET")) {
                Map<String, Object> out;
                synchronized (this) {
                    out = workspaceInfo();
                }
                respond(exchange, 200, out);
                return;
            }
            if (!method.equals("POST")) throw fault(405, "METHOD_NOT_ALLOWED");
            var input = body(exchange, Set.of("path", "name"));
            Workspaces registry;
            synchronized (this) {
                registry = workspaces;
                if (registry == null) throw fault(405, "METHOD_NOT_ALLOWED");
                if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
                if (profiles.size() >= 16) throw fault(409, "WORKSPACE_LIMIT");
                if (settingsChanging) throw fault(409, "WORKSPACE_BUSY");
                settingsChanging = true;
            }
            boolean mutationReleased = false;
            try {
                var registered =
                        registry.register(
                                text(input, "path", 1024), clean(text(input, "name", 80)));
                PreparedResponse out;
                synchronized (this) {
                    if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
                    try {
                        attachLease(registered.getValue());
                    } catch (IOException | HarnessException unavailable) {
                        occupied.add(registered.getValue().workspaceBinding());
                    }
                    profiles.put(registered.getKey(), registered.getValue());
                    out = prepareResponse(200, workspaceInfo());
                    settingsChanging = false;
                    mutationReleased = true;
                    notifyAll();
                }
                respond(exchange, out);
            } catch (HarnessException ex) {
                throw fault(409, ex.code());
            } catch (IOException ex) {
                throw fault(400, "WORKSPACE_PATH_DENIED");
            } finally {
                if (!mutationReleased) {
                    synchronized (this) {
                        settingsChanging = false;
                        notifyAll();
                    }
                }
            }
            return;
        }
        if (path.length >= 3 && path[1].equals("v1") && path[2].equals("models")) {
            if (settings == null || !query.isEmpty()) throw fault(404, "NOT_FOUND");
            if (path.length == 3 && method.equals("GET")) {
                var state = settings.read();
                respond(
                        exchange,
                        200,
                        Map.of(
                                "models",
                                state.get("models"),
                                "selected",
                                state.get("selected"),
                                "revision",
                                state.get("revision")));
                return;
            }
            if (path.length == 4 && path[3].equals("check") && method.equals("POST")) {
                var input = body(exchange, Set.of("modelId"));
                try {
                    respond(exchange, 200, settings.check(text(input, "modelId", 32)));
                } catch (HarnessException ex) {
                    throw fault(400, ex.code());
                }
                return;
            }
            throw fault(405, "METHOD_NOT_ALLOWED");
        }
        if (path.length < 3 || !path[1].equals("v1") || !path[2].equals("tasks"))
            throw fault(404, "NOT_FOUND");
        if (path.length == 3) {
            if (method.equals("POST") && query.isEmpty()) {
                var body =
                        body(exchange, Set.of("profile", "goal", "requestId"), Set.of("modelId"));
                respond(
                        exchange,
                        202,
                        submit(
                                text(body, "profile", 32),
                                text(body, "goal", 16384),
                                text(body, "requestId", 64),
                                optionalModel(body)));
                return;
            }
            if (method.equals("GET")) {
                Map<String, Object> response;
                response = taskPage(query);
                respond(exchange, 200, response);
                return;
            }
        }
        if (path.length < 4) throw fault(405, "METHOD_NOT_ALLOWED");
        Task task;
        synchronized (this) {
            task = find(path[3]);
            if (task == null) throw fault(404, "NOT_FOUND");
        }
        if (path.length == 4 && method.equals("GET") && query.isEmpty()) {
            Map<String, Object> response;
            synchronized (this) {
                response = summary(task);
            }
            respond(exchange, 200, response);
        } else if (path.length >= 5
                && Set.of("files", "file", "artifacts", "content", "display").contains(path[4])) {
            contentRoute(exchange, task, path, method, query);
        } else if (path.length >= 5 && path[4].equals("questions")) {
            questionRoute(exchange, task, path, method, query);
        } else if (path.length >= 5 && path[4].equals("turns")) {
            synchronized (this) {
                turnRoute(exchange, task, path, method, query);
            }
        } else if (path.length == 5
                && path[4].equals("metadata")
                && method.equals("POST")
                && query.isEmpty()) {
            var input = body(exchange, Set.of("revision", "name", "pinned", "archived"));
            synchronized (this) {
                if (!task.revision.equals(input.get("revision")))
                    throw fault(409, "SESSION_CONFLICT");
                if (!(input.get("pinned") instanceof Boolean pinned)
                        || !(input.get("archived") instanceof Boolean archived))
                    throw fault(400, "INVALID_BODY");
                String name = clean(text(input, "name", 80));
                if (archived != task.archived) {
                    maintenanceReady(task);
                    SessionLifecycle.setArchived(
                            profiles.get(task.profile).sessions(), task.id, archived, sanitizer);
                }
                var change =
                        SessionEvent.session(
                                catalogId,
                                "web.task.changed",
                                Map.of(
                                        "taskId",
                                        task.id,
                                        "name",
                                        name,
                                        "pinned",
                                        pinned,
                                        "archived",
                                        archived));
                try {
                    task.revision = catalog.append(change);
                    catalog.flush();
                } catch (RuntimeException ex) {
                    broken = true;
                    throw fault(503, "SUBMISSION_NOT_CONFIRMED");
                }
                task.name = name;
                task.pinned = pinned;
                task.archived = archived;
                cursors.clear();
                respond(exchange, 200, summary(task));
            }
        } else if (path.length == 5
                && path[4].equals("fork")
                && method.equals("POST")
                && query.isEmpty()) {
            var input = body(exchange, Set.of("eventId", "requestId"));
            synchronized (this) {
                respond(
                        exchange,
                        202,
                        forkTask(task, text(input, "eventId", 64), text(input, "requestId", 64)));
            }
        } else if (path.length == 5
                && path[4].equals("export")
                && method.equals("GET")
                && query.isEmpty()) {
            synchronized (this) {
                maintenanceReady(task);
                var repository = profiles.get(task.profile).sessions();
                try (var writer = repository.openWriter(task.id, WriteOwnership.EXCLUSIVE)) {
                    var exportEvents = repository.openReader(task.id).events();
                    if (exportEvents.isEmpty()) throw fault(404, "SESSION_HISTORY_UNAVAILABLE");
                    var content = new ByteArrayOutputStream();
                    content.write(
                            WebJson.encode(
                                    Map.of(
                                            "format",
                                            "jharness-jsonl",
                                            "version",
                                            1,
                                            "sessionId",
                                            task.id)));
                    content.write('\n');
                    for (var e : exportEvents) {
                        var row = fullEvent(e);
                        content.write(WebJson.encode(row));
                        content.write('\n');
                        if (content.size() > 220000) throw fault(413, "EXPORT_LIMIT");
                    }
                    writer.flush();
                    respond(
                            exchange,
                            200,
                            Map.of(
                                    "sessionId",
                                    task.id,
                                    "content",
                                    content.toString(StandardCharsets.UTF_8),
                                    "sha256",
                                    hashBytes(content.toByteArray()),
                                    "artifactsIncluded",
                                    false));
                }
            }
        } else if (path.length == 5
                && path[4].equals("diagnosis")
                && method.equals("GET")
                && query.isEmpty()) {
            var diagnosis = new LinkedHashMap<>(TaskDiagnosis.inspect(events(task), sanitizer));
            synchronized (this) {
                diagnosis.put("task", summary(task));
                diagnosis.put(
                        "association",
                        Map.of(
                                "sessionId",
                                task.id,
                                "requestHash",
                                task.requestHash,
                                "workspaceBinding",
                                task.binding,
                                "catalogId",
                                catalogId));
            }
            respond(exchange, 200, diagnosis);
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
        } else if (path.length == 5
                && path[4].equals("tools")
                && method.equals("GET")
                && query.isEmpty()) {
            var recorded =
                    events(task).reversed().stream()
                            .filter(e -> e.type().equals("extensions.ready"))
                            .findFirst();
            respond(
                    exchange,
                    200,
                    Map.of(
                            "recorded",
                            recorded.isPresent(),
                            "eventId",
                            recorded.map(SessionEvent::eventId).orElse(""),
                            "tools",
                            recorded.map(e -> e.payload().get("tools")).orElse(List.of())));
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
                        || closed
                        || System.nanoTime() >= pending.deadline
                        || task.cancelled
                        || !callId.equals(pending.callId)) throw fault(409, "STALE_APPROVAL");
                if (!pending.decision.complete(
                        new ApprovalService.ApprovalDecision(approved, reason, "web-operator")))
                    throw fault(409, "STALE_APPROVAL");
            }
            respond(exchange, 200, Map.of("accepted", true));
        } else throw fault(405, "METHOD_OR_QUERY_NOT_ALLOWED");
    }

    private static int contentNumber(Map<String, String> query, String key, int fallback) {
        try {
            return Integer.parseInt(query.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException bad) {
            throw fault(400, "INVALID_QUERY");
        }
    }

    private static String contentPath(Map<String, String> query) {
        try {
            String value = query.getOrDefault("path", "Lg");
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value))
                throw new IllegalArgumentException();
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException bad) {
            throw fault(400, "INVALID_QUERY");
        }
    }

    private static void references(Object value, Set<String> ids) {
        if (value instanceof Map<?, ?> map)
            for (var entry : map.entrySet()) {
                Object child = entry.getValue();
                if (Set.of("artifactRef", "artifactId", "id").contains(entry.getKey())
                        && child instanceof String text
                        && text.matches("[0-9a-f-]{36}-[0-9a-f]{64}")) {
                    if (ids.size() < 64) ids.add(text);
                } else references(child, ids);
            }
        else if (value instanceof List<?> list) for (Object item : list) references(item, ids);
    }

    private Set<String> artifactReferences(Task task) {
        Set<String> ids = new LinkedHashSet<>();
        for (var e : events(task))
            if (Set.of(
                            "tool.completed",
                            "delivery.registered",
                            "artifact.created",
                            "terminal.snapshot",
                            "environment.closed")
                    .contains(e.type())) references(e.payload(), ids);
        return ids;
    }

    private void contentRoute(
            HttpExchange exchange,
            Task task,
            String[] path,
            String method,
            Map<String, String> query)
            throws IOException {
        if (!method.equals("GET")) throw fault(405, "METHOD_NOT_ALLOWED");
        if (!Set.of("path", "offset", "line", "version").containsAll(query.keySet()))
            throw fault(400, "INVALID_QUERY");
        try {
            var provider = profiles.get(task.profile).content();
            Map<String, Object> out;
            switch (path[4]) {
                case "display" -> {
                    if (path.length != 5 || !query.isEmpty()) throw fault(400, "INVALID_QUERY");
                    HarnessSdk sdk;
                    Map<String, Object> last;
                    synchronized (this) {
                        sdk = task.sdk;
                        last = task.display;
                    }
                    out = sdk == null ? last : sdk.display().snapshot();
                    if (out.isEmpty())
                        out =
                                Map.of(
                                        "sessionId",
                                        task.id,
                                        "state",
                                        "UNAVAILABLE",
                                        "source",
                                        "temporary-model-stream",
                                        "text",
                                        "");
                }
                case "files" -> {
                    if (path.length != 5 || !Set.of("path").containsAll(query.keySet()))
                        throw fault(400, "INVALID_QUERY");
                    if (provider == null) throw fault(404, "INSPECTION_UNAVAILABLE");
                    out = provider.list(contentPath(query), CancellationToken.NONE);
                }
                case "file" -> {
                    if (path.length != 5) throw fault(404, "NOT_FOUND");
                    if (provider == null) throw fault(404, "INSPECTION_UNAVAILABLE");
                    out =
                            provider.file(
                                    contentPath(query),
                                    contentNumber(query, "offset", 0),
                                    contentNumber(query, "line", 0),
                                    query.getOrDefault("version", ""),
                                    CancellationToken.NONE);
                }
                case "artifacts" -> {
                    var ids = artifactReferences(task);
                    if (path.length == 5 && query.isEmpty()) {
                        out =
                                Map.of(
                                        "sessionId",
                                        task.id,
                                        "artifacts",
                                        ids.stream()
                                                .map(
                                                        id ->
                                                                Map.of(
                                                                        "id",
                                                                        id,
                                                                        "sha256",
                                                                        id.substring(37)))
                                                .toList(),
                                        "limited",
                                        ids.size() == 64);
                    } else if (path.length == 6
                            && Set.of("offset", "version").containsAll(query.keySet())) {
                        if (!ids.contains(path[5])) throw fault(404, "ARTIFACT_NOT_FOUND");
                        if (provider == null) throw fault(404, "INSPECTION_UNAVAILABLE");
                        out =
                                provider.artifact(
                                        task.id,
                                        path[5],
                                        contentNumber(query, "offset", 0),
                                        query.getOrDefault("version", ""),
                                        CancellationToken.NONE);
                    } else throw fault(400, "INVALID_QUERY");
                }
                case "content" -> {
                    if (path.length != 6
                            || !Set.of("offset", "version").containsAll(query.keySet()))
                        throw fault(400, "INVALID_QUERY");
                    var e =
                            events(task).stream()
                                    .filter(event -> event.eventId().equals(path[5]))
                                    .findFirst()
                                    .orElseThrow(() -> fault(404, "NOT_FOUND"));
                    var clean = sanitizer.sanitize(e.payload());
                    boolean message =
                            Set.of("user.message", "assistant.message").contains(e.type())
                                    && clean.get("content") instanceof String;
                    String text =
                            message
                                    ? (String) clean.get("content")
                                    : new String(WebJson.encode(clean), StandardCharsets.UTF_8);
                    out =
                            new LinkedHashMap<>(
                                    io.jharness.ecosystem.ContentPages.page(
                                            text,
                                            contentNumber(query, "offset", 0),
                                            query.getOrDefault("version", "")));
                    out.put("source", "recorded-event");
                    out.put("eventId", e.eventId());
                    out.put("sessionId", task.id);
                    out.put("format", message ? "text" : "json");
                }
                default -> throw fault(404, "NOT_FOUND");
            }
            respond(exchange, 200, out);
        } catch (HarnessException ex) {
            throw fault(
                    Set.of("FILE_CONFLICT", "CONTENT_CHANGED").contains(ex.code())
                            ? 409
                            : Set.of("ARTIFACT_NOT_FOUND", "FILE_NOT_FOUND").contains(ex.code())
                                    ? 404
                                    : 400,
                    ex.code());
        } catch (IllegalArgumentException ex) {
            throw fault(400, "INVALID_QUERY");
        }
    }

    private void questionRoute(
            HttpExchange exchange,
            Task task,
            String[] path,
            String method,
            Map<String, String> query)
            throws IOException {
        try {
            HarnessSdk sdk;
            synchronized (this) {
                sdk = task.sdk;
            }
            UserQuestionService service = null;
            if (sdk != null) {
                try {
                    service = sdk.questions();
                } catch (HarnessException | IllegalStateException closedService) {
                    /* Read durable facts below. */
                }
            }
            if (path.length == 5 && method.equals("GET")) {
                var rows =
                        service == null
                                ? io.jharness.ecosystem.UserQuestionsPlugin.project(events(task))
                                : service.list();
                if (!Set.of("offset").containsAll(query.keySet()))
                    throw fault(400, "INVALID_QUERY");
                int offset;
                try {
                    offset = Integer.parseInt(query.getOrDefault("offset", "0"));
                } catch (NumberFormatException bad) {
                    throw fault(400, "INVALID_QUERY");
                }
                if (offset < 0 || offset > rows.size()) throw fault(400, "INVALID_QUERY");
                int end = rows.size() - offset, begin = Math.max(0, end - 8);
                respond(
                        exchange,
                        200,
                        Map.of(
                                "questions",
                                rows.subList(begin, end),
                                "total",
                                rows.size(),
                                "offset",
                                offset,
                                "hasWaiting",
                                rows.stream()
                                        .anyMatch(
                                                q ->
                                                        Set.of("PENDING", "SUBMITTED")
                                                                .contains(q.get("state")))));

                return;
            }
            if (!query.isEmpty()) throw fault(400, "INVALID_QUERY");
            if (path.length == 7
                    && method.equals("POST")
                    && Set.of("answer", "draft").contains(path[6])) {
                var input =
                        body(
                                exchange,
                                path[6].equals("draft")
                                        ? Set.of(
                                                "questionId",
                                                "sessionId",
                                                "turnId",
                                                "callId",
                                                "selected",
                                                "text",
                                                "revision")
                                        : Set.of(
                                                "questionId",
                                                "sessionId",
                                                "turnId",
                                                "callId",
                                                "requestId",
                                                "selected",
                                                "text",
                                                "skip"));
                if (!task.id.equals(input.get("sessionId"))
                        || !path[5].equals(input.get("questionId")))
                    throw fault(409, "QUESTION_ASSOCIATION_MISMATCH");
                if (path[6].equals("draft")) {
                    if (service == null) throw fault(409, "QUESTION_CLOSED");
                    respond(exchange, 200, service.draft(input));
                    return;
                }
                var receipt =
                        service == null
                                ? io.jharness.ecosystem.UserQuestionsPlugin.recorded(
                                        events(task), input, sanitizer)
                                : service.respond(input);
                respond(exchange, "SUBMITTED".equals(receipt.get("state")) ? 202 : 200, receipt);
                return;
            }
            throw fault(405, "METHOD_NOT_ALLOWED");
        } catch (HarnessException ex) {
            throw fault(
                    Set.of("INVALID_ANSWER", "INVALID_QUESTION").contains(ex.code())
                            ? 400
                            : ex.code().equals("QUESTION_NOT_FOUND") ? 404 : 409,
                    ex.code());
        }
    }

    private synchronized List<String> profileIds() {
        return profiles.keySet().stream().sorted().toList();
    }

    private synchronized Map<String, Object> submit(
            String profile, String goal, String requestId, String modelId) {
        if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
        if (settingsChanging) throw fault(409, "WORKSPACE_BUSY");
        if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
        String requestHash = hash(requestId), goalHash = hash(goal);
        var prior = submission(requestHash);
        if (prior != null) {
            if (!prior.profile.equals(profile)
                    || !prior.goalHash.equals(goalHash)
                    || !modelId.isEmpty() && !modelId.equals(prior.selection.get("modelRoute")))
                throw fault(409, "REQUEST_ID_CONFLICT");
            return summary(prior);
        }
        var selected = profiles.get(profile);
        if (selected == null) throw fault(400, "UNKNOWN_PROFILE");
        if (occupied.contains(selected.workspaceBinding())) throw fault(409, "WORKSPACE_BUSY");
        Map<String, Object> selection = captureModel(modelId);
        if (diskCatalog == null && tasks.size() >= maxTasks || occupied.size() >= maxActive)
            throw fault(429, "TASK_LIMIT");
        if (occupied.contains(selected.workspaceBinding())) throw fault(409, "WORKSPACE_BUSY");
        if (followup(requestHash) != null) throw fault(409, "REQUEST_ID_CONFLICT");
        String sanitizedGoal = clean(goal);
        var task =
                new Task(
                        diskCatalog == null
                                ? "web-" + UUID.randomUUID()
                                : "web-" + catalogBinding.substring(0, 32) + "-" + requestHash,
                        profile,
                        requestHash,
                        goalHash,
                        selected.workspaceBinding());
        task.selection = selection;
        cache(task);
        cursors.clear();
        occupied.add(task.binding);
        try {
            var lease = leases.get(task.binding);
            if (lease != null) lease.accept(accepted(task), catalogBinding);
            var admission = SessionEvent.session(catalogId, "web.task.accepted", accepted(task));
            task.createdAt = admission.timestamp().toString();
            task.revision = catalog.append(admission);
            catalog.flush();
        } catch (IOException | RuntimeException ex) {
            broken = true;
            task.phase = "UNKNOWN";
            throw fault(503, "SUBMISSION_NOT_CONFIRMED");
        }
        task.worker =
                daemonFactory("jharness-web-task").newThread(() -> execute(task, sanitizedGoal));
        task.worker.start();
        return summary(task);
    }

    private Map<String, Object> workspaceInfo() {
        var rows = new ArrayList<Map<String, Object>>();
        if (workspaces != null) {
            for (Object raw : (List<?>) workspaces.read().get("workspaces")) {
                var row = new LinkedHashMap<>(Values.object(raw));
                var profile = profiles.get(row.get("id"));
                row.put("busy", profile != null && occupied.contains(profile.workspaceBinding()));
                rows.add(row);
            }
        } else
            profiles.forEach(
                    (id, profile) ->
                            rows.add(
                                    Map.of(
                                            "id",
                                            id,
                                            "name",
                                            id,
                                            "path",
                                            profile.workspaceBinding(),
                                            "busy",
                                            occupied.contains(profile.workspaceBinding()))));
        return Map.of(
                "workspaces",
                rows,
                "canRegister",
                workspaces != null,
                "pagedHistory",
                diskCatalog != null);
    }

    private void maintenanceReady(Task task) {
        if (!Set.of("FINISHED", "VERIFIED").contains(task.phase) || occupied.contains(task.binding))
            throw fault(409, "WORKSPACE_BUSY");
        if (!SessionRecovery.inspect(events(task)).ready()) throw fault(409, "RECOVERY_REQUIRED");
    }

    private Map.Entry<Task, TurnQueue.Input> followup(String hash) {
        if (diskCatalog == null) return null;
        var found = new ArrayList<Map.Entry<Task, TurnQueue.Input>>();
        diskCatalog.scan(
                history -> {
                    if (history.stream()
                            .anyMatch(
                                    e ->
                                            Set.of("web.turn.accepted", "web.turn.interrupted")
                                                            .contains(e.type())
                                                    && hash.equals(
                                                            e.payload().get("requestHash")))) {
                        var task = find((String) history.getFirst().payload().get("taskId"));
                        found.add(Map.entry(task, task.turns.inputs.get(hash)));
                    }
                });
        if (found.size() > 1) throw fault(503, "INVALID_SUBMISSION_INDEX");
        return found.isEmpty() ? null : found.getFirst();
    }

    private Map<String, Object> inputView(Task task, TurnQueue.Input input) {
        int n = new ArrayList<>(task.turns.inputs.keySet()).indexOf(input.hash) + 2;
        return Map.of("task", summary(task), "submission", input.view(n));
    }

    private void turnRoute(
            HttpExchange exchange,
            Task task,
            String[] path,
            String method,
            Map<String, String> query)
            throws IOException {
        if (path.length == 5 && method.equals("GET")) {
            if (query.keySet().equals(Set.of("request"))) {
                if (!query.get("request").matches("[A-Za-z0-9_-]{1,64}"))
                    throw fault(400, "INVALID_REQUEST_ID");
                var input = task.turns.inputs.get(hash(query.get("request")));
                if (input == null) throw fault(404, "SUBMISSION_NOT_FOUND");
                respond(exchange, 200, inputView(task, input));
                return;
            }
            if (!Set.of("offset").containsAll(query.keySet())) throw fault(400, "INVALID_QUERY");
            int offset;
            try {
                offset = Integer.parseInt(query.getOrDefault("offset", "0"));
            } catch (NumberFormatException ex) {
                throw fault(400, "INVALID_QUERY");
            }
            if (offset < 0 || offset > task.turns.inputs.size()) throw fault(400, "INVALID_QUERY");
            respond(exchange, 200, task.turns.page(offset));
            return;
        }
        if (diskCatalog == null) throw fault(400, "TURNS_UNAVAILABLE");
        if (!query.isEmpty()) throw fault(400, "INVALID_QUERY");
        if (path.length == 5 && method.equals("POST")) {
            var input = body(exchange, Set.of("requestId", "text", "kind"), Set.of("modelId"));
            respond(
                    exchange,
                    202,
                    enqueue(
                            task,
                            text(input, "requestId", 64),
                            text(input, "text", 16384),
                            text(input, "kind", 16),
                            optionalModel(input)));
            return;
        }
        if (path.length == 7 && method.equals("POST")) {
            body(exchange, Set.of());
            var input = task.turns.inputs.get(path[5]);
            if (input == null) throw fault(404, "SUBMISSION_NOT_FOUND");
            if (path[6].equals("cancel")) {
                if (TurnQueue.PENDING.contains(input.state)) {
                    var fact =
                            SessionEvent.session(
                                    catalogId,
                                    "web.turn.cancelled",
                                    Map.of("taskId", task.id, "requestHash", input.hash));
                    persistInput(task, fact);
                    input.content = "";
                    input.state = "CANCELLED";
                } else if (!input.state.equals("CANCELLED"))
                    throw fault(409, "SUBMISSION_ALREADY_STARTED");
            } else if (path[6].equals("start")) {
                if (!TurnQueue.PENDING.contains(input.state) || task.turns.head() != input)
                    throw fault(409, "QUEUE_ORDER_CONFLICT");
                continuationReady(task);
                prepareTurn(input.selection);
                startInput(task, input);
            } else throw fault(404, "NOT_FOUND");
            respond(exchange, 200, inputView(task, input));
            return;
        }
        throw fault(405, "METHOD_NOT_ALLOWED");
    }

    private static String optionalModel(Map<String, Object> body) {
        if (!body.containsKey("modelId")) return "";
        return text(body, "modelId", 32);
    }

    private Map<String, Object> captureModel(String modelId) {
        if (settings == null) {
            if (!modelId.isEmpty()) throw fault(400, "UNKNOWN_MODEL");
            return Map.of();
        }
        try {
            return settings.capture(modelId);
        } catch (HarnessException ex) {
            throw fault(400, ex.code());
        } catch (IOException ex) {
            throw fault(503, "CREDENTIAL_STORE_UNAVAILABLE");
        }
    }

    private void prepareTurn(Map<String, Object> selection) throws IOException {
        if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
        // Legacy submissions have no frozen route and must be explicitly re-submitted.
        if (settings != null && selection.isEmpty()) throw fault(409, "MODEL_SELECTION_REQUIRED");
        if (settings != null) {
            try {
                settings.prepareSelection(selection);
            } catch (HarnessException ex) {
                throw fault(400, ex.code());
            } catch (IOException ex) {
                throw fault(503, "CREDENTIAL_STORE_UNAVAILABLE");
            }
        }
    }

    private void continuationReady(Task task) {
        if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
        reconcileArchival(task);
        if (task.archived) throw fault(409, "SESSION_ARCHIVED");
        if (!Set.of("FINISHED", "VERIFIED").contains(task.phase)
                || task.sdk != null
                || occupied.contains(task.binding)) throw fault(409, "WORKSPACE_BUSY");
        if (!SessionRecovery.inspect(events(task)).ready()) throw fault(409, "RECOVERY_REQUIRED");
        if (events(task).stream().noneMatch(e -> e.type().equals("sdk.created")))
            throw fault(409, "SESSION_HISTORY_UNAVAILABLE");
        if (occupied.size() >= maxActive) throw fault(429, "ACTIVE_LIMIT");
    }

    private Map<String, Object> enqueue(
            Task task, String requestId, String content, String kind, String modelId)
            throws IOException {
        if (closed || broken) throw fault(503, "WEB_UNAVAILABLE");
        if (settingsChanging) throw fault(409, "WORKSPACE_BUSY");
        if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
        if (!Set.of("message", "supplement").contains(kind)) throw fault(400, "INVALID_BODY");
        String requestHash = hash(requestId), goalHash = hash(content);
        var prior = followup(requestHash);
        if (prior != null) {
            if (!prior.getKey().id.equals(task.id)
                    || !prior.getValue().goalHash.equals(goalHash)
                    || !prior.getValue().kind.equals(kind)
                    || !modelId.isEmpty()
                            && !modelId.equals(prior.getValue().selection.get("modelRoute")))
                throw fault(409, "REQUEST_ID_CONFLICT");
            return inputView(prior.getKey(), prior.getValue());
        }
        if (submission(requestHash) != null) throw fault(409, "REQUEST_ID_CONFLICT");
        reconcileArchival(task);
        if (task.phase.equals("UNKNOWN") || task.archived)
            throw fault(409, task.archived ? "SESSION_ARCHIVED" : "RECOVERY_REQUIRED");
        if (task.turns.inputs.size() >= 256 || task.turns.pending() >= 8)
            throw fault(429, "QUEUE_LIMIT");
        if (task.turns.inputs.values().stream().anyMatch(i -> i.state.equals("PAUSED")))
            throw fault(409, "QUEUE_PAUSED");
        boolean idle = Set.of("FINISHED", "VERIFIED").contains(task.phase);
        if (idle) {
            continuationReady(task);
        } else if (!occupied.contains(task.binding) || task.worker == null)
            throw fault(409, "WORKSPACE_BUSY");
        var selection = captureModel(modelId);
        String safe = clean(content);
        var fact =
                SessionEvent.session(
                        catalogId,
                        "web.turn.accepted",
                        Map.of(
                                "taskId",
                                task.id,
                                "requestHash",
                                requestHash,
                                "goalHash",
                                goalHash,
                                "content",
                                safe,
                                "kind",
                                kind,
                                "selection",
                                selection));
        persistInput(task, fact);
        var input = task.turns.inputs.get(requestHash);
        input.state = "QUEUED";
        if (idle) advance(task);
        return inputView(task, input);
    }

    private void persistInput(Task task, SessionEvent fact) {
        try {
            catalog.append(fact);
            catalog.flush();
            task.turns.apply(fact);
        } catch (RuntimeException ex) {
            broken = true;
            throw fault(503, "SUBMISSION_NOT_CONFIRMED");
        }
    }

    private void pauseInputs(Task task) {
        for (var input : task.turns.inputs.values())
            if (input.state.equals("QUEUED")) {
                persistInput(
                        task,
                        SessionEvent.session(
                                catalogId,
                                "web.turn.paused",
                                Map.of("taskId", task.id, "requestHash", input.hash)));
            }
    }

    private void advance(Task task) {
        var input = task.turns.head();
        if (input == null || !input.state.equals("QUEUED")) return;
        try {
            continuationReady(task);
            prepareTurn(input.selection);
            startInput(task, input);
        } catch (IOException ex) {
            if (!broken) pauseInputs(task);
        } catch (WebFault ex) {
            if (broken) throw ex;
            pauseInputs(task);
        }
    }

    private void startInput(Task task, TurnQueue.Input input) {
        String content = input.content;
        try {
            var pointer = new LinkedHashMap<String, Object>(accepted(task));
            pointer.put("turnHash", input.hash);
            pointer.put("turnGoalHash", input.goalHash);
            pointer.put("turnKind", input.kind);
            pointer.put("turnSelection", input.selection);
            var lease = leases.get(task.binding);
            if (lease != null) lease.accept(pointer, catalogBinding);
            persistInput(
                    task,
                    SessionEvent.session(
                            catalogId,
                            "web.turn.started",
                            Map.of("taskId", task.id, "requestHash", input.hash)));
            input.state = "RUNNING";
            task.executionSelection = input.selection;
            task.phase = "STARTING";
            task.status = "CREATED";
            task.error = "";
            task.cancelled = false;
            task.recovered = false;
            occupied.add(task.binding);
            task.worker = new Thread(() -> execute(task, content, input), "jharness-web-turn");
            task.cleanupComplete = false;
            task.worker.setDaemon(true);
            task.worker.start();
        } catch (IOException | RuntimeException ex) {
            input.state = "UNKNOWN";
            task.phase = "UNKNOWN";
            occupied.add(task.binding);
            broken = true;
            throw fault(503, "SUBMISSION_NOT_CONFIRMED");
        }
    }

    private Map<String, Object> fullEvent(SessionEvent e) {
        var row = new LinkedHashMap<String, Object>();
        row.put("eventId", e.eventId());
        row.put("sessionId", e.sessionId());
        row.put("type", e.type());
        row.put("timestamp", e.timestamp().toString());
        row.put("schemaVersion", e.schemaVersion());
        row.put("level", e.level().name());
        row.put("turnId", e.turnId());
        row.put("stepId", e.stepId());
        row.put("callId", e.callId());
        row.put("payload", sanitizer.sanitize(e.payload()));
        return row;
    }

    private Map<String, Object> forkTask(Task parent, String event, String requestId)
            throws IOException {
        if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw fault(400, "INVALID_REQUEST_ID");
        String requestHash = hash(requestId), goalHash = hash("fork:" + parent.id + ":" + event);
        var prior = submission(requestHash);
        if (prior != null) {
            if (!prior.profile.equals(parent.profile) || !prior.goalHash.equals(goalHash))
                throw fault(409, "REQUEST_ID_CONFLICT");
            return summary(prior);
        }
        if (followup(requestHash) != null) throw fault(409, "REQUEST_ID_CONFLICT");
        maintenanceReady(parent);
        if (occupied.size() >= maxActive) throw fault(429, "ACTIVE_LIMIT");
        var profile = profiles.get(parent.profile);
        if (!(profile.sessions() instanceof JsonlSessionRepository physical)
                || diskCatalog == null
                || profile.artifacts() == null) throw fault(400, "FORK_UNAVAILABLE");
        var history = events(parent);
        int end = -1;
        for (int i = 0; i < history.size(); i++)
            if (history.get(i).eventId().equals(event)) end = i;
        if (end < 0) throw fault(400, "UNKNOWN_CURSOR");
        if (!SessionRecovery.inspect(history.subList(0, end + 1)).ready())
            throw fault(409, "RECOVERY_REQUIRED");
        var task =
                new Task(
                        "web-" + catalogBinding.substring(0, 32) + "-" + requestHash,
                        parent.profile,
                        requestHash,
                        goalHash,
                        parent.binding);
        task.archived = SessionLifecycle.archived(history.subList(0, end + 1));
        task.name =
                clean(
                        parent.name.substring(0, Math.min(70, parent.name.length()))
                                + " \u00b7 \u5206\u53c9");
        cache(task);
        occupied.add(task.binding);
        cursors.clear();
        try {
            var lease = leases.get(task.binding);
            if (lease != null) lease.accept(accepted(task), catalogBinding);
            task.revision =
                    catalog.append(
                            SessionEvent.session(catalogId, "web.task.accepted", accepted(task)));
            catalog.flush();
            physical.forkTo(
                    parent.id,
                    event,
                    task.id,
                    target -> {
                        try {
                            profile.artifacts().copy(parent.id, target);
                        } catch (IOException ex) {
                            throw new HarnessException(
                                    "ARTIFACT_COPY_FAILED", "Artifact snapshot failed");
                        }
                    },
                    target -> {
                        try {
                            profile.artifacts().rollback(target);
                        } catch (IOException ex) {
                            throw new HarnessException(
                                    "ARTIFACT_ROLLBACK_FAILED", "Artifact compensation failed", ex);
                        }
                    });
            task.phase = "FINISHED";
            task.revision =
                    catalog.append(
                            SessionEvent.session(
                                    catalogId,
                                    "web.task.finished",
                                    Map.of(
                                            "taskId",
                                            task.id,
                                            "phase",
                                            task.phase,
                                            "status",
                                            task.status,
                                            "error",
                                            "",
                                            "parentSessionId",
                                            parent.id,
                                            "fromEventId",
                                            event)));
            task.revision =
                    catalog.append(
                            SessionEvent.session(
                                    catalogId,
                                    "web.task.changed",
                                    Map.of(
                                            "taskId",
                                            task.id,
                                            "name",
                                            task.name,
                                            "pinned",
                                            false,
                                            "archived",
                                            task.archived)));
            catalog.flush();
            if (lease != null) lease.clear(task.id, catalogBinding);
            occupied.remove(task.binding);
            return summary(task);
        } catch (IOException | RuntimeException ex) {
            task.phase = "UNKNOWN";
            broken = true;
            throw fault(503, "SUBMISSION_NOT_CONFIRMED");
        }
    }

    private void execute(Task task, String goal) {
        execute(task, goal, null);
    }

    private void execute(Task task, String goal, TurnQueue.Input input) {
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
                                    sanitizer,
                                    input == null ? task.selection : input.selection);
            synchronized (this) {
                task.sdk = sdk;
                if (task.cancelled || closed) sdk.cancel();
                task.phase = "RUNNING";
                task.status = "RUNNING";
            }
            if (!sdk.snapshot().sessionId().equals(task.id)) throw new IllegalStateException();
            status =
                    sdk.run(
                                    goal,
                                    () -> task.cancelled,
                                    input == null
                                            ? Map.of()
                                            : Map.of("requestHash", input.hash, "kind", input.kind))
                            .status()
                            .name();
        } catch (CancellationException ex) {
            status = "CANCELLED";
        } catch (Exception ex) {
            phase = "UNKNOWN";
            error = "TASK_EXECUTION_UNCERTAIN";
        } finally {
            if (sdk != null) {
                try {
                    var display = sdk.display().snapshot();
                    synchronized (this) {
                        task.display = display;
                    }
                } catch (RuntimeException displayFailure) {
                    phase = "UNKNOWN";
                    error = "TASK_DISPLAY_UNCERTAIN";
                }
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
                        if (input != null) {
                            if (phase.equals("FINISHED")) {
                                String turnId =
                                        events(task).stream()
                                                .filter(e -> e.type().equals("turn.started"))
                                                .reduce((a, b) -> b)
                                                .map(SessionEvent::turnId)
                                                .orElse("");
                                var fact =
                                        SessionEvent.session(
                                                catalogId,
                                                "web.turn.finished",
                                                Map.of(
                                                        "taskId",
                                                        task.id,
                                                        "requestHash",
                                                        input.hash,
                                                        "status",
                                                        status,
                                                        "turnId",
                                                        turnId));
                                catalog.append(fact);
                                task.turns.apply(fact);
                            } else input.state = "UNKNOWN";
                        }
                        catalog.append(
                                SessionEvent.session(
                                        catalogId,
                                        "web.task.finished",
                                        Map.of(
                                                "taskId",
                                                task.id,
                                                "phase",
                                                phase,
                                                "status",
                                                status,
                                                "error",
                                                error,
                                                "cancelRequested",
                                                task.cancelled)));
                        catalog.flush();
                    } catch (RuntimeException ex) {
                        broken = true;
                        task.phase = "UNKNOWN";
                    }
                }
                if (task.phase.equals("FINISHED") && !closed) {
                    try {
                        var lease = leases.get(task.binding);
                        if (lease != null) lease.clear(task.id, catalogBinding);
                        occupied.remove(task.binding);
                    } catch (IOException | RuntimeException ex) {
                        broken = true;
                        task.phase = "UNKNOWN";
                        task.error = "WORKSPACE_CHECKPOINT_FAILED";
                    }
                }
                try {
                    if (!closed && !broken) {
                        if (task.phase.equals("FINISHED") && !task.cancelled) {
                            try {
                                advance(task);
                            } catch (WebFault ex) {
                                /* failed checkpoint already preserves UNKNOWN */
                            }
                        } else pauseInputs(task);
                    }
                } catch (RuntimeException pauseFailure) {
                    broken = true;
                    task.phase = "UNKNOWN";
                    task.error = "QUEUE_CHECKPOINT_FAILED";
                } finally {
                    if (task.worker == Thread.currentThread()) task.cleanupComplete = true;
                    notifyAll();
                    if (closed
                            && tasks.values().stream()
                                    .noneMatch(t -> t.worker != null && !t.cleanupComplete)) {
                        try {
                            closeResources();
                        } catch (RuntimeException failure) {
                            broken = true;
                            task.phase = "UNKNOWN";
                            task.error = "RESOURCE_CLEANUP_UNKNOWN";
                        }
                    }
                }
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
                                call.sideEffectLevel().name()),
                        System.nanoTime() + approvalTimeout.toNanos());
        synchronized (this) {
            if (closed || task.cancelled || task.approvals.size() >= 8)
                return new ApprovalService.ApprovalDecision(
                        false, "Approval unavailable", "web-server");
            task.approvals.put(id, pending);
        }
        long end = pending.deadline;
        try {
            while (true) {
                context.cancellation().throwIfCancelled();
                synchronized (this) {
                    if (closed || task.cancelled) break;
                    if (context.cancellation().isCancelled()) break;
                    // POST and expiry linearize under the same monitor. A decision accepted
                    // before the deadline must survive a delayed owner waking after it.
                    if (pending.decision.isDone()) return pending.decision.join();
                    if (System.nanoTime() >= end) {
                        pending.decision.complete(
                                new ApprovalService.ApprovalDecision(
                                        false, "Approval expired", "web-server"));
                        return pending.decision.join();
                    }
                }
                try {
                    var answer = pending.decision.get(20, TimeUnit.MILLISECONDS);
                    synchronized (this) {
                        if (closed || task.cancelled || context.cancellation().isCancelled()) break;
                        return answer;
                    }
                } catch (TimeoutException ignored) {
                    /* bounded cancellation polling */
                }
            }
            return new ApprovalService.ApprovalDecision(
                    false,
                    task.cancelled
                            ? "Approval cancelled"
                            : closed ? "Approval unavailable" : "Approval expired",
                    "web-server");
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

    private void reconcileArchival(Task task) {
        if (!Set.of("FINISHED", "VERIFIED").contains(task.phase)) return;
        var history = events(task);
        if (!history.isEmpty()) task.archived = SessionLifecycle.archived(history);
    }

    private Map<String, Object> summary(Task task) {
        reconcileArchival(task);
        var value =
                new LinkedHashMap<String, Object>(
                        Map.of(
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
                                HarnessSdk.PROTOCOL_VERSION));
        var modelSelection =
                task.executionSelection.isEmpty() ? task.selection : task.executionSelection;
        var resolved = Values.object(modelSelection.getOrDefault("resolved", Map.of()));
        value.put("modelId", modelSelection.getOrDefault("modelRoute", ""));
        value.put("modelName", resolved.getOrDefault("model", ""));
        value.put("modelProtocol", resolved.getOrDefault("protocol", "chat-completions"));
        value.put("name", task.name);
        value.put("pinned", task.pinned);
        value.put("archived", task.archived);
        value.put("createdAt", task.createdAt);
        value.put("revision", task.revision);
        value.put(
                "queuedInputs",
                task.pendingSnapshot == null ? task.turns.pending() : task.pendingSnapshot);
        return value;
    }

    private List<SessionEvent> events(Task task) {
        SessionRepository repository;
        synchronized (this) {
            repository = profiles.get(task.profile).sessions();
        }
        if (repository instanceof JsonlSessionRepository physical) {
            if (!java.nio.file.Files.exists(
                    physical.directory().resolve(task.id + ".jsonl"),
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) return List.of();
            return repository.openReader(task.id).events();
        }
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
                    if (bytes + frame.length + 256 > WebJson.RESPONSE_LIMIT) {
                        continuation(exchange, cursorQuery);
                        return;
                    }
                    exchange.getResponseBody().write(frame);
                    exchange.getResponseBody().flush();
                    cursorQuery.put("after", id);
                    bytes += frame.length;
                    if (++count >= limit) {
                        continuation(exchange, cursorQuery);
                        return;
                    }
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
            continuation(exchange, cursorQuery);
        } finally {
            streams.release();
        }
    }

    /** SSE EOF never proves all facts have been read; resume only with a bounded GET. */
    private static void continuation(HttpExchange exchange, Map<String, String> query)
            throws IOException {
        exchange.getResponseBody()
                .write(
                        (": continuation=GET; after="
                                        + query.getOrDefault("after", "")
                                        + "; eof-is-not-completion\n\n")
                                .getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
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
        if (raw.length() > 4096) throw fault(400, "INVALID_QUERY");
        var result = new HashMap<String, String>();
        for (String item : raw.split("&", -1)) {
            var pair = item.split("=", -1);
            if (pair.length != 2
                    || !pair[0].matches("[a-z]+")
                    || !pair[1].matches(
                            pair[0].equals("path")
                                    ? "[A-Za-z0-9_-]{1,2048}"
                                    : "[A-Za-z0-9_-]{1,64}")
                    || result.putIfAbsent(pair[0], pair[1]) != null)
                throw fault(400, "INVALID_QUERY");
        }
        return result;
    }

    private static Map<String, Object> body(HttpExchange exchange, Set<String> keys)
            throws IOException {
        return body(exchange, keys, Set.of());
    }

    private static Map<String, Object> body(
            HttpExchange exchange, Set<String> keys, Set<String> optional) throws IOException {
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
        var actual = new HashSet<>(value.keySet());
        actual.removeAll(optional);
        if (!actual.equals(keys)) throw fault(400, "INVALID_BODY");
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
        respond(exchange, prepareResponse(status, value));
    }

    private record PreparedResponse(int status, byte[] bytes) {}

    private PreparedResponse prepareResponse(int status, Map<String, Object> value)
            throws IOException {
        byte[] bytes;
        try {
            bytes = WebJson.encode(sanitizer.sanitize(value), WebJson.RESPONSE_LIMIT);
        } catch (IOException oversized) {
            status = 413;
            bytes = WebJson.encode(Map.of("error", "RESPONSE_LIMIT"));
        }
        return new PreparedResponse(status, bytes);
    }

    private void respond(HttpExchange exchange, PreparedResponse response) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Connection", "close");
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        if (response.status() == 401) headers.set("WWW-Authenticate", "Bearer");
        exchange.sendResponseHeaders(response.status(), response.bytes().length);
        exchange.getResponseBody().write(response.bytes());
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
        final long deadline;
        final Map<String, Object> view;
        final CompletableFuture<ApprovalService.ApprovalDecision> decision =
                new CompletableFuture<>();

        Pending(String callId, Map<String, Object> view, long deadline) {
            this.callId = callId;
            this.deadline = deadline;
            this.view = view;
        }
    }

    private static final class Task {
        final String id, profile, requestHash, goalHash, binding;
        Map<String, Object> selection = Map.of(), executionSelection = Map.of();
        final TurnQueue turns = new TurnQueue();
        Long pendingSnapshot;
        final Map<String, Pending> approvals = new LinkedHashMap<>();
        String phase = "STARTING", status = "CREATED", error = "";
        String name = "", createdAt = java.time.Instant.now().toString(), revision = "";
        boolean pinned, archived;
        volatile boolean cancelled;
        boolean recovered;
        HarnessSdk sdk;
        Map<String, Object> display = Map.of();
        Thread worker;
        boolean cleanupComplete;

        Task(String id, String profile, String requestHash, String goalHash, String binding) {
            if (id == null
                    || !id.matches("[A-Za-z0-9_-]{1,128}")
                    || profile == null
                    || !profile.matches("[A-Za-z0-9_-]{1,32}")
                    || requestHash == null
                    || !requestHash.matches("[a-f0-9]{64}")
                    || goalHash == null
                    || !goalHash.matches("[a-f0-9]{64}")
                    || binding == null
                    || binding.isBlank()
                    || binding.length() > 1024)
                throw new IllegalArgumentException("Invalid task identity");
            this.id = id;
            this.profile = profile;
            this.requestHash = requestHash;
            this.goalHash = goalHash;
            this.binding = binding;
            this.name =
                    "\u4f1a\u8bdd " + requestHash.substring(0, Math.min(8, requestHash.length()));
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
        try (var owner = catalogRepository.openWriter(catalogId, WriteOwnership.EXCLUSIVE);
                var lease =
                        sessions instanceof JsonlSessionRepository physical
                                ? new WorkspaceLease(physical)
                                : null) {
            var disk =
                    catalogRepository instanceof JsonlSessionRepository physical
                            ? new DurableTaskCatalog(physical, catalogId, owner, sanitizer)
                            : null;
            SessionWriter writer =
                    disk == null ? new SanitizingSessionWriter(owner, sanitizer) : disk;
            String catalogKey =
                    hash(
                            catalogRepository instanceof JsonlSessionRepository physical
                                    ? physical.directory().resolve(catalogId).toString()
                                    : catalogId);
            if (lease != null && !lease.pending().isEmpty()) {
                if (!taskId.equals(lease.pending().get("taskId"))
                        || !catalogKey.equals(lease.pending().get("catalogKey")))
                    throw new HarnessException(
                            "ENVIRONMENT_MISMATCH", "Use original catalog and task");
                if (disk != null && disk.byId(taskId).isEmpty()) {
                    var admission = new LinkedHashMap<>(lease.pending());
                    admission.remove("catalogKey");
                    writer.append(SessionEvent.session(catalogId, "web.task.accepted", admission));
                    writer.flush();
                }
            }
            var events =
                    disk == null
                            ? catalogRepository.openReader(catalogId).events()
                            : disk.byId(taskId);
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
                            .filter(
                                    e ->
                                            taskId.equals(e.payload().get("taskId"))
                                                    && Set.of(
                                                                    "web.task.accepted",
                                                                    "web.task.finished",
                                                                    "web.task.verified",
                                                                    "web.turn.started",
                                                                    "web.turn.interrupted")
                                                            .contains(e.type()))
                            .reduce((a, b) -> b)
                            .orElseThrow();
            if (last.type().equals("web.task.verified")
                    || last.type().equals("web.task.finished")
                            && !"UNKNOWN".equals(last.payload().get("phase")))
                throw new HarnessException("VERIFICATION_NOT_REQUIRED", "Task already settled");
            if (sessions instanceof JsonlSessionRepository physical
                    ? java.nio.file.Files.exists(
                            physical.directory().resolve(taskId + ".jsonl"),
                            java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    : sessions.list().contains(taskId)) {
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
            if (lease != null) lease.clear(taskId, catalogKey);
        } catch (IOException ex) {
            throw new HarnessException("WEB_VERIFICATION_FAILED", "Offline maintenance failed", ex);
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
            if (tasks.values().stream().noneMatch(t -> t.worker != null && !t.cleanupComplete))
                closeResources();
            // A noncooperating worker retains the ownership locks until its mandatory cleanup
            // exits.
            // Its acceptance pointer remains unresolved; a new host must not replay it.
        }
    }

    private void closeResources() {
        if (resourcesClosed) return;
        resourcesClosed = true;
        RuntimeException failure = null;
        try {
            catalog.close();
        } catch (RuntimeException ex) {
            failure = ex;
        }
        for (var lease : leases.values()) {
            try {
                lease.close();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
