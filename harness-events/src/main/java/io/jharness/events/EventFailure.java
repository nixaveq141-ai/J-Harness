package io.jharness.events;

public record EventFailure(String code, String eventId, String type, Throwable cause) {
    public EventFailure {
        code = io.jharness.api.Values.required(code, "code");
        eventId = io.jharness.api.Values.required(eventId, "eventId");
        type = io.jharness.api.Values.required(type, "type");
    }

    @Override
    public String toString() {
        return "EventFailure[code="
                + code
                + ", causeType="
                + (cause == null ? "none" : cause.getClass().getName())
                + "]";
    }
}
