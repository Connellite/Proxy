package io.github.connellite.proxy.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
#if SPRING_BOOT_3
import org.springframework.security.config.Customizer;
#endif

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminUserDetailsService adminUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
#if SPRING_BOOT_3
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .userDetailsService(adminUserDetailsService)
                .csrf(Customizer.withDefaults());
#else
        http
                .authorizeRequests()
                .antMatchers("/css/**", "/login").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
                .and()
                .logout()
                .logoutSuccessUrl("/login?logout")
                .permitAll()
                .and()
                .userDetailsService(adminUserDetailsService);
#endif
        return http.build();
    }
}
