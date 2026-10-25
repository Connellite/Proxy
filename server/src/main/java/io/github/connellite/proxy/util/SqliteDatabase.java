package io.github.connellite.proxy.util;

import lombok.experimental.UtilityClass;
import org.sqlite.SQLiteConfig;

import java.nio.file.Path;

@UtilityClass
public final class SqliteDatabase {

    public static final String FILE_NAME = "proxy.db";

    public static Path databaseFile(Path dataDir) {
        return dataDir.resolve(FILE_NAME).toAbsolutePath();
    }

    public static SQLiteConfig config() {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(10_000);
        // Explicit read-only lets setReadOnly(true) from @Transactional(readOnly = true) work:
        // those transactions stay DEFERRED with query_only and never take the write lock.
        // Any other transaction is upgraded to BEGIN IMMEDIATE before its first statement, so
        // writers wait on busy_timeout instead of failing with SQLITE_BUSY_SNAPSHOT.
        config.setTransactionMode(SQLiteConfig.TransactionMode.DEFERRED);
        config.setExplicitReadOnly(true);
        return config;
    }
}
