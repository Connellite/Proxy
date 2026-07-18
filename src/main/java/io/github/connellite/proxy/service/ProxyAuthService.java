package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.AppSettings;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProxyAuthService {

    private final ProxyUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;
    private final ConcurrentHashMap<Long, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    public boolean isAuthRequired() {
        return settingsService.get().isAuthRequired();
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedSession> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return Optional.empty();
        }
        Optional<ProxyUser> found = userRepository.findByUsernameIgnoreCase(username.trim());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ProxyUser user = found.get();
        if (!user.isUsable()) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        if (user.getMaxConnections() > 0) {
            int current = activeConnections.getOrDefault(user.getId(), new AtomicInteger()).get();
            if (current >= user.getMaxConnections()) {
                return Optional.empty();
            }
        }
        return Optional.of(new AuthenticatedSession(user));
    }

    public boolean tryAcquireConnection(AuthenticatedSession session) {
        if (session == null) {
            return true;
        }
        ProxyUser user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || !user.isUsable()) {
            return false;
        }
        if (user.getMaxConnections() <= 0) {
            activeConnections.computeIfAbsent(session.getUserId(), id -> new AtomicInteger()).incrementAndGet();
            return true;
        }
        AtomicInteger counter = activeConnections.computeIfAbsent(session.getUserId(), id -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= user.getMaxConnections()) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void releaseConnection(AuthenticatedSession session) {
        if (session == null) {
            return;
        }
        AtomicInteger counter = activeConnections.get(session.getUserId());
        if (counter == null) {
            return;
        }
        int left = counter.decrementAndGet();
        if (left <= 0) {
            activeConnections.remove(session.getUserId(), counter);
        }
    }

    public int activeConnectionsFor(Long userId) {
        AtomicInteger counter = activeConnections.get(userId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    public int totalActiveConnections() {
        return activeConnections.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public void clearActiveConnections() {
        activeConnections.clear();
    }

    public AppSettings currentSettings() {
        return settingsService.get();
    }
}
