package io.jharness.api;

public enum TaskStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    EXECUTING_TOOL,
    COMPLETED,
    FAILED,
    CANCELLED,
    LIMIT_REACHED
}
