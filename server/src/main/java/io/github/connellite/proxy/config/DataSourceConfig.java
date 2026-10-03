package io.github.connellite.proxy.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.connellite.proxy.util.RuntimeEnvironment;
import io.github.connellite.proxy.util.SqliteDatabase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWarDeployment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWarDeployment;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Order: JNDI on a WAR, then {@code spring.datasource.url} as written (including {@code jdbc:sqlite:}),
 * then the SQLite file in the data directory. Native images skip JNDI and the URL and always open the file.
 * Hibernate resolves the dialect from JDBC metadata unless {@code spring.jpa.database-platform} is set.
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @ConditionalOnWarDeployment
    DataSourceSelection warDataSourceSelection(@Qualifier("dataDir") Path dataDir,
                                               DataSourceProperties dataSourceProperties,
                                               Environment environment) {
        String jndiName = StringUtils.trimToNull(dataSourceProperties.getJndiName());
        if (jndiName != null) {
            try {
                DataSource jndiDataSource = new JndiDataSourceLookup().getDataSource(jndiName);
                log.info("Using JNDI DataSource {}", jndiName);
                return new DataSourceSelection(jndiDataSource);
            } catch (DataSourceLookupFailureException ex) {
                log.warn("JNDI resource '{}' was not found. Falling back to JDBC settings.", jndiName);
            }
        }
        return jdbcOrSqlite(dataDir, dataSourceProperties, environment);
    }

    @Bean
    @ConditionalOnNotWarDeployment
    DataSourceSelection jarDataSourceSelection(@Qualifier("dataDir") Path dataDir,
                                               DataSourceProperties dataSourceProperties,
                                               Environment environment) {
        if (RuntimeEnvironment.isNativeImage()) {
            return sqliteSelection(dataDir, dataSourceProperties, environment);
        }
        return jdbcOrSqlite(dataDir, dataSourceProperties, environment);
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceSelection selection) {
        return selection.dataSource();
    }

    private DataSourceSelection jdbcOrSqlite(Path dataDir,
                                              DataSourceProperties dataSourceProperties,
                                              Environment environment) {
        String url = StringUtils.trimToNull(dataSourceProperties.getUrl());
        if (url != null) {
            log.info("Using JDBC DataSource {}", url);
            return new DataSourceSelection(jdbcDataSource(dataSourceProperties, environment));
        }
        return sqliteSelection(dataDir, dataSourceProperties, environment);
    }

    private DataSourceSelection sqliteSelection(Path dataDir,
                                                DataSourceProperties dataSourceProperties,
                                                Environment environment) {
        log.info("Using SQLite DataSource at {}", SqliteDatabase.databaseFile(dataDir));
        return new DataSourceSelection(sqliteDataSource(dataDir, dataSourceProperties, environment));
    }

    private static HikariDataSource sqliteDataSource(Path dataDir,
                                                     DataSourceProperties dataSourceProperties,
                                                     Environment environment) {
        Path dbFile = SqliteDatabase.databaseFile(dataDir);
        DataSourceProperties sqlite = new DataSourceProperties();
        sqlite.setUrl("jdbc:sqlite:" + dbFile);
        sqlite.setUsername(dataSourceProperties.getUsername());
        sqlite.setPassword(dataSourceProperties.getPassword());
        HikariDataSource dataSource = sqlite.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(dataSource));
        dataSource.setDataSourceProperties(SqliteDatabase.config().toProperties());
        return dataSource;
    }

    private static HikariDataSource jdbcDataSource(DataSourceProperties dataSourceProperties,
                                                   Environment environment) {
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(dataSource));
        if (DatabaseDriver.fromJdbcUrl(dataSourceProperties.getUrl()) == DatabaseDriver.SQLITE) {
            dataSource.setDataSourceProperties(SqliteDatabase.config().toProperties());
        }
        return dataSource;
    }

    public record DataSourceSelection(DataSource dataSource) {
    }
}
