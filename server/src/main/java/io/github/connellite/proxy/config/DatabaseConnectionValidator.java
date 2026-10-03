package io.github.connellite.proxy.config;

#if SPRING_BOOT_3
import jakarta.annotation.PostConstruct;
#else
import javax.annotation.PostConstruct;
#endif
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Fails startup when the chosen {@link DataSource} cannot be used.
 * Does not switch to another database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseConnectionValidator {

    private final DataSource dataSource;

    @PostConstruct
    public void validateConnection() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection == null || !connection.isValid(2)) {
                throw new IllegalStateException("Database connection is not valid");
            }
        } catch (RuntimeException ex) {
            log.error("Failed to connect to the database at startup", ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to connect to the database at startup", ex);
            throw new IllegalStateException("Failed to connect to the database", ex);
        }
    }
}
