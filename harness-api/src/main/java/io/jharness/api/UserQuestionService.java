package io.jharness.api;

import java.util.*;

/** Scoped user input only. Answers never authorize tools, plans, or configuration changes. */
public interface UserQuestionService {
    Map<String, Object> ask(ToolInvocation call, ToolContext context);

    List<Map<String, Object>> list();

    Map<String, Object> draft(Map<String, Object> input);

    Map<String, Object> respond(Map<String, Object> submission);
}
