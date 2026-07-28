package io.github.connellite.proxy.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin.html", true)
                        .permitAll())
                .logout(logout -> logout
                        // GWT uses Window.Location.assign("/logout") → GET
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .userDetailsService(adminUserDetailsService)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/proxyAdmin/rpc/**"));
#else
        http
                .authorizeRequests()
                .antMatchers("/css/**", "/icons/**", "/login").permitAll()
                .anyRequest().authenticated()
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
                .csrf()
                .ignoringAntMatchers("/proxyAdmin/rpc/**")
                .and()
                .userDetailsService(adminUserDetailsService);
#endif
        return http.build();
    }
}
