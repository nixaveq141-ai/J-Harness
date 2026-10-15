package io.jharness.testkit;

import io.jharness.api.*;
import java.util.*;

/** A recording policy for call-order assertions, not a physical persistence guarantee. */
public final class CheckpointRecorder implements CheckpointPolicy {
    private final CheckpointPolicy delegate;
    private final List<String> boundaries = new ArrayList<>();

    public CheckpointRecorder(CheckpointPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public synchronized List<String> boundaries() {
        return List.copyOf(boundaries);
    }

    @Override
    public synchronized CheckpointDecision beforeEachModelRequest(SessionState state) {
        boundaries.add("model:" + state.sessionId());
        return delegate.beforeEachModelRequest(state);
    }

    @Override
    public synchronized CheckpointDecision beforeTopLevelTool(ToolInvocation call) {
        boundaries.add("tool:" + call.callId());
        return delegate.beforeTopLevelTool(call);
    }

    @Override
    public synchronized CheckpointDecision atStepBoundary(SessionState state) {
        boundaries.add("step:" + state.sessionId());
        return delegate.atStepBoundary(state);
    }
}
