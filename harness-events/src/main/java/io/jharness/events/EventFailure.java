package io.jharness.events;

public record EventFailure(String code, String eventId, String type, Throwable cause) {
    @Override
    public String toString() {
        return "EventFailure[code="
                + code
                + ", causeType="
                + (cause == null ? "none" : cause.getClass().getName())
                + "]";
    }
}
