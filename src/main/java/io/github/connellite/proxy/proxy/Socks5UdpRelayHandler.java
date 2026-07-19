package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Relays SOCKS5 UDP ASSOCIATE datagrams for one TCP control connection.
 */
@Slf4j
final class Socks5UdpRelayHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Channel tcpControl;
    private final ProxyMetrics metrics;
    private final Long userId;

    private volatile InetSocketAddress clientEndpoint;

    Socks5UdpRelayHandler(Channel tcpControl, AuthenticatedSession session, ProxyMetrics metrics) {
        this.tcpControl = Objects.requireNonNull(tcpControl, "tcpControl");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.userId = session == null ? null : session.userId();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        tcpControl.closeFuture().addListener(future -> ctx.close());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress sender = packet.sender();
        InetSocketAddress client = clientEndpoint;
        if (client == null || client.equals(sender)) {
            handleFromClient(ctx, packet, sender);
        } else {
            handleFromRemote(ctx, packet, sender, client);
        }
    }

    private void handleFromClient(ChannelHandlerContext ctx, DatagramPacket packet, InetSocketAddress sender) {
        ByteBuf content = packet.content();
        Socks5UdpMessages.Decoded decoded = null;
        try {
            decoded = Socks5UdpMessages.decode(content);
            if (clientEndpoint == null) {
                clientEndpoint = sender;
            }
            int payloadBytes = decoded.data().readableBytes();
            if (payloadBytes > 0) {
                metrics.recordTraffic(userId, payloadBytes, 0);
            }
            ctx.writeAndFlush(new DatagramPacket(decoded.data().retain(), decoded.destination()));
        } catch (Exception ex) {
            log.debug("Dropping invalid SOCKS5 UDP datagram from {}: {}", sender, ex.toString());
        } finally {
            if (decoded != null) {
                ReferenceCountUtil.release(decoded.data());
            }
        }
    }

    private void handleFromRemote(ChannelHandlerContext ctx, DatagramPacket packet,
                                  InetSocketAddress sender, InetSocketAddress client) {
        ByteBuf payload = packet.content();
        ByteBuf framed = null;
        try {
            int payloadBytes = payload.readableBytes();
            if (payloadBytes > 0) {
                metrics.recordTraffic(userId, 0, payloadBytes);
            }
            framed = Socks5UdpMessages.encode(payload, sender, payload);
            ctx.writeAndFlush(new DatagramPacket(framed.retain(), client));
        } catch (Exception ex) {
            log.debug("Failed to wrap SOCKS5 UDP response from {}: {}", sender, ex.toString());
        } finally {
            ReferenceCountUtil.release(framed);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("SOCKS5 UDP relay error: {}", cause.toString());
        ctx.close();
        RelayHandler.closeOnFlush(tcpControl);
    }
}
