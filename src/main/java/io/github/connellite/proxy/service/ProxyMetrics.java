package io.github.connellite.proxy.service;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live proxy metrics (active connections) + traffic accounting via {@link TrafficStatsService}.
 */
@Service
@RequiredArgsConstructor
public class ProxyMetrics {

    private static final AttributeKey<AtomicBoolean> TRACKED = AttributeKey.valueOf("proxyMetricsTracked");

    private final ProxyAuthService authService;
    private final TrafficStatsService trafficStatsService;

    private final AtomicInteger activeConnections = new AtomicInteger();

    public boolean track(Channel channel, AuthenticatedSession session) {
        AtomicBoolean flag = channel.attr(TRACKED).get();
        if (flag != null) {
            return true;
        }
        if (session != null && !authService.tryAcquireConnection(session)) {
            return false;
        }
        AtomicBoolean tracked = new AtomicBoolean(true);
        channel.attr(TRACKED).set(tracked);
        activeConnections.incrementAndGet();
        channel.closeFuture().addListener(future -> {
            if (tracked.compareAndSet(true, false)) {
                activeConnections.updateAndGet(v -> Math.max(0, v - 1));
                authService.releaseConnection(session);
            }
        });
        return true;
    }

    public void resetActiveConnections() {
        activeConnections.set(0);
        authService.clearActiveConnections();
    }

    public int getActiveConnections() {
        return Math.max(0, activeConnections.get());
    }

    public long getBytesUpTotal() {
        return trafficStatsService.lifetimeBytesUp();
    }

    public long getBytesDownTotal() {
        return trafficStatsService.lifetimeBytesDown();
    }

    public long getBytesUpSession() {
        return trafficStatsService.sessionBytesUp();
    }

    public long getBytesDownSession() {
        return trafficStatsService.sessionBytesDown();
    }

    public void recordTraffic(Long userId, long up, long down) {
        trafficStatsService.record(userId, up, down);
    }
}
