package io.github.connellite.proxy.service;

import io.github.connellite.proxy.repository.ProxyUserRepository;
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
    private final ConcurrentHashMap<Long, TrafficDelta> pending = new ConcurrentHashMap<>();

    public void record(Long userId, long bytesUp, long bytesDown) {
        if (userId == null || (bytesUp <= 0 && bytesDown <= 0)) {
            return;
        }
        pending.computeIfAbsent(userId, id -> new TrafficDelta()).add(bytesUp, bytesDown);
    }

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Map.Entry<Long, TrafficDelta> entry : pending.entrySet()) {
            TrafficDelta delta = pending.remove(entry.getKey());
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
