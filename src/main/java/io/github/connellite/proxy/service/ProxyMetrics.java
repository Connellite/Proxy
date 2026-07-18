package io.github.connellite.proxy.service;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Live proxy metrics (works with or without authenticated users).
 */
@Service
@RequiredArgsConstructor
public class ProxyMetrics {

    private static final AttributeKey<Boolean> TRACKED = AttributeKey.valueOf("proxyMetricsTracked");

    private final ProxyAuthService authService;
    private final TrafficStatsService trafficStatsService;

    private final AtomicInteger activeConnections = new AtomicInteger();
    @Getter
    private final LongAdder bytesUp = new LongAdder();
    @Getter
    private final LongAdder bytesDown = new LongAdder();

    /**
     * Registers an active client connection once per channel. Releases on channel close.
     *
     * @return false if a named user hit their connection limit
     */
    public boolean track(Channel channel, AuthenticatedSession session) {
        if (Boolean.TRUE.equals(channel.attr(TRACKED).get())) {
            return true;
        }
        if (session != null && !authService.tryAcquireConnection(session)) {
            return false;
        }
        channel.attr(TRACKED).set(true);
        activeConnections.incrementAndGet();
        channel.closeFuture().addListener(future -> {
            if (Boolean.TRUE.equals(channel.attr(TRACKED).getAndSet(false))) {
                activeConnections.decrementAndGet();
                authService.releaseConnection(session);
            }
        });
        return true;
    }

    public int getActiveConnections() {
        return Math.max(0, activeConnections.get());
    }

    public long getBytesUpTotal() {
        return bytesUp.sum();
    }

    public long getBytesDownTotal() {
        return bytesDown.sum();
    }

    public void recordTraffic(Long userId, long up, long down) {
        if (up > 0) {
            bytesUp.add(up);
        }
        if (down > 0) {
            bytesDown.add(down);
        }
        if (userId != null) {
            trafficStatsService.record(userId, up, down);
        }
    }
}
