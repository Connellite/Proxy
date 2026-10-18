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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

@Service
@RequiredArgsConstructor
public class TrafficStatsService {

    private static final long RATE_IDLE_EVICT_MS = 30_000;

    private final ProxyUserRepository userRepository;
    private final CacheService cacheService;
    private final ConfigRepository configRepository;
    private final SettingsService settingsService;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<TrafficStatsService> self;

    /** Serializes flush and admin reset so a reset cannot commit under an in-flight add. */
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final ConcurrentHashMap<String, UserSlot> slots = new ConcurrentHashMap<>();
    private final TrafficDelta pendingGlobal = new TrafficDelta();
    /** Bytes observed since this JVM process started (not reset on flush). */
    private final TrafficDelta sessionTotal = new TrafficDelta();
    /** Micrometer-backed live throughput meters per user. */
    private final ConcurrentHashMap<String, ThroughputMeters> throughputMeters = new ConcurrentHashMap<>();

    /**
     * Queue traffic for durable flush. {@code userId} may be null (anonymous / auth off).
     */
    public void record(String userId, long bytesUp, long bytesDown) {
        if (bytesUp <= 0 && bytesDown <= 0) {
            return;
        }
        sessionTotal.add(bytesUp, bytesDown);
        synchronized (pendingGlobal) {
            pendingGlobal.add(bytesUp, bytesDown);
        }
        if (userId != null) {
            slot(userId).add(bytesUp, bytesDown);
            throughputMeters.computeIfAbsent(userId, id -> new ThroughputMeters(meterRegistry, id)).record(bytesUp, bytesDown);
        }
    }

    /**
     * Refresh the live counter from the database, not from {@code user}.
     * The passed row may have been loaded before {@link #resetUserTraffic(String)}.
     */
    public void warmLiveTotal(ProxyUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        mutationLock.lock();
        try {
            UserSlot slot = slot(user.getId());
            int epoch = slot.epoch();
            long base = userRepository.findById(user.getId())
                    .map(loaded -> loaded.getBytesUp() + loaded.getBytesDown())
                    .orElse(0L);
            slot.raiseLiveTo(base, epoch);
        } finally {
            mutationLock.unlock();
        }
    }

    public boolean isOverTrafficLimit(String userId, long trafficLimitBytes) {
        if (userId == null || trafficLimitBytes < 0) {
            return false;
        }
        return slot(userId).live() >= trafficLimitBytes;
    }

    public long liveTotalBytes(String userId) {
        if (userId == null) {
            return 0L;
        }
        UserSlot slot = slots.get(userId);
        return slot == null ? 0L : Math.max(0L, slot.live());
    }

    public void clearLiveTotal(String userId) {
        if (userId == null) {
            return;
        }
        mutationLock.lock();
        try {
            slots.remove(userId);
        } finally {
            mutationLock.unlock();
        }
    }

    /**
     * Drop not-yet-flushed bytes for this user and zero the persisted counters.
     * Holds the same lock as {@link #flush()} until commit, so a flush cannot add the old pending total back.
     */
    @Transactional
    public void resetUserTraffic(String id) {
        requireActiveTransaction();
        mutationLock.lock();
        boolean unlockNow = true;
        try {
            UserSlot slot = slot(id);
            long[] discarded = new long[3];
            AtomicBoolean changed = new AtomicBoolean();
            unlockNow = !afterCompletion(status -> {
                if (status != TransactionSynchronization.STATUS_COMMITTED && changed.get()) {
                    slot.restoreDiscard(discarded);
                }
            });
            if (unlockNow) {
                throw new IllegalStateException("resetUserTraffic requires an active transaction");
            }
            System.arraycopy(slot.discardAndZero(), 0, discarded, 0, 3);
            changed.set(true);
            ProxyUser user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
            user.setBytesUp(0);
            user.setBytesDown(0);
            userRepository.save(user);
        } finally {
            if (unlockNow) {
                mutationLock.unlock();
            }
        }
    }

    public UserThroughput throughputFor(String userId) {
        if (userId == null) {
            return UserThroughput.ZERO;
        }
        ThroughputMeters meters = throughputMeters.get(userId);
        return meters == null ? UserThroughput.ZERO : meters.snapshot();
    }

    public Map<String, UserThroughput> throughputSnapshot() {
        Map<String, UserThroughput> map = new ConcurrentHashMap<>();
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
        requireActiveTransaction();
        mutationLock.lock();
        boolean unlockNow = true;
        try {
            FlushBatch batch = new FlushBatch();
            AtomicBoolean drained = new AtomicBoolean();
            unlockNow = !afterCompletion(status -> {
                if (status != TransactionSynchronization.STATUS_COMMITTED && drained.get()) {
                    batch.restore();
                }
            });
            if (unlockNow) {
                throw new IllegalStateException("flush requires an active transaction");
            }
            drained.set(true);
            drainInto(batch);
            apply(batch);
        } finally {
            if (unlockNow) {
                mutationLock.unlock();
            }
        }
    }

