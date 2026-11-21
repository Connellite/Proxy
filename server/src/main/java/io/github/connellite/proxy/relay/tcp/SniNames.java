package io.github.connellite.proxy.relay.tcp;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/** Reads the host name from a TLS ClientHello. Does not move the reader index. */
public final class SniNames {

    private SniNames() {
    }

    /** {@code null} when the hello is still incomplete. Empty when the hello has no SNI. */
    public static String read(ByteBuf buf) {
        int index = buf.readerIndex();
        int end = buf.writerIndex();
        if (end - index < 4) {
            return null;
        }
        int first = buf.getUnsignedByte(index);
        if (first == 1) {
            return handshake(buf, index, end);
        }
        if (first != 22) {
            return body(buf, index, end);
        }
        if (end - index < 5) {
            return null;
        }
        int recordLength = buf.getUnsignedShort(index + 3);
        if (end - index < 5 + recordLength) {
            return null;
        }
        return handshake(buf, index + 5, index + 5 + recordLength);
    }

    private static String handshake(ByteBuf buf, int pos, int limit) {
        if (pos >= limit || buf.getByte(pos) != 1) {
            return "";
        }
        return body(buf, pos + 4, limit);
    }

    /** ClientHello body as Netty's {@code SslClientHelloHandler} passes it: version, then random. */
    private static String body(ByteBuf buf, int pos, int limit) {
        pos += 2 + 32;
        if (pos >= limit) {
            return "";
        }
        int session = buf.getUnsignedByte(pos);
        pos += 1 + session;
        if (pos + 2 > limit) {
            return "";
        }
        int ciphers = buf.getUnsignedShort(pos);
        pos += 2 + ciphers;
        if (pos >= limit) {
            return "";
        }
        int compression = buf.getUnsignedByte(pos);
        pos += 1 + compression;
        if (pos + 2 > limit) {
            return "";
        }
        int extensions = buf.getUnsignedShort(pos);
        pos += 2;
        int extEnd = Math.min(limit, pos + extensions);
        while (pos + 4 <= extEnd) {
            int type = buf.getUnsignedShort(pos);
            int length = buf.getUnsignedShort(pos + 2);
            pos += 4;
            if (pos + length > extEnd) {
                return "";
            }
            if (type == 0) {
                return hostName(buf, pos, pos + length);
            }
            pos += length;
        }
        return "";
    }

    private static String hostName(ByteBuf buf, int start, int end) {
        if (start + 2 > end) {
            return "";
        }
        int list = buf.getUnsignedShort(start);
        int pos = start + 2;
        int listEnd = Math.min(end, pos + list);
        while (pos + 3 <= listEnd) {
            int type = buf.getUnsignedByte(pos);
            int length = buf.getUnsignedShort(pos + 1);
            pos += 3;
            if (pos + length > listEnd) {
                return "";
            }
            if (type == 0) {
                return buf.toString(pos, length, StandardCharsets.US_ASCII);
            }
            pos += length;
        }
        return "";
    }
}
