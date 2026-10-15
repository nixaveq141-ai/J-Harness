package io.jharness.ecosystem;

import java.util.*;

/** Scoped observation and cancellation only. Launching is exclusively a policy-controlled tool. */
public interface SubagentService {
    List<Map<String, Object>> tasks();

    Map<String, Object> status(String taskId);

    void cancel(String taskId);
}
