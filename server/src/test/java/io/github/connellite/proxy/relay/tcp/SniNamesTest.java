package io.github.connellite.proxy.relay.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SniNamesTest {

    @Test
    void readsHostFromClientHello() {
        byte[] name = "ip.example.ru".getBytes(StandardCharsets.US_ASCII);
        byte[] hello = clientHello(name);
        ByteBuf buf = Unpooled.wrappedBuffer(hello);
        assertThat(SniNames.read(buf)).isEqualTo("ip.example.ru");
        assertThat(buf.readerIndex()).isZero();
        ByteBuf body = Unpooled.wrappedBuffer(hello, 9, hello.length - 9);
        assertThat(SniNames.read(body)).isEqualTo("ip.example.ru");
    }

    private static byte[] clientHello(byte[] name) {
        int extensions = 2 + 2 + 2 + 1 + 2 + name.length;
        int body = 2 + 32 + 1 + 2 + 2 + 1 + 1 + 2 + extensions;
        byte[] out = new byte[5 + 4 + body];
        out[0] = 22;
        out[1] = 3;
        out[2] = 1;
        int record = 4 + body;
        out[3] = (byte) (record >> 8);
        out[4] = (byte) record;
        out[5] = 1;
        out[6] = (byte) (body >> 16);
        out[7] = (byte) (body >> 8);
        out[8] = (byte) body;
        out[9] = 3;
        out[10] = 3;
        int pos = 11 + 32;
        out[pos++] = 0;
        out[pos++] = 0;
        out[pos++] = 2;
        out[pos++] = 0;
        out[pos++] = 0x2f;
        out[pos++] = 1;
        out[pos++] = 0;
        out[pos++] = (byte) (extensions >> 8);
        out[pos++] = (byte) extensions;
        out[pos++] = 0;
        out[pos++] = 0;
        int extBody = 2 + 1 + 2 + name.length;
        out[pos++] = (byte) (extBody >> 8);
        out[pos++] = (byte) extBody;
        int list = extBody - 2;
        out[pos++] = (byte) (list >> 8);
        out[pos++] = (byte) list;
        out[pos++] = 0;
        out[pos++] = (byte) (name.length >> 8);
        out[pos++] = (byte) name.length;
        System.arraycopy(name, 0, out, pos, name.length);
        return out;
    }
}
