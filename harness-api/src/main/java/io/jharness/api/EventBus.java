package io.jharness.api;

import java.time.Duration;

public interface EventBus {
    /** Nonblocking notification; observer failures cannot change the publisher result. */
    void publish(SessionEvent event);

    Registration notify(String type, NotificationListener listener);

    /** Ascending priority; ties preserve registration order. Timeout cancels cooperatively. */
    Registration serial(String type, SerialListener listener, int priority, Duration timeout);

    void publishSerial(SessionEvent event);

    <T> Registration intercept(ExtensionPoint<T> point, Interceptor<T> interceptor, int priority);

    <T> T invoke(ExtensionPoint<T> point, T request, Interceptor.Next<T> terminal);
}
