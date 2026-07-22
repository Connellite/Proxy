package io.github.connellite.proxy.proxy.socks;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import lombok.experimental.UtilityClass;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * SOCKS5 UDP request/response framing (RFC 1928 §7). Fragmentation is not supported.
 */
@UtilityClass
final class Socks5UdpMessages {

    static Decoded decode(ByteBuf content) throws Exception {
        if (content.readableBytes() < 4) {
            throw new IllegalArgumentException("UDP datagram too short for SOCKS5 header");
        }
        content.skipBytes(2); // RSV
        byte frag = content.readByte();
        if (frag != 0) {
            throw new IllegalArgumentException("SOCKS5 UDP fragmentation is not supported");
        }
        Socks5AddressType atyp = Socks5AddressType.valueOf(content.readByte());
        String host = Socks5AddressDecoder.DEFAULT.decodeAddress(atyp, content);
        int port = content.readUnsignedShort();
        ByteBuf data = content.readRetainedSlice(content.readableBytes());
        return new Decoded(new InetSocketAddress(host, port), data);
    }

    static ByteBuf encode(ByteBuf allocatorBuf, InetSocketAddress source, ByteBuf payload) throws Exception {
        Socks5AddressType atyp = addressType(source.getAddress());
        String host = normalizeHost(source.getAddress());
        ByteBuf out = allocatorBuf.alloc().buffer(4 + 16 + 2 + payload.readableBytes());
        out.writeShort(0);
        out.writeByte(0);
        out.writeByte(atyp.byteValue());
        Socks5AddressEncoder.DEFAULT.encodeAddress(atyp, host, out);
        out.writeShort(source.getPort());
        out.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        return out;
    }

    static Socks5AddressType addressType(InetAddress address) {
        return address instanceof Inet6Address ? Socks5AddressType.IPv6 : Socks5AddressType.IPv4;
    }

    static String normalizeHost(InetAddress address) {
        String host = address.getHostAddress();
        int zone = host.indexOf('%');
        return zone >= 0 ? host.substring(0, zone) : host;
    }

    record Decoded(InetSocketAddress destination, ByteBuf data) {
    }
}
