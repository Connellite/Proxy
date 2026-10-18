package io.github.connellite.proxy.dto;

/**
 * Result of issuing a key. {@code privateKey} exists only in this object and must not be logged or stored.
 */
public record IssuedSshKey(
        long id,
        String fingerprint,
        String publicKey,
        String privateKey,
        boolean encrypted
) {
}
