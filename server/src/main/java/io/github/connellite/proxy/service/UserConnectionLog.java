package io.github.connellite.proxy.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Last proxy connections (HTTP, SOCKS, SSH).
 */
@Component
public class UserConnectionLog {

    static final int LIMIT = 200;

    private final AtomicLong ids = new AtomicLong();
    private final Cache<Long, Entry> cache = Caffeine.newBuilder().maximumSize(LIMIT).build();

    public Tracker open(InetSocketAddress client, String userId, String protocol) {
        return new Tracker(client, userId == null ? "" : userId, protocol == null ? "" : protocol, System.nanoTime());
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

    private void record(Tracker tracker, String outcome) {
        long millis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tracker.startedNanos));
        cache.put(ids.incrementAndGet(), new Entry(Instant.now(), tracker.userId, tracker.protocol,
                client(tracker.client), tracker.up.get(), tracker.down.get(),
                outcome == null ? "" : outcome, millis));
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

    public record Entry(Instant at, String user, String protocol, String client, long bytesUp, long bytesDown,
                        String outcome, long millis) {
    }

    public final class Tracker {

        private final InetSocketAddress client;
        private final String userId;
        private final String protocol;
        private final long startedNanos;
        private final AtomicLong up = new AtomicLong();
        private final AtomicLong down = new AtomicLong();
        private final AtomicBoolean finished = new AtomicBoolean();

        private Tracker(InetSocketAddress client, String userId, String protocol, long startedNanos) {
            this.client = client;
            this.userId = userId;
            this.protocol = protocol;
            this.startedNanos = startedNanos;
        }

        public void addUp(long bytes) {
            if (bytes > 0) {
                up.addAndGet(bytes);
            }
        }

        public void addDown(long bytes) {
            if (bytes > 0) {
                down.addAndGet(bytes);
            }
        }

        public void finish(String outcome) {
            if (finished.compareAndSet(false, true)) {
                record(this, outcome);
            }
        }
    }
}
