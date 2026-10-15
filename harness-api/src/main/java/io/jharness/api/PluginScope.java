package io.jharness.api;

/** Exact scope visibility. Parent/child agents never inherit contributions implicitly. */
public record PluginScope(String sessionId, String agentId) {
    public PluginScope {
        sessionId = Values.required(sessionId, "sessionId");
        agentId = Values.required(agentId, "agentId");
    }
}
