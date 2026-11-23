package io.github.connellite.proxy.proxy;

/**
 * Lifecycle for an inbound TCP listener.
 */
public interface ProxyListener {

    boolean isRunning();

    void stop();

}
