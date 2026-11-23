package io.github.connellite.proxy.dns.tcp;

/**
 * Lifecycle for an inbound TCP listener.
 */
public interface TcpListener {

    boolean isRunning();

    void stop();

}
