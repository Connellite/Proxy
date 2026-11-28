package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeaderMangleEncoderTest {

    @Test
    void rewritesConnectHead() {
        FullHttpRequest connect = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "example.com:443");
        connect.headers().set(HttpHeaderNames.HOST, "example.com:443");
        connect.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic dXNlcjpwYXNz");

        assertThat(encode(connect)).isEqualTo("CONNECT  example.com:443 HTTP/1.1\r\n"
                + "hoSt:eXaMpLe.cOm:443\r\n"
                + "proxy-authorization: Basic dXNlcjpwYXNz\r\n"
                + "\r\n");
    }

    @Test
    void keepsBody() {
        FullHttpRequest post = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/form",
                Unpooled.copiedBuffer("Host", StandardCharsets.ISO_8859_1));
        post.headers().set("Host", "Example.com");
        post.headers().set("Content-Length", 4);

        assertThat(encode(post)).isEqualTo("POST  /form HTTP/1.1\r\n"
                + "hoSt:eXaMpLe.cOm\r\n"
                + "Content-Length: 4\r\n"
                + "\r\n"
                + "Host");
    }

    @Test
    void stockEncoderUnlessEnabled() {
        EmbeddedChannel plain = new EmbeddedChannel();
        EmbeddedChannel enabled = new EmbeddedChannel();
        HttpHeaderMangleEncoder.enable(enabled);

        assertThat(HttpHeaderMangleEncoder.forChannel(plain)).isExactlyInstanceOf(HttpRequestEncoder.class);
        assertThat(HttpHeaderMangleEncoder.forChannel(enabled)).isExactlyInstanceOf(HttpHeaderMangleEncoder.class);
        plain.finishAndReleaseAll();
        enabled.finishAndReleaseAll();
    }

    private static String encode(FullHttpRequest request) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpHeaderMangleEncoder());
        channel.writeOutbound(request);
        StringBuilder out = new StringBuilder();
        ByteBuf buf;
        while ((buf = channel.readOutbound()) != null) {
            out.append(buf.toString(StandardCharsets.ISO_8859_1));
            buf.release();
        }
        channel.finishAndReleaseAll();
        return out.toString();
    }
}
