package io.jharness.plugin;

import io.jharness.api.*;
import java.util.*;

/** Fail-fast duplicates; exact scope, no implicit global or parent fallback. */
public final class DefaultServiceRegistry implements ServiceRegistry {
    private final Map<Key, Object> entries = new LinkedHashMap<>();

    @Override
    public synchronized <T> Registration register(
            Class<T> type, String key, T implementation, PluginScope scope) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(scope);
        Values.required(key, "key");
        type.cast(Objects.requireNonNull(implementation));
        Key identity = new Key(type, key, scope);
        if (entries.containsKey(identity))
            throw new HarnessException(
                    "DUPLICATE_SERVICE", "Duplicate service: " + type.getName() + "/" + key);
        entries.put(identity, implementation);
        return new Registration() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (DefaultServiceRegistry.this) {
                    if (!closed) {
                        closed = true;
                        entries.remove(identity);
                    }
                }
            }
        };
    }

    @Override
    public synchronized <T> Optional<T> find(Class<T> type, String key, PluginScope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        Values.required(key, "key");
        return Optional.ofNullable(type.cast(entries.get(new Key(type, key, scope))));
    }

    @Override
    public <T> T require(Class<T> type, String key, PluginScope scope) {
        return find(type, key, scope)
                .orElseThrow(
                        () ->
                                new HarnessException(
                                        "MISSING_SERVICE",
                                        "Missing service: " + type.getName() + "/" + key));
    }

    @Override
    public synchronized <T> T requireUnique(Class<T> type, PluginScope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        List<T> found =
                entries.entrySet().stream()
                        .filter(e -> e.getKey().type.equals(type) && e.getKey().scope.equals(scope))
                        .map(e -> type.cast(e.getValue()))
                        .toList();
        if (found.isEmpty())
            throw new HarnessException("MISSING_SERVICE", "Missing service: " + type.getName());
        if (found.size() > 1)
            throw new HarnessException("AMBIGUOUS_SERVICE", "Ambiguous service: " + type.getName());
        return found.getFirst();
    }

    private record Key(Class<?> type, String key, PluginScope scope) {}
}
