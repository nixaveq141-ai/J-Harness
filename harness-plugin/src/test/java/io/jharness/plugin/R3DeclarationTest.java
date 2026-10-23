package io.jharness.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.jharness.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class R3DeclarationTest {
    @Test
    void badDeclarationFailsBeforeRegistrationAndNamesTool() {
        var registry = new ScopedToolRegistry();
        var scope = new PluginScope("s", "a");
        AgentTool broken =
                new AgentTool() {
                    public String name() {
                        return "broken";
                    }

                    public String description() {
                        return "fixture";
                    }

                    public Map<String, Object> inputSchema() {
                        return null;
                    }

                    public Map<String, Object> outputSchema() {
                        return Map.of("type", "object");
                    }

                    public SideEffectLevel sideEffectLevel() {
                        return SideEffectLevel.READ_ONLY;
                    }

                    public ToolResult execute(ToolInvocation call, ToolContext context) {
                        throw new AssertionError();
                    }
                };
        var failure = assertThrows(HarnessException.class, () -> registry.register(broken, scope));
        assertEquals("INVALID_TOOL_DECLARATION", failure.code());
        assertTrue(failure.getMessage().contains("broken"));
        assertTrue(registry.visibleTools(scope).isEmpty());
    }
}
