package io.github.connellite.proxy.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQLite JDBC honors {@link Connection#setReadOnly(boolean)} by opening the DB file read-only.
 * Spring marks {@code @Transactional(readOnly = true)} connections read-only; with a small pool the
 * flag can leak and later writes fail with {@code SQLITE_READONLY}.
 */
final class WritableSqliteDataSource extends DelegatingDataSource {

    WritableSqliteDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return ensureWritable(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return ensureWritable(super.getConnection(username, password));
    }

    private static Connection ensureWritable(Connection connection) throws SQLException {
        if (connection.isReadOnly()) {
            connection.setReadOnly(false);
        }
        return connection;
    }
}
