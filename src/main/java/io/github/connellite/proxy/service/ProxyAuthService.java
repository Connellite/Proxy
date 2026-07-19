package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProxyAuthService {

    private final ProxyUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;
    private final ConcurrentHashMap<Long, UserConnectionState> connectionStates = new ConcurrentHashMap<>();

    public boolean isHttpAuthRequired() {
        return settingsService.get().isHttpAuthRequired();
    }

    public boolean isSocksAuthRequired() {
        return settingsService.get().isSocksAuthRequired();
    }

    public boolean isSocksUdpEnabled() {
        return settingsService.get().isSocksUdpEnabled();
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedSession> authenticate(String username, String password) {
        if (StringUtils.isBlank(username) || password == null) {
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
        return Optional.of(new AuthenticatedSession(user));
    }

    public Optional<ConnectionPermit> acquireConnection(AuthenticatedSession session) {
        if (session == null) {
            return Optional.of(ConnectionPermit.NOOP);
        }
        ProxyUser user = userRepository.findById(session.userId()).orElse(null);
        if (user == null || !user.isUsable()) {
            return Optional.empty();
        }
        UserConnectionState state = connectionStates.computeIfAbsent(session.userId(), id -> new UserConnectionState());
        return state.tryAcquire(user.getMaxConnections());
    }

    public void releaseConnection(ConnectionPermit permit) {
        if (permit != null) {
            permit.release();
        }
    }

    public int activeConnectionsFor(Long userId) {
        UserConnectionState state = connectionStates.get(userId);
        return state == null ? 0 : state.activeConnections();
    }

    public int totalActiveConnections() {
        return connectionStates.values().stream().mapToInt(UserConnectionState::activeConnections).sum();
    }

    public void clearActiveConnections() {
        connectionStates.clear();
    }

    public AppSettings currentSettings() {
        return settingsService.get();
    }

    public static final class ConnectionPermit {

        private static final ConnectionPermit NOOP = new ConnectionPermit(null, false);

        private final UserConnectionState state;
        private final boolean limited;
        private final AtomicBoolean released = new AtomicBoolean();

        private ConnectionPermit(UserConnectionState state, boolean limited) {
            this.state = state;
            this.limited = limited;
        }

        private void release() {
            if (state != null && released.compareAndSet(false, true)) {
                state.release(limited);
            }
        }
    }

    private static final class UserConnectionState {

        private final AtomicInteger activeConnections = new AtomicInteger();
        private Semaphore permits;
        private int maxConnections;

        synchronized Optional<ConnectionPermit> tryAcquire(int latestMaxConnections) {
            if (latestMaxConnections <= 0) {
                activeConnections.incrementAndGet();
                return Optional.of(new ConnectionPermit(this, false));
            }

            refreshPermits(latestMaxConnections);
            if (!permits.tryAcquire()) {
                return Optional.empty();
            }
            activeConnections.incrementAndGet();
            return Optional.of(new ConnectionPermit(this, true));
        }

        private void refreshPermits(int latestMaxConnections) {
            int active = activeConnections();
            if (active == 0 && (permits == null
                    || maxConnections != latestMaxConnections
                    || permits.availablePermits() != latestMaxConnections)) {
                permits = new Semaphore(latestMaxConnections);
                maxConnections = latestMaxConnections;
            } else if (permits == null) {
                permits = new Semaphore(Math.max(0, latestMaxConnections - active));
                maxConnections = latestMaxConnections;
            }
        }

        void release(boolean limited) {
            activeConnections.updateAndGet(value -> Math.max(0, value - 1));
            if (limited) {
                synchronized (this) {
                    if (permits != null) {
                        permits.release();
                    }
                }
            }
        }

        int activeConnections() {
            return Math.max(0, activeConnections.get());
        }
    }
}
