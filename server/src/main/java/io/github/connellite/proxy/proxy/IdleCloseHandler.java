package io.github.connellite.proxy.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

public final class IdleCloseHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            RelayHandler.closeOnFlush(ctx.channel());
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
