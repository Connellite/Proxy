package io.github.connellite.proxy.dns.tcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Last TCP relay sessions (SNI).
 */
@Component
public class RelayTcpSessionLog {

    static final int LIMIT = 200;

    private final AtomicLong ids = new AtomicLong();
    private final Cache<Long, Entry> cache = Caffeine.newBuilder().maximumSize(LIMIT).build();

    public void record(InetSocketAddress client, String hostname, String outcome, long startedNanos) {
        long millis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        cache.put(ids.incrementAndGet(), new Entry(Instant.now(), client(client),
                hostname == null ? "" : hostname, outcome == null ? "" : outcome, millis));
    }

    /**
     * Newest first. Walking the map does not count as a cache read.
     */
    public List<Entry> recent() {
        cache.cleanUp();
        return cache.asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static String client(InetSocketAddress address) {
        if (address == null) {
            return "";
        }
        String host = address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
        if (host.indexOf(':') >= 0) {
            return "[" + host + "]:" + address.getPort();
        }
        return host + ":" + address.getPort();
    }

    public record Entry(Instant at, String client, String hostname, String outcome, long millis) {
    }
}