    /**
     * Spring calls {@code @PreDestroy} on the target, not the transactional proxy.
     * Go through the proxy so {@link #flush()} actually commits.
     */
    @PreDestroy
    public void flushOnShutdown() {
        self.getObject().flush();
    }

    private void drainInto(FlushBatch batch) {
        synchronized (pendingGlobal) {
            batch.globalUp = pendingGlobal.up.sumThenReset();
            batch.globalDown = pendingGlobal.down.sumThenReset();
        }
        for (Map.Entry<String, UserSlot> entry : slots.entrySet()) {
            long[] drained = entry.getValue().drain();
            if (drained[0] > 0 || drained[1] > 0) {
                batch.users.add(new UserDrain(entry.getKey(), entry.getValue(), drained[0], drained[1]));
            }
        }
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Active transaction is required");
        }
    }

    private void apply(FlushBatch batch) {
        settingsService.ensureInitialized();
        if (batch.globalUp > 0) {
            settingsService.addToLong(ConfigEntry.BYTES_UP_TOTAL, batch.globalUp);
        }
        if (batch.globalDown > 0) {
            settingsService.addToLong(ConfigEntry.BYTES_DOWN_TOTAL, batch.globalDown);
        }
        if (batch.users.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<String> flushedUserIds = new ArrayList<>();
        for (UserDrain drain : batch.users) {
            userRepository.addTraffic(drain.userId, drain.up, drain.down, now);
            flushedUserIds.add(drain.userId);
        }
        cacheService.evictUsersAfterCommit(flushedUserIds);
    }

    /**
     * @return true when the lock will be released from the transaction callback
     */
    private boolean afterCompletion(IntConsumer onComplete) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    onComplete.accept(status);
                } finally {
                    mutationLock.unlock();
                }
            }
        });
        return true;
    }

    private UserSlot slot(String userId) {
        UserSlot existing = slots.get(userId);
        if (existing != null) {
            return existing;
        }
        long base = userRepository.findById(userId)
                .map(user -> user.getBytesUp() + user.getBytesDown())
                .orElse(0L);
        UserSlot created = new UserSlot(base);
        UserSlot prev = slots.putIfAbsent(userId, created);
        return prev != null ? prev : created;
    }

    private final class FlushBatch {
        private long globalUp;
        private long globalDown;
        private final List<UserDrain> users = new ArrayList<>();

        private void restore() {
            synchronized (pendingGlobal) {
                pendingGlobal.add(globalUp, globalDown);
            }
            for (UserDrain drain : users) {
                drain.slot.restore(drain.up, drain.down);
            }
        }
    }

    private record UserDrain(String userId, UserSlot slot, long up, long down) {
    }

    private static final class UserSlot {
        private final TrafficDelta pending = new TrafficDelta();
        private final AtomicLong live;
        private int epoch;

        private UserSlot(long base) {
            this.live = new AtomicLong(base);
        }

        private synchronized void add(long bytesUp, long bytesDown) {
            pending.add(bytesUp, bytesDown);
            live.addAndGet(Math.max(0L, bytesUp) + Math.max(0L, bytesDown));
        }

        private synchronized long[] drain() {
            return new long[] {pending.up.sumThenReset(), pending.down.sumThenReset()};
        }

        private synchronized void restore(long bytesUp, long bytesDown) {
            pending.add(bytesUp, bytesDown);
        }

        /** @return discarded up, discarded down, live value before the reset */
        private synchronized long[] discardAndZero() {
            long up = pending.up.sumThenReset();
            long down = pending.down.sumThenReset();
            long previousLive = live.getAndSet(0L);
            epoch++;
            return new long[] {up, down, previousLive};
        }

        private synchronized void restoreDiscard(long[] discarded) {
            pending.add(discarded[0], discarded[1]);
            live.addAndGet(discarded[2]);
            epoch--;
        }

        private synchronized int epoch() {
            return epoch;
        }

        private synchronized long live() {
            return live.get();
        }

        private synchronized void raiseLiveTo(long base, int seenEpoch) {
            if (epoch != seenEpoch) {
                return;
            }
            live.accumulateAndGet(base, Math::max);
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

        ThroughputMeters(MeterRegistry meterRegistry, String userId) {
            String userTag = userId;
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
