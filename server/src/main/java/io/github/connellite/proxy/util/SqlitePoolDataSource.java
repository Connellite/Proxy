package io.github.connellite.proxy.util;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pooled SQLite connections. {@link SQLiteDataSource} applies {@link SqliteDatabase#config()}
 * when it opens a connection. Hikari then calls {@code setAutoCommit(true)} on return.
 * sqlite-jdbc always runs {@code COMMIT} for that call, which throws
 * "cannot commit - no transaction is active" once Hibernate has already committed.
 * {@link SQLiteDataSource} has no setting for this. The driver reports it as generic
 * {@code SQLITE_ERROR}, so the message is the only way to tell this commit from a real failure.
 */
public final class SqlitePoolDataSource implements DataSource {

    @Delegate
    private final SQLiteDataSource source;

    public SqlitePoolDataSource(SQLiteConfig config) {
        this.source = new SQLiteDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return new IdleAutoCommitConnection(source.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return new IdleAutoCommitConnection(source.getConnection(username, password));
    }

    @RequiredArgsConstructor
    private static final class IdleAutoCommitConnection implements Connection {

        @Delegate
        private final Connection delegate;

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            try {
                delegate.setAutoCommit(autoCommit);
            } catch (SQLException ex) {
                if (!(autoCommit && noActiveTransaction(ex))) {
                    throw ex;
                }
            }
        }

        private static boolean noActiveTransaction(SQLException ex) {
            String message = ex.getMessage();
            return message != null && message.contains("cannot commit - no transaction is active");
        }
    }
}
