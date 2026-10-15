package io.jharness.events;

public record EventFailure(String code, String eventId, String type, Throwable cause) {}
