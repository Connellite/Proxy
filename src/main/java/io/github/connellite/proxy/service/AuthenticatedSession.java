package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import lombok.Getter;

import java.util.Objects;

@Getter
public final class AuthenticatedSession {

    private final Long userId;
    private final String username;

    public AuthenticatedSession(ProxyUser user) {
        this(Objects.requireNonNull(user.getId(), "userId"), user.getUsername());
    }

    public AuthenticatedSession(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }
}
