package io.jharness.web;

import io.jharness.api.*;
import java.util.*;

/** Bounded submission projection; executable text is retained only for pending inputs. */
final class TurnQueue {
    static final Set<String> PENDING = Set.of("QUEUED", "PAUSED");
    final Map<String, Input> inputs = new LinkedHashMap<>();

    static final class Input {
        final String hash, goalHash, kind;
        Map<String, Object> selection = Map.of();
        String content, state, turnId = "", status = "";

        Input(String hash, String goalHash, String kind, String content, String state) {
            if (hash == null
                    || !hash.matches("[a-f0-9]{64}")
                    || goalHash == null
                    || !goalHash.matches("[a-f0-9]{64}")
                    || kind == null
                    || !Set.of("message", "supplement").contains(kind)
                    || content == null
                    || state == null
                    || content.length() > 16384)
                throw new IllegalArgumentException("Invalid submission fact");
            this.hash = hash;
            this.goalHash = goalHash;
            this.kind = kind;
            this.content = content;
            this.state = state;
        }

        Map<String, Object> view(int sequence) {
            return Map.of(
                    "submissionId",
                    hash,
                    "kind",
                    kind,
                    "state",
                    state,
                    "turnId",
                    turnId,
                    "status",
                    status,
                    "sequence",
                    sequence,
                    "modelId",
                    selection.getOrDefault("modelRoute", ""),
                    "preview",
                    content.substring(0, Math.min(200, content.length())));
        }
    }

    void apply(SessionEvent event) {
        var p = event.payload();
        if (!(p.get("requestHash") instanceof String hash) || !hash.matches("[a-f0-9]{64}"))
            throw new HarnessException("INVALID_WEB_CATALOG", "Invalid submission hash");
        if (event.type().equals("web.turn.accepted")
                || event.type().equals("web.turn.interrupted")) {
            if (inputs.size() >= 256 || inputs.containsKey(hash))
                throw new IllegalArgumentException("Submission quota or duplicate");
            if (!(p.get("goalHash") instanceof String goalHash)
                    || !(p.get("kind") instanceof String kind)
                    || !(p.getOrDefault("content", "") instanceof String content))
                throw new HarnessException("INVALID_WEB_CATALOG", "Invalid submission fields");
            Input candidate;
            try {
                candidate =
                        new Input(
                                hash,
                                goalHash,
                                kind,
                                content,
                                event.type().equals("web.turn.interrupted") ? "UNKNOWN" : "PAUSED");
            } catch (IllegalArgumentException invalid) {
                throw new HarnessException(
                        "INVALID_WEB_CATALOG", "Invalid submission fact", invalid);
            }
            if (!(p.getOrDefault("selection", Map.of()) instanceof Map<?, ?>))
                throw new HarnessException("INVALID_WEB_CATALOG", "Invalid selection");
            var selection = Values.object(p.getOrDefault("selection", Map.of()));
            if (selection.containsKey("modelRoute")
                    && !(selection.get("modelRoute") instanceof String))
                throw new HarnessException("INVALID_WEB_CATALOG", "Invalid model route");
            candidate.selection = selection;
            inputs.put(hash, candidate);
            return;
        }
        var input = inputs.get(hash);
        if (input == null) throw new IllegalArgumentException("Submission fact without admission");
        switch (event.type()) {
            case "web.turn.started" -> {
                input.state = "UNKNOWN";
                input.content = "";
            }
            case "web.turn.finished" -> {
                if (!(p.get("status") instanceof String status)
                        || !Set.of(
                                        "UNKNOWN",
                                        "CREATED",
                                        "RUNNING",
                                        "WAITING_APPROVAL",
                                        "EXECUTING_TOOL",
                                        "COMPLETED",
                                        "FAILED",
                                        "CANCELLED",
                                        "LIMIT_REACHED")
                                .contains(status)
                        || p.containsKey("turnId") && !(p.get("turnId") instanceof String))
                    throw new HarnessException("INVALID_WEB_CATALOG", "Invalid submission outcome");
                input.state = "FINISHED";
                input.status = (String) p.get("status");
                input.turnId = (String) p.getOrDefault("turnId", "");
                input.content = "";
            }
            case "web.turn.cancelled" -> {
                input.state = "CANCELLED";
                input.content = "";
            }
            case "web.turn.paused" -> input.state = "PAUSED";
            default -> throw new IllegalArgumentException("Unknown submission event");
        }
    }

    long pending() {
        return inputs.values().stream().filter(i -> PENDING.contains(i.state)).count();
    }

    Input head() {
        return inputs.values().stream()
                .filter(i -> PENDING.contains(i.state))
                .findFirst()
                .orElse(null);
    }

    void verified() {
        inputs.values().stream()
                .filter(i -> Set.of("UNKNOWN", "RUNNING").contains(i.state))
                .forEach(
                        i -> {
                            i.state = "VERIFIED";
                            i.content = "";
                        });
    }

    Map<String, Object> page(int offset) {
        if (offset < 0 || offset > inputs.size())
            throw new IllegalArgumentException("Invalid submission page");
        var rows = new ArrayList<Map<String, Object>>();
        int n = 0;
        for (var input : inputs.values()) {
            if (n >= offset && rows.size() < 32) rows.add(input.view(n + 2));
            n++;
        }
        return Map.of(
                "submissions",
                rows,
                "total",
                inputs.size(),
                "pending",
                pending(),
                "next",
                offset + rows.size(),
                "more",
                offset + rows.size() < inputs.size());
    }
}
