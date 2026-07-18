package io.github.connellite.proxy.service;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.AdminAccount;
import io.github.connellite.proxy.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataBootstrap implements ApplicationRunner {

    private final AdminAccountRepository adminRepository;
    private final SettingsService settingsService;
    private final ProxyProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        settingsService.ensureInitialized();
        if (adminRepository.count() == 0) {
            AdminAccount admin = new AdminAccount();
            admin.setUsername(properties.getBootstrap().getAdminUsername());
            admin.setPasswordHash(passwordEncoder.encode(properties.getBootstrap().getAdminPassword()));
            adminRepository.save(admin);
            log.warn("Created default admin account '{}' — change the password in the web UI", admin.getUsername());
        }
    }
}
