package io.github.connellite.proxy.util;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pooled SQLite connections. {@link SQLiteDataSource} applies {@link SqliteDatabase#config()}
 * when it opens a connection. Hikari then calls {@code setAutoCommit(true)} on return.
 * sqlite-jdbc always runs {@code COMMIT} for that call, which throws
 * "cannot commit - no transaction is active" once Hibernate has already committed.
 * {@link SQLiteDataSource} has no setting for this. The driver reports it as generic
 * {@code SQLITE_ERROR}, so the message is the only way to tell this commit from a real failure.
 * <p>
 * sqlite-jdbc flips its auto-commit flag before it runs {@code BEGIN IMMEDIATE} or {@code COMMIT},
 * and {@code commit()}/{@code rollback()} run {@code BEGIN IMMEDIATE} right after. When that SQL
 * fails (usually {@code SQLITE_BUSY} after the busy timeout), the flag no longer matches the
 * database: the next borrower runs without a transaction and fails with the same message.
 * Hikari does not evict on {@link org.sqlite.SQLiteException} because it has no SQLState, so
 * these failures are rethrown with SQLState {@value #CONNECTION_BROKEN} and the pool drops the
 * connection.
 * <p>
 * {@code commit()} and {@code rollback()} here run only {@code COMMIT} and {@code ROLLBACK}. The
 * driver's {@code BEGIN IMMEDIATE} after them would retake the write lock, and fail a commit that
 * is already durable. The connection stays in auto-commit until the next {@code setAutoCommit(false)}.
 */
public final class SqlitePoolDataSource implements DataSource {

    private static final String CONNECTION_BROKEN = "08003";

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
                    throw broken(ex);
                }
            }
        }

        @Override
        public void commit() throws SQLException {
            try {
                delegate.setAutoCommit(true);
            } catch (SQLException ex) {
                throw broken(ex);
            }
        }

        @Override
        public void rollback() throws SQLException {
            try (Statement statement = delegate.createStatement()) {
                statement.execute("ROLLBACK");
            } catch (SQLException ex) {
                throw broken(ex);
            }
            setAutoCommit(true);
        }

        private static boolean noActiveTransaction(SQLException ex) {
            String message = ex.getMessage();
            return message != null && message.contains("cannot commit - no transaction is active");
        }

        private static SQLException broken(SQLException ex) {
            return new SQLException(ex.getMessage(), CONNECTION_BROKEN, ex.getErrorCode(), ex);
        }
    }
}
