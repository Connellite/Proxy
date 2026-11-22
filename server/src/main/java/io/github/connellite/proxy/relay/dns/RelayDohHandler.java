package io.github.connellite.proxy.relay.dns;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * One DNS-over-HTTPS request: GET or POST {@code /dns-query}.
 */
@RequiredArgsConstructor final class RelayDohHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String PATH = "/dns-query";
    private static final String DNS_MESSAGE = "application/dns-message";

    private final RelayDnsServer dns;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        if (!PATH.equals(decoder.path())) {
            write(ctx, request, HttpResponseStatus.NOT_FOUND, null);
            return;
        }
        byte[] query;
        try {
            query = message(request, decoder);
        } catch (ContentTypeException ex) {
            write(ctx, request, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, null);
            return;
        } catch (IllegalArgumentException ex) {
            write(ctx, request, HttpResponseStatus.BAD_REQUEST, null);
            return;
        }
        InetSocketAddress client = ctx.channel().remoteAddress() instanceof InetSocketAddress remote ? remote : null;
        //noinspection resource
        if (!dns.exchange(query, client, wire -> ctx.channel().eventLoop().execute(() -> {
            if (ctx.channel().isActive()) {
                write(ctx, request, HttpResponseStatus.OK, wire);
            }
        }))) {
            write(ctx, request, HttpResponseStatus.FORBIDDEN, null);
        }
    }

    private static byte[] message(FullHttpRequest request, QueryStringDecoder decoder) {
        if (HttpMethod.GET.equals(request.method())) {
            List<String> dns = decoder.parameters().get("dns");
            if (dns == null || dns.isEmpty() || dns.get(0).isBlank()) {
                throw new IllegalArgumentException("missing dns");
            }
            return Base64.getUrlDecoder().decode(pad(dns.get(0).trim()));
        }
        if (!HttpMethod.POST.equals(request.method())) {
            throw new IllegalArgumentException("method");
        }
        String type = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith(DNS_MESSAGE)) {
            throw new ContentTypeException();
        }
        if (request.content().readableBytes() == 0) {
            throw new IllegalArgumentException("empty");
        }
        byte[] body = new byte[request.content().readableBytes()];
        request.content().readBytes(body);
        return body;
    }

    private static void write(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, byte[] body) {
        FullHttpResponse response = body == null
                ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
                : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body));
        if (body != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, DNS_MESSAGE);
        }
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(future -> ctx.close());
        }
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
