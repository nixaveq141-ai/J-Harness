package io.jharness.api;

@FunctionalInterface
public interface Interceptor<T> {
    T intercept(T request, Next<T> next) throws Exception;

    @FunctionalInterface
    interface Next<T> {
        T proceed(T request) throws Exception;
    }
}
