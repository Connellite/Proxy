package io.github.connellite.proxy;

/**
 * Lifecycle for an inbound listener. {@code start} stays on each server: bind signatures differ.
 */
public interface ServerListener {

    boolean isRunning();

    void stop();

    default String getProtocol() {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no protocol label");
    }
}
