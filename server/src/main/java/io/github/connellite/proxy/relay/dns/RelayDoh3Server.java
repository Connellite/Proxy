package io.github.connellite.proxy.relay.dns;

import io.github.connellite.proxy.config.ProxyProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** DNS-over-HTTP/3. GET and POST {@code /dns-query} on QUIC with ALPN {@code h3}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayDoh3Server {

    private static final String PATH = "/dns-query";
    private static final String DNS_MESSAGE = "application/dns-message";
    private static final int MAX_BODY = 65535;
    private static final AttributeKey<Request> REQUEST = AttributeKey.newInstance("doh3Request");

    private final RelayDnsServer dns;
    private final ProxyProperties properties;
    private final StreamHandler streams = new StreamHandler();

    private EventLoopGroup group;
    private Channel server;

    public synchronized void start(String bindHost, int port, QuicSslContext ssl) throws InterruptedException {
        if (!Quic.isAvailable()) {
            Throwable cause = Quic.unavailabilityCause();
            throw new IllegalStateException("DoH3 native library is not available", cause);
        }
        stop();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        long idleMillis = Math.max(1, properties.getIdleTimeoutSeconds()) * 1000L;
        server = new Bootstrap().group(group)
                .channel(NioDatagramChannel.class)
                .handler(Http3.newQuicServerCodecBuilder()
                        .sslContext(ssl)
                        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                        .maxIdleTimeout(idleMillis, TimeUnit.MILLISECONDS)
                        .initialMaxData(10_000_000)
                        .initialMaxStreamDataBidirectionalLocal(1_000_000)
                        .initialMaxStreamDataBidirectionalRemote(1_000_000)
                        .initialMaxStreamsBidirectional(100)
                        .handler(new ChannelInitializer<QuicChannel>() {
                            @Override
                            protected void initChannel(QuicChannel ch) {
                                ch.pipeline().addLast(new Http3ServerConnectionHandler(streams));
                            }
                        })
                        .build())
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DoH3 listening on {}:{}", bindHost, port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            group = null;
        }
    }

    @ChannelHandler.Sharable
    private final class StreamHandler extends Http3RequestStreamInboundHandler {
        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
            request(ctx).headers = frame.headers();
            finishIfComplete(ctx);
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
            try {
                Request request = request(ctx);
                int next = request.body.readableBytes() + frame.content().readableBytes();
                if (next > MAX_BODY) {
                    request.tooBig = true;
                    return;
                }
                request.body.writeBytes(frame.content());
            } finally {
                frame.release();
            }
            finishIfComplete(ctx);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
            finish(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            release(ctx);
            super.channelInactive(ctx);
        }

        private void finishIfComplete(ChannelHandlerContext ctx) {
            Request request = ctx.channel().attr(REQUEST).get();
            if (request == null || request.headers == null || request.done || request.tooBig) {
                return;
            }
            CharSequence length = request.headers.get("content-length");
            if (length == null) {
                return;
            }
            try {
                if (request.body.readableBytes() >= Integer.parseInt(length.toString())) {
                    finish(ctx);
                }
            } catch (NumberFormatException ignored) {
                // Wait for the client to close the request stream.
            }
        }

        private void finish(ChannelHandlerContext ctx) {
            Request request = ctx.channel().attr(REQUEST).get();
            if (request == null || request.done) {
                return;
            }
            request.done = true;
            try {
                if (request.tooBig || request.headers == null) {
                    respond(ctx, "400", null);
                    return;
                }
                QueryStringDecoder decoder = new QueryStringDecoder(text(request.headers.path()));
                if (!PATH.equals(decoder.path())) {
                    respond(ctx, "404", null);
                    return;
                }
                byte[] query;
                try {
                    query = message(request, decoder);
                } catch (ContentTypeException ex) {
                    respond(ctx, "415", null);
                    return;
                } catch (IllegalArgumentException ex) {
                    respond(ctx, "400", null);
                    return;
                }
                InetSocketAddress client = client(ctx);
                //noinspection resource
                if (!dns.exchange(query, client, wire -> ctx.channel().eventLoop().execute(() -> {
                    if (ctx.channel().isActive()) {
                        respond(ctx, "200", wire);
                    }
                }))) {
                    respond(ctx, "403", null);
                }
            } finally {
                release(ctx);
            }
        }

        private Request request(ChannelHandlerContext ctx) {
            Request request = ctx.channel().attr(REQUEST).get();
            if (request == null) {
                request = new Request(ctx.alloc().buffer());
                ctx.channel().attr(REQUEST).set(request);
            }
            return request;
        }
    }

    private static byte[] message(Request request, QueryStringDecoder decoder) {
        CharSequence method = request.headers.method();
        if (method != null && "GET".contentEquals(method)) {
            List<String> dns = decoder.parameters().get("dns");
            if (dns == null || dns.isEmpty() || dns.get(0).isBlank()) {
                throw new IllegalArgumentException("missing dns");
            }
            return Base64.getUrlDecoder().decode(pad(dns.get(0).trim()));
        }
        if (method == null || !"POST".contentEquals(method)) {
            throw new IllegalArgumentException("method");
        }
        CharSequence type = request.headers.get("content-type");
        if (type == null || !type.toString().toLowerCase(Locale.ROOT).startsWith(DNS_MESSAGE)) {
            throw new ContentTypeException();
        }
        if (request.body.readableBytes() == 0) {
            throw new IllegalArgumentException("empty");
        }
        byte[] body = new byte[request.body.readableBytes()];
        request.body.readBytes(body);
        return body;
    }

    private static void respond(ChannelHandlerContext ctx, String status, byte[] body) {
        Http3Headers headers = new DefaultHttp3Headers();
        headers.status(status);
        if (body != null) {
            headers.add("content-type", DNS_MESSAGE);
            headers.add("content-length", Integer.toString(body.length));
        }
        ctx.write(new DefaultHttp3HeadersFrame(headers));
        ByteBuf content = body == null ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body);
        ctx.writeAndFlush(new DefaultHttp3DataFrame(content)).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }

    private static InetSocketAddress client(ChannelHandlerContext ctx) {
        if (!(ctx.channel() instanceof QuicStreamChannel stream)) {
            return null;
        }
        QuicChannel parent = stream.parent();
        SocketAddress remote = parent == null ? null : parent.remoteSocketAddress();
        return remote instanceof InetSocketAddress inet ? inet : null;
    }

    private static void release(ChannelHandlerContext ctx) {
        Request request = ctx.channel().attr(REQUEST).getAndSet(null);
        if (request != null) {
            request.body.release();
        }
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }

    private static final class Request {
        private Http3Headers headers;
        private final ByteBuf body;
        private boolean tooBig;
        private boolean done;

        private Request(ByteBuf body) {
            this.body = body;
        }
    }
}
