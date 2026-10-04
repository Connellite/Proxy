package io.github.connellite.proxy.proxy.outbound;

import io.netty.channel.Channel;

public interface TunnelCallback {

    void onSuccess(Channel outbound);

    void onFailure(Throwable cause);
}
