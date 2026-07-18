package io.github.connellite.proxy.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
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

@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
