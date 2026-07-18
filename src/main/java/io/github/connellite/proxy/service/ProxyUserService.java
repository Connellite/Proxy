package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import io.github.connellite.proxy.dto.ProxyUserForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProxyUserService {

    private final ProxyUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ProxyAuthService authService;

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
        if (form.getPassword() == null || form.getPassword().isBlank()) {
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
        user.setRemark(blankToNull(form.getRemark()));
        user.setMaxConnections(Math.max(0, form.getMaxConnections()));
        user.setExpiresAt(parseExpireDate(form.getExpiresAt()));
        if (creating || (form.getPassword() != null && !form.getPassword().isBlank())) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
    }

    private static Instant parseExpireDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDate date = LocalDate.parse(value);
        return date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
