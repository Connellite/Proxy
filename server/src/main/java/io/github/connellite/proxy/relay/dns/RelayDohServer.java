package io.github.connellite.proxy.relay.dns;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.github.connellite.proxy.relay.RelayCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** DNS-over-HTTPS. GET and POST {@code /dns-query} with {@code application/dns-message}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayDohServer {

    private static final String PATH = "/dns-query";
    private static final String DNS_MESSAGE = "application/dns-message";

    private final RelayDnsServer dns;
    private final RelayCatalog catalog;

    private EventLoopGroup group;
    private Channel server;

    public synchronized void start(String bindHost, int port, SslContext ssl) throws InterruptedException {
        stop();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        server = new ServerBootstrap().group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc()));
                        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                    ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
                                    ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                        @Override
                                        protected void initChannel(Http2StreamChannel stream) {
                                            stream.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                                            stream.pipeline().addLast(new HttpObjectAggregator(65535));
                                            stream.pipeline().addLast(new DohHandler());
                                        }
                                    }));
                                    return;
                                }
                                ctx.pipeline().addLast(new HttpServerCodec());
                                ctx.pipeline().addLast(new HttpObjectAggregator(65535));
                                ctx.pipeline().addLast(new DohHandler());
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DoH listening on {}:{}", bindHost, port);
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

    private final class DohHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
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
            if (!catalog.current().dnsAllows(client == null ? null : client.getAddress())) {
                write(ctx, request, HttpResponseStatus.FORBIDDEN, null);
                return;
            }
            //noinspection resource
            dns.exchange(query, client, wire -> ctx.channel().eventLoop().execute(() -> {
                if (ctx.channel().isActive()) {
                    write(ctx, request, HttpResponseStatus.OK, wire);
                }
            }));
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
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }

    private static final class ContentTypeException extends IllegalArgumentException {
    }
}
