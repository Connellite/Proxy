package io.github.connellite.proxy.relay.dns;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.relay.RelayAnswers;
import io.github.connellite.proxy.relay.RelayCatalog;
import io.github.connellite.proxy.relay.RelaySnapshot;
import io.github.connellite.proxy.relay.UpstreamResolver;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * UDP and TCP DNS. A matched name is answered from the snapshot. Every other name is forwarded.
 * There is no client list, so anyone who can reach this port can query other names through it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayDnsServer {

    private final RelayCatalog catalog;
    private final ProxyProperties properties;
    private final RelayQueryLog queryLog;
    private final Semaphore forwards = new Semaphore(RelaySnapshot.MAX_FORWARDS);

    private EventLoopGroup group;
    private Channel udp;
    private Channel tcp;

    public synchronized boolean isRunning() {
        return udp != null && udp.isActive();
    }

    public synchronized void start(String bindHost, int port) throws InterruptedException {
        stop();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        udp = new Bootstrap().group(group)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                        byte[] query = new byte[packet.content().readableBytes()];
                        packet.content().readBytes(query);
                        exchange(query, packet.sender(), wire -> {
                            ByteBuf buf = Unpooled.wrappedBuffer(wire);
                            ctx.writeAndFlush(new DatagramPacket(buf, packet.sender()));
                        });
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        tcp = new ServerBootstrap().group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                byte[] query = new byte[msg.readableBytes()];
                                msg.readBytes(query);
                                InetSocketAddress client = ctx.channel().remoteAddress() instanceof InetSocketAddress remote
                                        ? remote : null;
                                if (!exchange(query, client, wire -> {
                                    ByteBuf out = ctx.alloc().buffer(2 + wire.length);
                                    out.writeShort(wire.length);
                                    out.writeBytes(wire);
                                    ctx.writeAndFlush(out).addListener(future -> ctx.close());
                                })) {
                                    ctx.close();
                                }
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DNS relay listening on {}:{}", bindHost, port);
    }

    public synchronized void stop() {
        if (udp != null) {
            udp.close();
            udp = null;
        }
        if (tcp != null) {
            tcp.close();
            tcp = null;
        }
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            group = null;
        }
    }

    /** @return {@code false} when the client address is not on the DNS allowlist. Nothing is sent or logged. */
    public boolean exchange(byte[] query, InetSocketAddress client, Consumer<byte[]> send) {
        if (!catalog.current().dnsAllows(client == null ? null : client.getAddress())) {
            return false;
        }
        long started = System.nanoTime();
        Message message;
        try {
            message = new Message(query);
        } catch (Exception ex) {
            queryLog.record(client, "", "", "FORMERR", "FORMERR", started);
            return true;
        }
        Record question = message.getQuestion();
        if (question == null) {
            send.accept(error(message, Rcode.FORMERR));
            queryLog.record(client, "", "", "FORMERR", "FORMERR", started);
            return true;
        }
        String name = question.getName().toString(true);
        String type = Type.string(question.getType());
        RelaySnapshot snapshot = catalog.current();
        RelaySnapshot.Route route = snapshot.match(question.getName().toString());
        InetAddress relayAddress = snapshot.advertisedAddress();
        RelayAnswers decision = RelayAnswers.decide(route, question.getType(), relayAddress);
        if (decision == RelayAnswers.FORWARD) {
            forward(snapshot, message, question, client, started, send);
            return true;
        }
        if (decision == RelayAnswers.SERVFAIL || route == null) {
            send.accept(error(message, Rcode.SERVFAIL));
            queryLog.record(client, name, type, "SERVFAIL", "SERVFAIL", started);
            return true;
        }
        InetAddress address = route.getMode() == RelayRouteMode.RELAY ? relayAddress : route.getAnswerAddress();
        boolean answered = decision == RelayAnswers.ANSWER && address != null;
        send.accept(synthetic(message, question, answered ? address : null));
        if (!answered) {
            queryLog.record(client, name, type, "No data", "NOERROR", started);
        } else if (route.getMode() == RelayRouteMode.RELAY) {
            queryLog.record(client, name, type, "Relay", address.getHostAddress(), started);
        } else {
            queryLog.record(client, name, type, "Address", address.getHostAddress(), started);
        }
        return true;
    }

    private void forward(RelaySnapshot snapshot, Message query, Record question, InetSocketAddress client,
                         long started, java.util.function.Consumer<byte[]> send) {
        String name = question.getName().toString(true);
        String type = Type.string(question.getType());
        if (!forwards.tryAcquire()) {
            send.accept(error(query, Rcode.SERVFAIL));
            queryLog.record(client, name, type, "SERVFAIL", "SERVFAIL", started);
            return;
        }
        try {
            SimpleResolver resolver = UpstreamResolver.resolver(
                    snapshot.resolverHost(), snapshot.resolverPort(), properties.getConnectTimeoutMs());
            CompletableFuture<Message> future = resolver.sendAsync(query).toCompletableFuture();
            future.orTimeout(Math.max(1, properties.getConnectTimeoutMs()), TimeUnit.MILLISECONDS)
                    .whenComplete((response, error) -> {
                        forwards.release();
                        if (error != null || response == null) {
                            log.debug("DNS forward failed: {}", error == null ? "empty" : error.toString());
                            send.accept(error(query, Rcode.SERVFAIL));
                            queryLog.record(client, name, type, "SERVFAIL", "SERVFAIL", started);
                            return;
                        }
                        try {
                            send.accept(response.toWire(65535));
                            queryLog.record(client, name, type, "Forwarded", RelayQueryLog.answers(response), started);
                        } catch (Exception ex) {
                            send.accept(error(query, Rcode.SERVFAIL));
                            queryLog.record(client, name, type, "SERVFAIL", "SERVFAIL", started);
                        }
                    });
        } catch (Exception ex) {
            forwards.release();
            log.debug("DNS forward failed: {}", ex.toString());
            send.accept(error(query, Rcode.SERVFAIL));
            queryLog.record(client, name, type, "SERVFAIL", "SERVFAIL", started);
        }
    }

    private static byte[] synthetic(Message query, Record question, InetAddress address) {
        Message response = new Message();
        response.getHeader().setID(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setFlag(Flags.AA);
        if (query.getHeader().getFlag(Flags.RD)) {
            response.getHeader().setFlag(Flags.RD);
            response.getHeader().setFlag(Flags.RA);
        }
        response.addRecord(question, Section.QUESTION);
        if (address != null) {
            try {
                Name name = question.getName();
                Record answer = address instanceof Inet4Address
                        ? new ARecord(name, DClass.IN, RelaySnapshot.ANSWER_TTL_SECONDS, address)
                        : new AAAARecord(name, DClass.IN, RelaySnapshot.ANSWER_TTL_SECONDS, address);
                response.addRecord(answer, Section.ANSWER);
            } catch (Exception ex) {
                response.getHeader().setRcode(Rcode.SERVFAIL);
            }
        }
        return wire(response);
    }

    private static byte[] error(Message query, int rcode) {
        Message response = new Message();
        response.getHeader().setID(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setRcode(rcode);
        Record question = query.getQuestion();
        if (question != null) {
            response.addRecord(question, Section.QUESTION);
        }
        return wire(response);
    }

    private static byte[] wire(Message message) {
        try {
            return message.toWire(65535);
        } catch (Exception ex) {
            return new byte[0];
        }
    }
}
