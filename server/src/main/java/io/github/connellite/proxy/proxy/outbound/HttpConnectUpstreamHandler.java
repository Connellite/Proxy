package io.github.connellite.proxy.proxy.outbound;

import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class HttpConnectUpstreamHandler extends ChannelInitializer<SocketChannel> {

    private final UpstreamSnapshot upstream;
    private final String authority;
    private final TunnelCallback callback;
    private final int outboundTtl;
    private final OutboundTweaks tweaks;

    HttpConnectUpstreamHandler(UpstreamSnapshot upstream, String authority,
                               TunnelCallback callback, int outboundTtl, OutboundTweaks tweaks) {
        this.upstream = upstream;
        this.authority = authority;
        this.callback = callback;
        this.outboundTtl = outboundTtl;
        this.tweaks = tweaks;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        OutboundIpTtl.apply(ch, outboundTtl);
        tweaks.install(ch.pipeline(), firstRecordIsUpstreamHello());
        configureTransport(ch);
        ch.pipeline().addLast(new ConnectResponseDecoder());
        ch.pipeline().addLast(HttpHeaderMangleEncoder.forChannel(ch));
        ch.pipeline().addLast(new HttpObjectAggregator(8192));
        ch.pipeline().addLast(new ConnectResponseHandler());
        QuietCloseHandlerAdapter.installLast(ch.pipeline());
    }

    /**
     * HTTP connects in the clear. HTTPS overrides this and inserts TLS first.
     */
    protected void configureTransport(SocketChannel ch) {
    }

    /**
     * {@code true} when this proxy's own TLS ClientHello to the upstream is the first handshake record
     * on the socket. Plain HTTP waits for the CONNECT reply before the client's ClientHello.
     */
    protected boolean firstRecordIsUpstreamHello() {
        return false;
    }

    protected String scheme() {
        return "HTTP";
    }

    protected UpstreamSnapshot upstream() {
        return upstream;
    }

    /**
     * CONNECT is the only request on this socket, so a 2xx reply has no body even without Content-Length.
     */
    private static final class ConnectResponseDecoder extends HttpResponseDecoder {

        @Override
        protected boolean isContentAlwaysEmpty(HttpMessage msg) {
            return ((HttpResponse) msg).status().codeClass() == HttpStatusClass.SUCCESS
                    || super.isContentAlwaysEmpty(msg);
        }
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
                removeIfPresent(pipeline, HttpResponseDecoder.class);
                removeIfPresent(pipeline, HttpRequestEncoder.class);
                ClientHelloSplitHandler.arm(pipeline);
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
