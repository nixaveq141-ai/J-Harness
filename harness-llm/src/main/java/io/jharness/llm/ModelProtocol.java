package io.jharness.llm;

/** Wire protocol selected by the trusted host, never by model output. */
public enum ModelProtocol {
    CHAT_COMPLETIONS("chat-completions", "/chat/completions"),
    RESPONSES("responses", "/responses"),
    ANTHROPIC("anthropic", "/messages");

    private final String id;
    final String path;

    ModelProtocol(String id, String path) {
        this.id = id;
        this.path = path;
    }

    public String id() {
        return id;
    }

    public static ModelProtocol fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new io.jharness.api.HarnessException("INVALID_CONFIG", "Unsupported model protocol");
    }
}
