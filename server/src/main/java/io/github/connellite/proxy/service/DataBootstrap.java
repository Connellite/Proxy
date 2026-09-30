package io.github.connellite.proxy.service;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.Role;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import io.github.connellite.proxy.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataBootstrap implements ApplicationRunner {

    private final ProxyUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SettingsService settingsService;
    private final ProxyProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        settingsService.ensureInitialized();
        Role adminRole = ensureRole(Role.ADMIN);
        ensureRole(Role.USER);

        String adminId = StringUtils.trimToEmpty(properties.getBootstrap().getAdminUsername());
        if (StringUtils.isBlank(adminId)) {
            throw new IllegalStateException("proxy.bootstrap.admin-username must not be blank");
        }

        ProxyUser admin = userRepository.findByIdIgnoreCase(adminId).orElse(null);
        if (admin == null) {
            admin = new ProxyUser();
            admin.setId(adminId);
            admin.setPasswordHash(passwordEncoder.encode(properties.getBootstrap().getAdminPassword()));
            admin.setEnabled(true);
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            admin.setRoles(roles);
            userRepository.save(admin);
            log.warn("Created default admin account '{}' — change the password in the web UI", adminId);
            return;
        }
        if (!admin.hasAdminRole()) {
            Set<Role> roles = new HashSet<>(admin.getRoles());
            roles.add(adminRole);
            admin.setRoles(roles);
            userRepository.save(admin);
            log.warn("Restored ROLE_ADMIN on bootstrap account '{}'", admin.getId());
        }
    }

    private Role ensureRole(String roleId) {
        return roleRepository.findById(roleId).orElseGet(() -> {
            Role role = new Role();
            role.setId(roleId);
            role.setActive(true);
            return roleRepository.save(role);
        });
    }
}
