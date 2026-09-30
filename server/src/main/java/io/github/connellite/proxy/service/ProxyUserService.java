package io.github.connellite.proxy.service;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import io.github.connellite.proxy.dto.ProxyUserForm;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.Role;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import io.github.connellite.proxy.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProxyUserService {

    private final ProxyUserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProxyAuthService authService;
    private final TrafficStatsService trafficStatsService;
    private final ProxyProperties properties;
    private final ZoneId appZoneId;

    @Transactional(readOnly = true)
    public List<ProxyUser> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ProxyUser getRequired(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public ProxyUser create(ProxyUserForm form) {
        String id = normalizeId(form.getId());
        if (repository.existsByIdIgnoreCase(id)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (StringUtils.isBlank(form.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
        ProxyUser user = new ProxyUser();
        user.setId(id);
        applyForm(user, form, true);
        applyRoles(user, form);
        return repository.save(user);
    }

    @Transactional
    public ProxyUser update(String id, ProxyUserForm form) {
        ProxyUser user = getRequired(id);
        applyForm(user, form, false);
        applyRoles(user, form);
        return repository.save(user);
    }

    @Transactional
    public void delete(String id) {
        ensureNotBootstrapAdmin(id);
        repository.deleteById(id);
        trafficStatsService.clearLiveTotal(id);
    }

    @Transactional
    public void setEnabled(String id, boolean enabled) {
        ProxyUser user = getRequired(id);
        if (!enabled && isBootstrapAdmin(user.getId())) {
            throw new IllegalArgumentException("Cannot disable bootstrap admin account");
        }
        user.setEnabled(enabled);
        repository.save(user);
    }

    @Transactional
    public void resetTraffic(String id) {
        ProxyUser user = getRequired(id);
        user.setBytesUp(0);
        user.setBytesDown(0);
        repository.save(user);
        trafficStatsService.clearLiveTotal(id);
    }

    public int activeConnections(String userId) {
        return authService.activeConnectionsFor(userId);
    }

    public boolean isBootstrapAdmin(String id) {
        return id != null && id.equalsIgnoreCase(bootstrapAdminId());
    }

    @Transactional
    public void changePassword(String id, PasswordChangeForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }
        ProxyUser user = repository.findByIdIgnoreCase(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        repository.save(user);
    }

    private void applyForm(ProxyUser user, ProxyUserForm form, boolean creating) {
        // Bootstrap admin must stay enabled so the UI remains reachable.
        user.setEnabled(isBootstrapAdmin(user.getId()) || form.isEnabled());
        user.setMaxConnections(Math.max(0, form.getMaxConnections()));
        user.setTrafficLimitBytes(normalizeLimit(form.getTrafficLimitBytes()));
        user.setSpeedLimitUpBps(normalizeLimit(form.getSpeedLimitUpBps()));
        user.setSpeedLimitDownBps(normalizeLimit(form.getSpeedLimitDownBps()));
        user.setExpiresAt(parseExpireDate(form.getExpiresAt()));
        if (creating || StringUtils.isNotBlank(form.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
    }

    private void applyRoles(ProxyUser user, ProxyUserForm form) {
        Set<Role> roles = new HashSet<>();
        boolean bootstrap = isBootstrapAdmin(user.getId());
        // Bootstrap admin always keeps ROLE_ADMIN; ROLE_USER is optional.
        if (bootstrap || form.isRoleAdmin()) {
            roles.add(requiredRole(Role.ADMIN));
        }
        if (form.isRoleUser()) {
            roles.add(requiredRole(Role.USER));
        }
        user.setRoles(roles);
    }

    private Role requiredRole(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + roleId));
    }

    private void ensureNotBootstrapAdmin(String id) {
        if (isBootstrapAdmin(id)) {
            throw new IllegalArgumentException("Cannot delete bootstrap admin account");
        }
    }

    private String bootstrapAdminId() {
        return StringUtils.trimToEmpty(properties.getBootstrap().getAdminUsername());
    }

    private static String normalizeId(String id) {
        String trimmed = StringUtils.trimToNull(id);
        if (trimmed == null) {
            throw new IllegalArgumentException("Username is required");
        }
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("Username must be at most 64 characters");
        }
        return trimmed;
    }

    /** Collapse any negative to -1 (unlimited). */
    private static long normalizeLimit(long value) {
        return value < 0 ? -1L : value;
    }

    private Instant parseExpireDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        LocalDate date = LocalDate.parse(value);
        return date.atTime(LocalTime.of(23, 59, 59)).atZone(appZoneId).toInstant();
    }
}
