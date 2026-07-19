package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import io.github.connellite.proxy.dto.ProxyUserForm;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProxyUserService {

    private final ProxyUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ProxyAuthService authService;
    private final ZoneId appZoneId;

    @Transactional(readOnly = true)
    public List<ProxyUser> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ProxyUser getRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public ProxyUser create(ProxyUserForm form) {
        String username = form.getUsername().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (StringUtils.isBlank(form.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
        ProxyUser user = new ProxyUser();
        applyForm(user, form, true);
        return repository.save(user);
    }

    @Transactional
    public ProxyUser update(Long id, ProxyUserForm form) {
        ProxyUser user = getRequired(id);
        String username = form.getUsername().trim();
        if (!user.getUsername().equalsIgnoreCase(username) && repository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        applyForm(user, form, false);
        return repository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        ProxyUser user = getRequired(id);
        user.setEnabled(enabled);
        repository.save(user);
    }

    @Transactional
    public void resetTraffic(Long id) {
        ProxyUser user = getRequired(id);
        user.setBytesUp(0);
        user.setBytesDown(0);
        repository.save(user);
    }

    public int activeConnections(Long userId) {
        return authService.activeConnectionsFor(userId);
    }

    private void applyForm(ProxyUser user, ProxyUserForm form, boolean creating) {
        user.setUsername(form.getUsername().trim());
        user.setEnabled(form.isEnabled());
        user.setMaxConnections(Math.max(0, form.getMaxConnections()));
        user.setExpiresAt(parseExpireDate(form.getExpiresAt()));
        if (creating || StringUtils.isNotBlank(form.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
    }

    private Instant parseExpireDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        // Calendar date from the date picker → end of that day in configured timezone.
        LocalDate date = LocalDate.parse(value);
        return date.atTime(LocalTime.of(23, 59, 59)).atZone(appZoneId).toInstant();
    }
}
