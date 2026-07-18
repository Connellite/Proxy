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
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class TrafficStatsService {

    private final ProxyUserRepository userRepository;
    private final AppSettingsRepository settingsRepository;
    private final SettingsService settingsService;

    private final ConcurrentHashMap<Long, TrafficDelta> pendingUsers = new ConcurrentHashMap<>();
    private final TrafficDelta pendingGlobal = new TrafficDelta();
    /** Bytes observed since this JVM process started (not reset on flush). */
    private final TrafficDelta sessionTotal = new TrafficDelta();

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
        }
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
}
