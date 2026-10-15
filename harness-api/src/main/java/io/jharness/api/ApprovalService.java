package io.jharness.api;

@FunctionalInterface
public interface ApprovalService {
    ApprovalDecision decide(ToolInvocation call, ToolContext context);

    record ApprovalDecision(boolean approved, String reason, String actor) {
        public ApprovalDecision {
            reason = Values.required(reason, "reason");
            actor = Values.required(actor, "actor");
        }
    }

    static ApprovalService rejectAll() {
        return (call, context) ->
                new ApprovalDecision(false, "No approval interface configured", "default");
    }
}
