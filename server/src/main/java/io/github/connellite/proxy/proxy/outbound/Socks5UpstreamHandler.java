package io.github.connellite.proxy.proxy.outbound;

import com.google.common.net.InetAddresses;
import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;

final class Socks5UpstreamHandler extends SimpleChannelInboundHandler<Object> {

    private enum Phase { INIT, AUTH, COMMAND, DONE }

    private final UpstreamSnapshot upstream;
    private final String targetHost;
    private final int targetPort;
    private final boolean wantAuth;
    private final TunnelCallback callback;
    private Phase phase = Phase.INIT;
    private boolean finished;

    Socks5UpstreamHandler(UpstreamSnapshot upstream, String targetHost, int targetPort,
                          boolean wantAuth, TunnelCallback callback) {
        this.upstream = upstream;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.wantAuth = wantAuth;
        this.callback = callback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (wantAuth) {
            ctx.writeAndFlush(new DefaultSocks5InitialRequest(Arrays.asList(
                    Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD)));
        } else {
            ctx.writeAndFlush(new DefaultSocks5InitialRequest(
                    Collections.singletonList(Socks5AuthMethod.NO_AUTH)));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (finished) {
            return;
        }
        if (phase == Phase.INIT && msg instanceof Socks5InitialResponse response) {
            handleInitial(ctx, response);
        } else if (phase == Phase.AUTH && msg instanceof Socks5PasswordAuthResponse response) {
            handleAuth(ctx, response);
        } else if (phase == Phase.COMMAND && msg instanceof Socks5CommandResponse response) {
            handleCommand(ctx, response);
        } else {
            fail(ctx, new IllegalStateException("Unexpected SOCKS5 response: " + msg.getClass().getSimpleName()));
        }
    }

    private void handleInitial(ChannelHandlerContext ctx, Socks5InitialResponse response) {
        Socks5AuthMethod method = response.authMethod();
        removeIfPresent(ctx.pipeline(), Socks5InitialResponseDecoder.class);
        if (method == Socks5AuthMethod.PASSWORD) {
            if (!wantAuth || StringUtils.isBlank(upstream.username())) {
                fail(ctx, new IllegalStateException("Upstream SOCKS5 requires username/password"));
                return;
            }
            phase = Phase.AUTH;
            ctx.pipeline().addBefore(ctx.name(), null, new Socks5PasswordAuthResponseDecoder());
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthRequest(
                    upstream.username(),
                    nullToEmpty(upstream.password())));
        } else if (method == Socks5AuthMethod.NO_AUTH) {
            sendConnect(ctx);
        } else {
            fail(ctx, new IllegalStateException("Upstream SOCKS5 auth method unsupported: " + method));
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, Socks5PasswordAuthResponse response) {
        if (response.status() != Socks5PasswordAuthStatus.SUCCESS) {
            fail(ctx, new IllegalStateException("Upstream SOCKS5 authentication failed"));
            return;
        }
        removeIfPresent(ctx.pipeline(), Socks5PasswordAuthResponseDecoder.class);
        sendConnect(ctx);
    }

    private void sendConnect(ChannelHandlerContext ctx) {
        phase = Phase.COMMAND;
        removeIfPresent(ctx.pipeline(), Socks5InitialResponseDecoder.class);
        ctx.pipeline().addBefore(ctx.name(), null, new Socks5CommandResponseDecoder());
        ctx.writeAndFlush(new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT, resolveAddressType(targetHost), targetHost, targetPort));
    }

    private void handleCommand(ChannelHandlerContext ctx, Socks5CommandResponse response) {
        if (response.status() != Socks5CommandStatus.SUCCESS) {
            fail(ctx, new IllegalStateException("Upstream SOCKS5 CONNECT failed: " + response.status()));
            return;
        }
        finished = true;
        phase = Phase.DONE;
        stripSocksClientHandlers(ctx.pipeline());
        callback.onSuccess(ctx.channel());
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        if (finished) {
            return;
        }
        finished = true;
        callback.onFailure(cause);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(ctx, cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!finished) {
            fail(ctx, new IllegalStateException("Upstream SOCKS5 connection closed"));
        }
    }

    private static Socks5AddressType resolveAddressType(String host) {
        try {
            if (InetAddresses.isInetAddress(host)) {
                byte[] bytes = InetAddresses.forString(host).getAddress();
                if (bytes.length == 4) {
                    return Socks5AddressType.IPv4;
                }
                if (bytes.length == 16) {
                    return Socks5AddressType.IPv6;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // treat as domain
        }
        return Socks5AddressType.DOMAIN;
    }

    private static void stripSocksClientHandlers(ChannelPipeline pipeline) {
        removeIfPresent(pipeline, Socks5CommandResponseDecoder.class);
        removeIfPresent(pipeline, Socks5PasswordAuthResponseDecoder.class);
        removeIfPresent(pipeline, Socks5InitialResponseDecoder.class);
        removeIfPresent(pipeline, Socks5ClientEncoder.class);
        for (String name : pipeline.names()) {
            if (pipeline.get(name) instanceof Socks5UpstreamHandler) {
                try {
                    pipeline.remove(name);
                } catch (Exception ignored) {
                }
                break;
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
