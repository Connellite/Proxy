package io.github.connellite.proxy.dns.server;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Last DNS answers.
 */
@Component
public class QueryLog {

    static final int LIMIT = 200;
    private static final int MAX_ADDRESSES = 4;

    private final AtomicLong ids = new AtomicLong();
    private final Cache<Long, Entry> cache = Caffeine.newBuilder().maximumSize(LIMIT).build();

    public void record(InetSocketAddress client, String protocol, String name, String type, String outcome,
                       String answer, long startedNanos) {
        long millis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        cache.put(ids.incrementAndGet(), new Entry(Instant.now(), client(client),
                protocol == null ? "" : protocol,
                name == null ? "" : name, type == null ? "" : type, outcome, answer == null ? "" : answer, millis));
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

    /**
     * Address records from a forwarded answer, or the response code when there are none.
     */
    public static String answers(Message message) {
        List<String> addresses = new ArrayList<>();
        for (Record record : message.getSection(Section.ANSWER)) {
            if (record instanceof ARecord a) {
                addresses.add(a.getAddress().getHostAddress());
            } else if (record instanceof AAAARecord aaaa) {
                addresses.add(aaaa.getAddress().getHostAddress());
            }
            if (addresses.size() == MAX_ADDRESSES) {
                break;
            }
        }
        if (addresses.isEmpty()) {
            return Rcode.string(message.getRcode());
        }
        return String.join(", ", addresses);
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

    public record Entry(Instant at, String client, String protocol, String name, String type, String outcome,
                        String answer, long millis) {
    }
}
