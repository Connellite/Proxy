package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxyAuthServiceTest {

    @Test
    void enforcesMaxConnectionsWithSemaphorePermits() {
        ProxyUser user = userWithMaxConnections(1L, 2);
        ProxyAuthService authService = authServiceFor(user);
        AuthenticatedSession session = new AuthenticatedSession(user);

        ProxyAuthService.ConnectionPermit first = authService.acquireConnection(session).orElseThrow();
        ProxyAuthService.ConnectionPermit second = authService.acquireConnection(session).orElseThrow();

        assertThat(authService.acquireConnection(session)).isEmpty();
        assertThat(authService.activeConnectionsFor(user.getId())).isEqualTo(2);

        authService.releaseConnection(first);

        ProxyAuthService.ConnectionPermit third = authService.acquireConnection(session).orElseThrow();

        authService.releaseConnection(second);
        authService.releaseConnection(third);
    }

    @Test
    void restoresFullLimitedCapacityAfterUnlimitedConnectionsDrain() {
        ProxyUser user = userWithMaxConnections(1L, 0);
        ProxyAuthService authService = authServiceFor(user);
        AuthenticatedSession session = new AuthenticatedSession(user);

        ProxyAuthService.ConnectionPermit unlimited = authService.acquireConnection(session).orElseThrow();

        user.setMaxConnections(2);
        ProxyAuthService.ConnectionPermit limited = authService.acquireConnection(session).orElseThrow();
        assertThat(authService.acquireConnection(session)).isEmpty();

        authService.releaseConnection(unlimited);
        authService.releaseConnection(limited);

        ProxyAuthService.ConnectionPermit first = authService.acquireConnection(session).orElseThrow();
        ProxyAuthService.ConnectionPermit second = authService.acquireConnection(session).orElseThrow();

        assertThat(authService.acquireConnection(session)).isEmpty();

        authService.releaseConnection(first);
        authService.releaseConnection(second);
    }

    private static ProxyAuthService authServiceFor(ProxyUser user) {
        ProxyUserRepository repository = mock(ProxyUserRepository.class);
        when(repository.findById(user.getId())).thenAnswer(invocation -> Optional.of(user));
        TrafficStatsService trafficStats = mock(TrafficStatsService.class);
        when(trafficStats.isOverTrafficLimit(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        return new ProxyAuthService(repository, null, null, trafficStats);
    }

    private static ProxyUser userWithMaxConnections(Long id, int maxConnections) {
        ProxyUser user = new ProxyUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEnabled(true);
        user.setMaxConnections(maxConnections);
        return user;
    }
}
