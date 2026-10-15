package io.jharness.api;

@FunctionalInterface
public interface NotificationListener {
    void accept(SessionEvent event) throws Exception;
}
