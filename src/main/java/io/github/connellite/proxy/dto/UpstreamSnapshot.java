package io.github.connellite.proxy.dto;

import io.github.connellite.proxy.model.UpstreamProxyType;

/**
 * Immutable snapshot of the active upstream used by outbound Netty handlers
 * (safe to read off the event loop without touching JPA).
 */
public record UpstreamSnapshot(
        long id,
        UpstreamProxyType type,
        String host,
        int port,
        String username,
        String password
) {
    public boolean hasAuth() {
        return username != null && !username.isBlank();
    }
}
