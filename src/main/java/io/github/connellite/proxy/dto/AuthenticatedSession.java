package io.github.connellite.proxy.dto;

import io.github.connellite.proxy.model.ProxyUser;

import java.util.Objects;

public record AuthenticatedSession(Long userId, String username) {

    public AuthenticatedSession(ProxyUser user) {
        this(Objects.requireNonNull(user.getId(), "userId"), user.getUsername());
    }

}
