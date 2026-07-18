package io.github.connellite.proxy.security;

import io.github.connellite.proxy.domain.AdminAccount;
import io.github.connellite.proxy.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccount admin = adminAccountRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found"));
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .roles("ADMIN")
                .build();
    }
}
