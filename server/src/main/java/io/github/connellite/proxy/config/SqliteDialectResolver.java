package io.github.connellite.proxy.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;

/**
 * Resolves SQLite from JDBC metadata. Boot 2 has no SQLite entry in
 * {@code org.hibernate.dialect.Database}. Boot 3's community dialects do, and this
 * resolver covers the same product name so the class stays reachable in a native image.
 */
public class SqliteDialectResolver implements DialectResolver {

    @Override
    public Dialect resolveDialect(DialectResolutionInfo info) {
        if (info == null || !"SQLite".equalsIgnoreCase(info.getDatabaseName())) {
            return null;
        }
#if SPRING_BOOT_3
        return new org.hibernate.community.dialect.SQLiteDialect(info);
#else
        return new org.sqlite.hibernate.dialect.SQLiteDialect();
#endif
    }
}
