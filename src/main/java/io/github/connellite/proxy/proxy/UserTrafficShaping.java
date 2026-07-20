package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

/**
 * Installs Netty per-channel traffic shaping for authenticated users with speed caps.
 * Netty treats {@code 0} as unlimited; our model uses {@code < 0}.
 */
final class UserTrafficShaping {

    private static final String HANDLER_NAME = "userTrafficShaping";
    private static final long CHECK_INTERVAL_MS = 1_000L;

    private UserTrafficShaping() {
    }

    static void install(Channel inbound, AuthenticatedSession session) {
        if (inbound == null || session == null || !session.hasSpeedLimit()) {
            return;
        }
        ChannelPipeline pipeline = inbound.pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            return;
        }
        long writeLimit = session.speedLimitDownBps() < 0 ? 0L : session.speedLimitDownBps();
        long readLimit = session.speedLimitUpBps() < 0 ? 0L : session.speedLimitUpBps();
        if (writeLimit == 0L && readLimit == 0L) {
            return;
        }
        pipeline.addFirst(HANDLER_NAME,
                new ChannelTrafficShapingHandler(writeLimit, readLimit, CHECK_INTERVAL_MS));
    }
}
