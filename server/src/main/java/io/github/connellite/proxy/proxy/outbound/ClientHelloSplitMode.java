package io.github.connellite.proxy.proxy.outbound;

/**
 * Where the first TLS handshake record is cut in two.
 */
public enum ClientHelloSplitMode {

    /** At a fixed byte offset inside the record fragment. */
    FIXED,
    /** Right before the SNI host name. */
    BEFORE_SNI,
    /** Right after the SNI host name. */
    AFTER_SNI,
    /** In the middle of the SNI host name. */
    MIDDLE_SNI,
    /** At a random point strictly inside the SNI host name. */
    RANDOM_SNI;

    public static ClientHelloSplitMode fromWire(String value) {
        if (value == null || value.isBlank()) {
            return FIXED;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FIXED;
        }
    }
}
