package io.github.connellite.proxy.dns.server;

/**
 * Lifecycle for an inbound DNS listener.
 */
public interface RelayListener {

    boolean isRunning();

    void stop();

    default String getProtocol() {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no protocol label");
    }
}
