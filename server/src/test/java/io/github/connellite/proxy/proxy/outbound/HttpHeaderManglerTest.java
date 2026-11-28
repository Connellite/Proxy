package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeaderManglerTest {

    @Test
    void rewritesConnectHead() {
        String in = "CONNECT example.com:443 HTTP/1.1\r\n"
                + "host: example.com:443\r\n"
                + "proxy-authorization: Basic dXNlcjpwYXNz\r\n"
                + "\r\n";

        String out = mangle(in);

        assertThat(out).isEqualTo("CONNECT  example.com:443 HTTP/1.1\r\n"
                + "hoSt:eXaMpLe.cOm:443\r\n"
                + "proxy-authorization: Basic dXNlcjpwYXNz\r\n"
                + "\r\n");
    }

    @Test
    void keepsBodyAfterHead() {
        String in = "POST /form HTTP/1.1\r\nHost: Example.com\r\nContent-Length: 4\r\n\r\nHost";

        String out = mangle(in);

        assertThat(out).isEqualTo("POST  /form HTTP/1.1\r\nhoSt:eXaMpLe.cOm\r\nContent-Length: 4\r\n\r\nHost");
    }

    @Test
    void leavesNonHttpAlone() {
        ByteBuf tls = Unpooled.wrappedBuffer(ClientHelloSplitHandlerTest.clientHelloRecord("example.com"));
        ByteBuf incomplete = Unpooled.copiedBuffer("GET / HTTP/1.1\r\nHost: a", StandardCharsets.ISO_8859_1);

        assertThat(HttpHeaderMangler.mangle(tls, ByteBufAllocator.DEFAULT)).isNull();
        assertThat(HttpHeaderMangler.mangle(incomplete, ByteBufAllocator.DEFAULT)).isNull();
        tls.release();
        incomplete.release();
    }

    @Test
    void handlerPassesTlsUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpHeaderMangleHandler());
        byte[] record = ClientHelloSplitHandlerTest.clientHelloRecord("example.com");

        channel.writeOutbound(Unpooled.wrappedBuffer(record));
        ByteBuf out = channel.readOutbound();
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        out.release();

        assertThat(bytes).isEqualTo(record);
        channel.finishAndReleaseAll();
    }

    private static String mangle(String in) {
        ByteBuf buf = Unpooled.copiedBuffer(in, StandardCharsets.ISO_8859_1);
        ByteBuf out = HttpHeaderMangler.mangle(buf, ByteBufAllocator.DEFAULT);
        buf.release();
        assertThat(out).isNotNull();
        String result = out.toString(StandardCharsets.ISO_8859_1);
        out.release();
        return result;
    }
}
