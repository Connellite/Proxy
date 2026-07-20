package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.UserThroughput;
import io.github.connellite.proxy.model.ConfigEntry;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ConfigRepository;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import com.google.common.primitives.Longs;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
#if SPRING_BOOT_3
import jakarta.annotation.PreDestroy;
#else
import javax.annotation.PreDestroy;
#endif
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class TrafficStatsService {

    private static final long RATE_IDLE_EVICT_MS = 30_000;

    private final ProxyUserRepository userRepository;
    private final ConfigRepository configRepository;
    private final SettingsService settingsService;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<Long, TrafficDelta> pendingUsers = new ConcurrentHashMap<>();
    private final TrafficDelta pendingGlobal = new TrafficDelta();
    /** Bytes observed since this JVM process started (not reset on flush). */
    private final TrafficDelta sessionTotal = new TrafficDelta();
    /** Live total bytes (DB baseline + recorded) for quota checks without waiting for flush. */
    private final ConcurrentHashMap<Long, AtomicLong> liveTotals = new ConcurrentHashMap<>();
    /** Micrometer-backed live throughput meters per user. */
    private final ConcurrentHashMap<Long, ThroughputMeters> throughputMeters = new ConcurrentHashMap<>();

    /**
     * Queue traffic for durable flush. {@code userId} may be null (anonymous / auth off).
     */
    public void record(Long userId, long bytesUp, long bytesDown) {
        if (bytesUp <= 0 && bytesDown <= 0) {
            return;
        }
        sessionTotal.add(bytesUp, bytesDown);
        pendingGlobal.add(bytesUp, bytesDown);
        if (userId != null) {
            long delta = Math.max(0L, bytesUp) + Math.max(0L, bytesDown);
            ensureLiveTotal(userId).addAndGet(delta);
            pendingUsers.computeIfAbsent(userId, id -> new TrafficDelta()).add(bytesUp, bytesDown);
            throughputMeters.computeIfAbsent(userId, id -> new ThroughputMeters(meterRegistry, id)).record(bytesUp, bytesDown);
        }
    }

    /** Seed / refresh the live counter from the persisted user row. */
    public void warmLiveTotal(ProxyUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        long base = user.getBytesUp() + user.getBytesDown();
        liveTotals.compute(user.getId(), (id, existing) -> {
            if (existing == null) {
                return new AtomicLong(base);
            }
            // Never move the live counter backwards relative to DB while traffic is pending.
            existing.accumulateAndGet(base, Math::max);
            return existing;
        });
    }

    public boolean isOverTrafficLimit(Long userId, long trafficLimitBytes) {
        if (userId == null || trafficLimitBytes < 0) {
            return false;
        }
        return ensureLiveTotal(userId).get() >= trafficLimitBytes;
    }

    public long liveTotalBytes(Long userId) {
        if (userId == null) {
            return 0L;
        }
        AtomicLong live = liveTotals.get(userId);
        return live == null ? 0L : Math.max(0L, live.get());
    }

    public void clearLiveTotal(Long userId) {
        if (userId != null) {
            liveTotals.remove(userId);
        }
    }

    private AtomicLong ensureLiveTotal(Long userId) {
        return liveTotals.computeIfAbsent(userId, id -> {
            long base = userRepository.findById(id)
                    .map(user -> user.getBytesUp() + user.getBytesDown())
                    .orElse(0L);
            return new AtomicLong(base);
        });
    }

    public UserThroughput throughputFor(Long userId) {
        if (userId == null) {
            return UserThroughput.ZERO;
        }
        ThroughputMeters meters = throughputMeters.get(userId);
        return meters == null ? UserThroughput.ZERO : meters.snapshot();
    }

    public Map<Long, UserThroughput> throughputSnapshot() {
        Map<Long, UserThroughput> map = new ConcurrentHashMap<>();
        throughputMeters.forEach((id, meters) -> map.put(id, meters.snapshot()));
        return map;
    }

    /** Since process start. */
    public long sessionBytesUp() {
        return sessionTotal.up.sum();
    }

    public long sessionBytesDown() {
        return sessionTotal.down.sum();
    }

    /** Lifetime total = DB + not-yet-flushed pending. */
    @Transactional(readOnly = true)
    public long lifetimeBytesUp() {
        return persistedBytesUp() + pendingGlobal.up.sum();
    }

    @Transactional(readOnly = true)
    public long lifetimeBytesDown() {
        return persistedBytesDown() + pendingGlobal.down.sum();
    }

    private long persistedBytesUp() {
        return configRepository.findById(ConfigEntry.BYTES_UP_TOTAL)
                .map(ConfigEntry::getValue)
                .map(Longs::tryParse)
                .orElse(0L);
    }

    private long persistedBytesDown() {
        return configRepository.findById(ConfigEntry.BYTES_DOWN_TOTAL)
                .map(ConfigEntry::getValue)
                .map(Longs::tryParse)
                .orElse(0L);
    }

    /** Roll the 1-second throughput window. */
    @Scheduled(fixedRate = 1_000)
    public void tickRates() {
        long now = System.currentTimeMillis();
        throughputMeters.forEach((id, meters) -> {
            meters.tick(now);
            if (meters.isStale(now, RATE_IDLE_EVICT_MS) && throughputMeters.remove(id, meters)) {
                meters.removeFrom(meterRegistry);
            }
        });
    }

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void flush() {
        flushInternal();
    }

    @PreDestroy
    @Transactional
    public void flushOnShutdown() {
        flushInternal();
    }

    private void flushInternal() {
        settingsService.ensureInitialized();
        long globalUp = pendingGlobal.up.sumThenReset();
        long globalDown = pendingGlobal.down.sumThenReset();
        if (globalUp > 0 || globalDown > 0) {
            if (globalUp > 0) {
                configRepository.addToLong(ConfigEntry.BYTES_UP_TOTAL, globalUp);
            }
            if (globalDown > 0) {
                configRepository.addToLong(ConfigEntry.BYTES_DOWN_TOTAL, globalDown);
            }
        }

        if (pendingUsers.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Map.Entry<Long, TrafficDelta> entry : pendingUsers.entrySet()) {
            TrafficDelta delta = pendingUsers.remove(entry.getKey());
            if (delta == null) {
                continue;
            }
            long up = delta.up.sumThenReset();
            long down = delta.down.sumThenReset();
            if (up > 0 || down > 0) {
                userRepository.addTraffic(entry.getKey(), up, down, now);
            }
        }
    }

    private static final class TrafficDelta {
        private final LongAdder up = new LongAdder();
        private final LongAdder down = new LongAdder();

        void add(long bytesUp, long bytesDown) {
            if (bytesUp > 0) {
                up.add(bytesUp);
            }
            if (bytesDown > 0) {
                down.add(bytesDown);
            }
        }
    }

    private static final class ThroughputMeters {
        private final LongAdder windowUp = new LongAdder();
        private final LongAdder windowDown = new LongAdder();
        private final AtomicLong upBps = new AtomicLong();
        private final AtomicLong downBps = new AtomicLong();
        private final AtomicLong lastActivityMs = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());
        private final List<Meter> meters;

        ThroughputMeters(MeterRegistry meterRegistry, Long userId) {
            String userTag = String.valueOf(userId);
            Counter upCounter = Counter.builder("proxy.user.traffic.bytes")
                    .description("Total upstream proxy bytes observed for a user in this process")
                    .baseUnit("bytes")
                    .tag("direction", "up")
                    .tag("user.id", userTag)
                    .register(meterRegistry);
            Counter downCounter = Counter.builder("proxy.user.traffic.bytes")
                    .description("Total downstream proxy bytes observed for a user in this process")
                    .baseUnit("bytes")
                    .tag("direction", "down")
                    .tag("user.id", userTag)
                    .register(meterRegistry);
            Gauge upGauge = Gauge.builder("proxy.user.throughput.bytes_per_second", upBps, AtomicLong::get)
                    .description("Current upstream proxy throughput for a user")
                    .baseUnit("bytes/second")
                    .tag("direction", "up")
                    .tag("user.id", userTag)
                    .register(meterRegistry);
            Gauge downGauge = Gauge.builder("proxy.user.throughput.bytes_per_second", downBps, AtomicLong::get)
                    .description("Current downstream proxy throughput for a user")
                    .baseUnit("bytes/second")
                    .tag("direction", "down")
                    .tag("user.id", userTag)
                    .register(meterRegistry);
            meters = List.of(upCounter, downCounter, upGauge, downGauge);
        }

        void record(long bytesUp, long bytesDown) {
            if (bytesUp > 0) {
                windowUp.add(bytesUp);
                ((Counter) meters.get(0)).increment(bytesUp);
            }
            if (bytesDown > 0) {
                windowDown.add(bytesDown);
                ((Counter) meters.get(1)).increment(bytesDown);
            }
            lastActivityMs.set(System.currentTimeMillis());
        }

        synchronized void tick(long nowMs) {
            long previousWindowStart = windowStartMs.getAndSet(nowMs);
            long elapsedMs = Math.max(1L, nowMs - previousWindowStart);
            long up = windowUp.sumThenReset();
            long down = windowDown.sumThenReset();
            upBps.set(up * 1000L / elapsedMs);
            downBps.set(down * 1000L / elapsedMs);
        }

        UserThroughput snapshot() {
            return new UserThroughput(upBps.get(), downBps.get());
        }

        boolean isStale(long nowMs, long idleMs) {
            return upBps.get() == 0 && downBps.get() == 0 && windowUp.sum() == 0 && windowDown.sum() == 0
                    && (nowMs - lastActivityMs.get()) > idleMs;
        }

        void removeFrom(MeterRegistry meterRegistry) {
            meters.forEach(meterRegistry::remove);
        }
    }
}
