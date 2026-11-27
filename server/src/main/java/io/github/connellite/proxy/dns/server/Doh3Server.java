package io.github.connellite.proxy.dns.server;

import io.github.connellite.proxy.ServerListener;
import io.github.connellite.proxy.config.ProxyProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** DNS-over-HTTP/3. GET and POST {@code /dns-query} on QUIC with ALPN {@code h3}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class Doh3Server implements ServerListener {

    private final DnsServer dns;
    private final ProxyProperties properties;

    private EventLoopGroup group;
    private Channel server;

    @Override
    public synchronized boolean isRunning() {
        return server != null && server.isActive();
    }

    @Override
    public String getProtocol() {
        return "DoH3";
    }

    public synchronized void start(String bindHost, int port, QuicSslContext ssl) throws InterruptedException {
        if (!Quic.isAvailable()) {
            Throwable cause = Quic.unavailabilityCause();
            throw new IllegalStateException("DoH3 native library is not available", cause);
        }
        stop();
        Doh3StreamHandler streams = new Doh3StreamHandler(dns, getProtocol());
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

    @Override
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
}
