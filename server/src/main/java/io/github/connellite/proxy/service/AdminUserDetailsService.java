package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final ProxyUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ProxyUser user = userRepository.findByIdIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!user.isEnabled() || !user.hasAdminRole()) {
            throw new UsernameNotFoundException("User not found");
        }
        return user;
    }
}
