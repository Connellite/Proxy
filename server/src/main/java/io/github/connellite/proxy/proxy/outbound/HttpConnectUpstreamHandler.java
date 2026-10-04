package io.github.connellite.proxy.proxy.outbound;

import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class HttpConnectUpstreamHandler extends ChannelInitializer<SocketChannel> {

    private final UpstreamSnapshot upstream;
    private final String authority;
    private final TunnelCallback callback;
    private final int outboundTtl;

    HttpConnectUpstreamHandler(UpstreamSnapshot upstream, String authority,
                               TunnelCallback callback, int outboundTtl) {
        this.upstream = upstream;
        this.authority = authority;
        this.callback = callback;
        this.outboundTtl = outboundTtl;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        OutboundIpTtl.apply(ch, outboundTtl);
        configureTransport(ch);
        ch.pipeline().addLast(new HttpClientCodec());
        ch.pipeline().addLast(new HttpObjectAggregator(8192));
        ch.pipeline().addLast(new ConnectResponseHandler());
    }

    /**
     * HTTP connects in the clear. HTTPS overrides this and inserts TLS first.
     */
    protected void configureTransport(SocketChannel ch) {
    }

    protected String scheme() {
        return "HTTP";
    }

    protected UpstreamSnapshot upstream() {
        return upstream;
    }

    private final class ConnectResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        private boolean finished;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            DefaultFullHttpRequest connect = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.CONNECT,
                    authority,
                    ctx.alloc().buffer(0),
                    new DefaultHttpHeaders(),
                    new DefaultHttpHeaders());
            connect.headers().set(HttpHeaderNames.HOST, authority);
            connect.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            if (upstream.hasAuth()) {
                String credentials = nullToEmpty(upstream.username()) + ":" + nullToEmpty(upstream.password());
                String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                connect.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic " + token);
            }
            ctx.writeAndFlush(connect);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            if (finished) {
                return;
            }
            finished = true;
            if (msg.status().code() == 200) {
                ChannelPipeline pipeline = ctx.pipeline();
                pipeline.remove(this);
                removeIfPresent(pipeline, HttpObjectAggregator.class);
                removeIfPresent(pipeline, HttpClientCodec.class);
                callback.onSuccess(ctx.channel());
            } else {
                ctx.close();
                callback.onFailure(new IllegalStateException(
                        "Upstream " + scheme() + " CONNECT failed: " + msg.status()));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!finished) {
                finished = true;
                callback.onFailure(cause);
            }
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!finished) {
                finished = true;
                callback.onFailure(new IllegalStateException("Upstream " + scheme() + " connection closed"));
            }
        }
    }

    private static void removeIfPresent(ChannelPipeline pipeline, Class<? extends ChannelHandler> type) {
        try {
            if (pipeline.get(type) != null) {
                pipeline.remove(type);
            }
        } catch (Exception ignored) {
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
