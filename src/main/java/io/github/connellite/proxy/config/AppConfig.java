package io.github.connellite.proxy.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public ZoneId appZoneId(ProxyProperties proxyProperties) {
        String id = proxyProperties.getTimezone();
        if (id == null || id.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(id.trim());
        } catch (DateTimeException ex) {
            throw new IllegalStateException("Invalid proxy.timezone: " + id, ex);
        }
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(ProxyProperties proxyProperties, DataSourceProperties dataSourceProperties) throws Exception {
        Path dir = Path.of(proxyProperties.getDataDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path dbFile = dir.resolve("proxy.db");
        return DataSourceBuilder.create()
                .type(dataSourceProperties.getType())
                .driverClassName(dataSourceProperties.determineDriverClassName())
                .url("jdbc:sqlite:" + dbFile.toAbsolutePath())
                .username(dataSourceProperties.determineUsername())
                .password(dataSourceProperties.determinePassword())
                .build();
    }
}
