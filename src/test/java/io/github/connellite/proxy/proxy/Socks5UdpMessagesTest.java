package io.github.connellite.proxy.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Socks5UdpMessagesTest {

    @Test
    void encodesAndDecodesIpv4Payload() throws Exception {
        ByteBuf payload = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8);
        ByteBuf framed = Socks5UdpMessages.encode(
                payload,
                new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 53),
                payload);

        Socks5UdpMessages.Decoded decoded = Socks5UdpMessages.decode(framed);
        try {
            assertThat(decoded.destination().getAddress().getHostAddress()).isEqualTo("8.8.8.8");
            assertThat(decoded.destination().getPort()).isEqualTo(53);
            assertThat(decoded.data().toString(StandardCharsets.UTF_8)).isEqualTo("hello");
        } finally {
            decoded.data().release();
            framed.release();
            payload.release();
        }
    }

    @Test
    void rejectsFragmentedDatagrams() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(0);
        buf.writeByte(1); // FRAG
        buf.writeByte(0x01);
        buf.writeBytes(new byte[] {1, 2, 3, 4});
        buf.writeShort(80);
        buf.writeByte(9);

        assertThrows(IllegalArgumentException.class, () -> Socks5UdpMessages.decode(buf));
        buf.release();
    }
}
