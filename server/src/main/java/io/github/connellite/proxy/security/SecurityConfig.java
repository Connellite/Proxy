package io.github.connellite.proxy.security;

import io.github.connellite.proxy.service.AdminUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
#if SPRING_BOOT_3
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
#else
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
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
                        .requestMatchers("/css/**", "/icons/**", "/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin.html", true)
                        .permitAll())
                .logout(logout -> logout
                        // GWT uses Window.Location.assign("/logout") → GET
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                // GWT RPC cannot follow a redirect to the login page; the client handles 401 itself
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/proxyAdmin/rpc/**")))
                .userDetailsService(adminUserDetailsService)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/proxyAdmin/rpc/**"));
#else
        http
                .authorizeRequests()
                .antMatchers("/css/**", "/icons/**", "/login").permitAll()
                .anyRequest().hasRole("ADMIN")
                .and()
                .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/admin.html", true)
                .permitAll()
                .and()
                .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
                .and()
                // GWT RPC cannot follow a redirect to the login page; the client handles 401 itself
                .exceptionHandling()
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/proxyAdmin/rpc/**"))
                .and()
                .csrf()
                .ignoringAntMatchers("/proxyAdmin/rpc/**")
                .and()
                .userDetailsService(adminUserDetailsService);
#endif
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
