package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.AppSettings;
import io.github.connellite.proxy.repository.AppSettingsRepository;
import io.github.connellite.proxy.repository.ProxyUserRepository;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class TrafficStatsService {

    private static final long RATE_IDLE_EVICT_MS = 30_000;

    private final ProxyUserRepository userRepository;
    private final AppSettingsRepository settingsRepository;
    private final SettingsService settingsService;

    private final ConcurrentHashMap<Long, TrafficDelta> pendingUsers = new ConcurrentHashMap<>();
    private final TrafficDelta pendingGlobal = new TrafficDelta();
    /** Bytes observed since this JVM process started (not reset on flush). */
    private final TrafficDelta sessionTotal = new TrafficDelta();
    /** Sliding 1s windows → B/s per user. */
    private final ConcurrentHashMap<Long, RateSample> rates = new ConcurrentHashMap<>();

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
            pendingUsers.computeIfAbsent(userId, id -> new TrafficDelta()).add(bytesUp, bytesDown);
            rates.computeIfAbsent(userId, id -> new RateSample()).add(bytesUp, bytesDown);
        }
    }

    public UserThroughput throughputFor(Long userId) {
        if (userId == null) {
            return UserThroughput.ZERO;
        }
        RateSample sample = rates.get(userId);
        return sample == null ? UserThroughput.ZERO : sample.snapshot();
    }

    public Map<Long, UserThroughput> throughputSnapshot() {
        Map<Long, UserThroughput> map = new ConcurrentHashMap<>();
        rates.forEach((id, sample) -> map.put(id, sample.snapshot()));
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
        return settingsRepository.findById(AppSettings.SINGLETON_ID)
                .map(AppSettings::getBytesUpTotal)
                .orElse(0L);
    }

    private long persistedBytesDown() {
        return settingsRepository.findById(AppSettings.SINGLETON_ID)
                .map(AppSettings::getBytesDownTotal)
                .orElse(0L);
    }

    /** Roll the 1-second throughput window. */
    @Scheduled(fixedRate = 1_000)
    public void tickRates() {
        long now = System.currentTimeMillis();
        rates.forEach((id, sample) -> {
            sample.tick(now);
            if (sample.isStale(now, RATE_IDLE_EVICT_MS)) {
                rates.remove(id, sample);
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
            settingsRepository.addTraffic(AppSettings.SINGLETON_ID, globalUp, globalDown);
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

    private static final class RateSample {
        private final AtomicLong windowUp = new AtomicLong();
        private final AtomicLong windowDown = new AtomicLong();
        private volatile long upBps;
        private volatile long downBps;
        private volatile long lastActivityMs = System.currentTimeMillis();
        private volatile long windowStartMs = System.currentTimeMillis();

        void add(long bytesUp, long bytesDown) {
            if (bytesUp > 0) {
                windowUp.addAndGet(bytesUp);
            }
            if (bytesDown > 0) {
                windowDown.addAndGet(bytesDown);
            }
            lastActivityMs = System.currentTimeMillis();
        }

        synchronized void tick(long nowMs) {
            long elapsedMs = Math.max(1L, nowMs - windowStartMs);
            long up = windowUp.getAndSet(0);
            long down = windowDown.getAndSet(0);
            upBps = up * 1000L / elapsedMs;
            downBps = down * 1000L / elapsedMs;
            windowStartMs = nowMs;
        }

        UserThroughput snapshot() {
            return new UserThroughput(upBps, downBps);
        }

        boolean isStale(long nowMs, long idleMs) {
            return upBps == 0 && downBps == 0 && windowUp.get() == 0 && windowDown.get() == 0
                    && (nowMs - lastActivityMs) > idleMs;
        }
    }
}
