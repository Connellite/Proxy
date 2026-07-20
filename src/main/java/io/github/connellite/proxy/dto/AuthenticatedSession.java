package io.github.connellite.proxy.dto;

import io.github.connellite.proxy.model.ProxyUser;

import java.util.Objects;

public record AuthenticatedSession(
        Long userId,
        String username,
        long trafficLimitBytes,
        long speedLimitUpBps,
        long speedLimitDownBps
) {

    public AuthenticatedSession(ProxyUser user) {
        this(
                Objects.requireNonNull(user.getId(), "userId"),
                user.getUsername(),
                user.getTrafficLimitBytes(),
                user.getSpeedLimitUpBps(),
                user.getSpeedLimitDownBps()
        );
    }

    public boolean hasSpeedLimit() {
        return speedLimitUpBps >= 0 || speedLimitDownBps >= 0;
    }
}
