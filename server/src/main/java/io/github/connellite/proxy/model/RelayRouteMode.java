package io.github.connellite.proxy.model;

/** How DNS answers a name, and whether the TCP relay accepts it. */
public enum RelayRouteMode {
    /** DNS returns this server's address. The TCP relay connects to the upstream. */
    RELAY,
    /** DNS returns {@code answerAddress}. The TCP relay does not accept the name. */
    ADDRESS
}
