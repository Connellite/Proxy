package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigRepository extends JpaRepository<ConfigEntry, String> {

    @Modifying
    @Query(value = """
            UPDATE config
               SET value = CAST((CAST(COALESCE(value, '0') AS INTEGER) + :delta) AS TEXT)
             WHERE key = :key
            """, nativeQuery = true)
    int addToLong(@Param("key") String key, @Param("delta") long delta);
}
