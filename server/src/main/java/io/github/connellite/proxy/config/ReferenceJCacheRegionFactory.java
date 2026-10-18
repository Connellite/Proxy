package io.github.connellite.proxy.config;

import javax.cache.Cache;
import javax.cache.configuration.MutableConfiguration;
import org.hibernate.cache.jcache.internal.JCacheRegionFactory;

/**
 * Hibernate's default {@code MutableConfiguration} stores entries by value, so Caffeine
 * instantiates {@code JavaSerializationCopier}. That class cannot be constructed in a
 * native image. This process is a single JVM, and Hibernate already disassembles entities,
 * so regions keep references instead.
 */
public class ReferenceJCacheRegionFactory extends JCacheRegionFactory {

    @Override
    protected Cache<Object, Object> createCache(String regionName) {
        MutableConfiguration<Object, Object> configuration = new MutableConfiguration<>();
        configuration.setStoreByValue(false);
        return getCacheManager().createCache(regionName, configuration);
    }
}
