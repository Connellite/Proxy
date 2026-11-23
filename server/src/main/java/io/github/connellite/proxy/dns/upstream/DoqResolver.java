package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import org.xbill.DNS.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * DNS-over-QUIC client: one length-prefixed DNS message per bidirectional stream.
 *
 * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doq.go
 */
public final class DoqResolver extends AbstractUpstreamDnsResolver {

    private final String host;
    private final int port;

    public DoqResolver(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    protected String protocol() {
        return "DoQ";
    }

    @Override
    protected void send(Message query, CompletableFuture<Message> future) throws Exception {
        QuicUpstream.requireAvailable("DoQ");
        long timeoutMs = timeoutMs();
        // RFC 9250 §4.2.1: the DNS Message ID MUST be set to 0.
        byte[] wire = withZeroId(query).toWire(65535);
        QuicSslContext ssl = QuicUpstream.doqContext();
        Channel datagram = new Bootstrap()
                .group(group())
                .channel(NioDatagramChannel.class)
                .handler(new QuicClientCodecBuilder()
                        .sslContext(ssl)
                        .sslEngineProvider(QuicUpstream.engineFor(ssl, host, port))
                        .maxIdleTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .initialMaxData(1_000_000)
                        .initialMaxStreamDataBidirectionalLocal(1_000_000)
                        .initialMaxStreamDataBidirectionalRemote(1_000_000)
                        .initialMaxStreamsBidirectional(1)
                        .build())
                .bind(0).sync().channel();
        future.whenComplete((r, e) -> datagram.close());
        if (future.isDone()) {
            return;
        }
        QuicChannel quic = QuicChannel.newBootstrap(datagram)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.close();
                    }
                })
                .remoteAddress(QuicUpstream.resolve(host, port))
                .connect()
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((r, e) -> quic.close());
        QuicStreamChannel stream = quic.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                byte[] bytes = new byte[msg.readableBytes()];
                                msg.readBytes(bytes);
                                try {
                                    future.complete(response(query, bytes, 0));
                                } catch (Exception ex) {
                                    future.completeExceptionally(ex);
                                } finally {
                                    ctx.close();
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                future.completeExceptionally(cause);
                                ctx.close();
                            }
                        });
                    }
                }).get(timeoutMs, TimeUnit.MILLISECONDS);
        ByteBuf out = stream.alloc().buffer(2 + wire.length);
        out.writeShort(wire.length);
        out.writeBytes(wire);
        // RFC 9250 §4.2: the client MUST indicate STREAM FIN after the query.
        stream.writeAndFlush(out).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
}
