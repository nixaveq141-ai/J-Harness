package io.jharness.api;

/** append must confirm its storage guarantee or throw; memory writers are TEST ONLY. */
public interface SessionWriter extends AutoCloseable {
    /** Only a physical-storage provider may claim this; memory implementations default to false. */
    default boolean durable() {
        return false;
    }

    String append(SessionEvent event);

    void flush();

    @Override
    void close();
}
