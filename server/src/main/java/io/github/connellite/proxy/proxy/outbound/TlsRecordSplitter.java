package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.experimental.UtilityClass;

import java.util.Random;

/**
 * Cuts one TLS handshake record carrying a ClientHello into two records.
 * The handshake bytes are unchanged; only a second 5-byte record header is added,
 * so a DPI box that reads a single record does not see the whole SNI.
 */
@UtilityClass
public final class TlsRecordSplitter {

    public static final int RECORD_HEADER = 5;
    public static final int CONTENT_HANDSHAKE = 22;
    /** RFC 8446: a TLSPlaintext fragment must not exceed 2^14 bytes. */
    public static final int MAX_FRAGMENT = 1 << 14;
    public static final int MIN_POSITION = 1;
    public static final int MAX_POSITION = 1500;

    private static final int HANDSHAKE_CLIENT_HELLO = 1;
    private static final int EXTENSION_SERVER_NAME = 0;
    private static final int SNI_HOST_NAME = 0;

    /**
     * @return {@code true} if {@code buf} starts with a TLS handshake record header
     * (only the bytes already present are checked).
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean startsWithHandshake(ByteBuf buf) {
        int readable = buf.readableBytes();
        int at = buf.readerIndex();
        if (readable < 1 || buf.getUnsignedByte(at) != CONTENT_HANDSHAKE) {
            return false;
        }
        return readable < 2 || buf.getUnsignedByte(at + 1) == 3;
    }

    /**
     * Length of the whole record (header + fragment) at the reader index,
     * or {@code -1} while the header is incomplete.
     */
    public static int recordLength(ByteBuf buf) {
        if (buf.readableBytes() < RECORD_HEADER) {
            return -1;
        }
        return RECORD_HEADER + buf.getUnsignedShort(buf.readerIndex() + 3);
    }

    /**
     * Split offset inside the record fragment, or {@code -1} when the record is left whole
     * (no SNI for an SNI mode, or the offset falls outside {@code (0, fragmentLength)}).
     *
     * @param fragment handshake bytes of the record, without the 5-byte header
     */
    public static int splitOffset(ByteBuf fragment, ClientHelloSplitMode mode, int position, Random random) {
        int length = fragment.readableBytes();
        int offset;
        if (mode == ClientHelloSplitMode.FIXED) {
            offset = position;
        } else {
            int[] sni = findSni(fragment);
            if (sni == null) {
                return -1;
            }
            int start = sni[0];
            int nameLength = sni[1];
            offset = switch (mode) {
                case BEFORE_SNI -> start;
                case AFTER_SNI -> start + nameLength;
                case MIDDLE_SNI -> start + nameLength / 2;
                case RANDOM_SNI -> nameLength < 2 ? start : start + 1 + random.nextInt(nameLength - 1);
                default -> -1;
            };
        }
        return offset > 0 && offset < length ? offset : -1;
    }

    /**
     * Writes {@code record} as two records cut at {@code offset} inside its fragment.
     * Does not change the reader index of {@code record}.
     */
    public static ByteBuf split(ByteBuf record, int offset, ByteBufAllocator alloc) {
        int at = record.readerIndex();
        int fragmentLength = record.readableBytes() - RECORD_HEADER;
        int type = record.getUnsignedByte(at);
        int version = record.getUnsignedShort(at + 1);
        ByteBuf out = alloc.buffer(record.readableBytes() + RECORD_HEADER);
        out.writeByte(type);
        out.writeShort(version);
        out.writeShort(offset);
        out.writeBytes(record, at + RECORD_HEADER, offset);
        out.writeByte(type);
        out.writeShort(version);
        out.writeShort(fragmentLength - offset);
        out.writeBytes(record, at + RECORD_HEADER + offset, fragmentLength - offset);
        return out;
    }

    /**
     * Locates the first host name of the SNI extension in a ClientHello.
     *
     * @return {@code {offset, length}} relative to the fragment reader index, or {@code null}
     */
    static int[] findSni(ByteBuf fragment) {
        int base = fragment.readerIndex();
        int end = base + fragment.readableBytes();
        int p = base;
        if (p + 4 > end || fragment.getUnsignedByte(p) != HANDSHAKE_CLIENT_HELLO) {
            return null;
        }
        // handshake type (1) + length (3) + client_version (2) + random (32)
        p += 4 + 2 + 32;
        if (p + 1 > end) {
            return null;
        }
        p += 1 + fragment.getUnsignedByte(p);
        if (p + 2 > end) {
            return null;
        }
        p += 2 + fragment.getUnsignedShort(p);
        if (p + 1 > end) {
            return null;
        }
        p += 1 + fragment.getUnsignedByte(p);
        if (p + 2 > end) {
            return null;
        }
        int extensionsEnd = Math.min(end, p + 2 + fragment.getUnsignedShort(p));
        p += 2;
        while (p + 4 <= extensionsEnd) {
            int type = fragment.getUnsignedShort(p);
            int length = fragment.getUnsignedShort(p + 2);
            int data = p + 4;
            if (type == EXTENSION_SERVER_NAME) {
                // server_name_list length (2) + name_type (1) + host_name length (2)
                if (data + 5 > extensionsEnd || fragment.getUnsignedByte(data + 2) != SNI_HOST_NAME) {
                    return null;
                }
                int nameLength = fragment.getUnsignedShort(data + 3);
                int nameStart = data + 5;
                if (nameLength == 0 || nameStart + nameLength > end) {
                    return null;
                }
                return new int[] {nameStart - base, nameLength};
            }
            p = data + length;
        }
        return null;
    }
}
