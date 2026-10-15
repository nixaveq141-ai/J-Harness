package io.jharness.api;

import java.util.Optional;

public interface ServiceRegistry {
    <T> Registration register(Class<T> type, String key, T implementation, PluginScope scope);

    <T> Optional<T> find(Class<T> type, String key, PluginScope scope);

    <T> T require(Class<T> type, String key, PluginScope scope);

    <T> T requireUnique(Class<T> type, PluginScope scope);
}
