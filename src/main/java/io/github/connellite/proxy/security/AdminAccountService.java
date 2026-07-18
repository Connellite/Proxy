package io.github.connellite.proxy.security;

import io.github.connellite.proxy.model.AdminAccount;
import io.github.connellite.proxy.repository.AdminAccountRepository;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(String username, PasswordChangeForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }
        AdminAccount admin = repository.findById(username).orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (!passwordEncoder.matches(form.getCurrentPassword(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        admin.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        repository.save(admin);
    }
}
