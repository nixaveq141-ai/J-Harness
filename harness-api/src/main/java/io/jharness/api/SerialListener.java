package io.jharness.api;

@FunctionalInterface
public interface SerialListener {
    void accept(SessionEvent event) throws Exception;
}
