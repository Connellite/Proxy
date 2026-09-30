package io.github.connellite.proxy.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class AppConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public ZoneId appZoneId(ProxyProperties proxyProperties) {
        String id = StringUtils.trimToNull(proxyProperties.getTimezone());
        if (id == null) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(id);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("Invalid proxy.timezone: " + id, ex);
        }
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(@Qualifier("dataDir") Path dataDir, DataSourceProperties dataSourceProperties) {
        Path dbFile = dataDir.resolve("proxy.db");
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(dataSourceProperties.determineDriverClassName())
                .url("jdbc:sqlite:" + dbFile.toAbsolutePath())
                .username(dataSourceProperties.determineUsername())
                .password(dataSourceProperties.determinePassword())
                .build();
        dataSource.setDataSourceProperties(sqliteConfig().toProperties());
        return dataSource;
    }

    private static SQLiteConfig sqliteConfig() {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(10_000);
        // BEGIN IMMEDIATE takes the write lock up front, so pooled writers wait on busy_timeout
        // instead of failing with SQLITE_BUSY_SNAPSHOT when upgrading a read snapshot.
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return config;
    }
}
