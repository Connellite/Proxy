package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

import java.util.Iterator;
import java.util.Map.Entry;

import static io.netty.handler.codec.http.HttpConstants.COLON;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;

/**
 * {@link HttpRequestEncoder} that writes the request head the way GoodbyeDPI does with {@code -a -r -s -m}:
 * <ul>
 *     <li>an extra space between the method and the request target;</li>
 *     <li>{@code Host} becomes {@code hoSt};</li>
 *     <li>no space after the colon of that header;</li>
 *     <li>the Host value in mixed case.</li>
 * </ul>
 * Works on the request object, so only HTTP this proxy encodes itself is changed. Servers may reject such a request.
 */
public final class HttpHeaderMangleEncoder extends HttpRequestEncoder {

    private static final AttributeKey<Boolean> ENABLED =
            AttributeKey.valueOf(HttpHeaderMangleEncoder.class, "enabled");
    private static final byte[] HOST = {'h', 'o', 'S', 't', ':'};

    /**
     * @return this encoder if the upstream socket behind {@code channel} carries plain HTTP to be changed,
     * otherwise the stock one
     */
    public static HttpRequestEncoder forChannel(Channel channel) {
        return Boolean.TRUE.equals(channel.attr(ENABLED).get())
                ? new HttpHeaderMangleEncoder()
                : new HttpRequestEncoder();
    }

    static void enable(Channel channel) {
        channel.attr(ENABLED).set(Boolean.TRUE);
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpRequest request) throws Exception {
        int afterMethod = buf.writerIndex() + request.method().asciiName().length();
        super.encodeInitialLine(buf, request);
        byte[] rest = ByteBufUtil.getBytes(buf, afterMethod, buf.writerIndex() - afterMethod);
        buf.writerIndex(afterMethod);
        buf.writeByte(SP).writeBytes(rest);
    }

    @Override
    protected void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
        Iterator<Entry<CharSequence, CharSequence>> iter = headers.iteratorCharSequence();
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> header = iter.next();
            if (HttpHeaderNames.HOST.contentEqualsIgnoreCase(header.getKey())) {
                buf.writeBytes(HOST);
                buf.writeCharSequence(mixCase(header.getValue()), CharsetUtil.US_ASCII);
            } else {
                buf.writeCharSequence(header.getKey(), CharsetUtil.US_ASCII);
                buf.writeByte(COLON).writeByte(SP);
                buf.writeCharSequence(header.getValue(), CharsetUtil.US_ASCII);
            }
            buf.writeByte(CR).writeByte(LF);
        }
    }

    static String mixCase(CharSequence value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(i % 2 == 0 ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return out.toString();
    }
}
