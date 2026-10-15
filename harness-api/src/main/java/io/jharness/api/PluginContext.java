package io.jharness.api;

public interface PluginContext {
    <T> Registration register(Class<T> type, String key, T implementation);

    ServiceRegistry services();

    EventBus events();

    ConfigView config();

    PluginScope scope();

    /** Track resources so startup rollback and stop close them in reverse order. */
    Registration own(AutoCloseable resource);
}
