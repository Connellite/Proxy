package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AppSettingsRepository extends JpaRepository<AppSettings, UUID> {

    @Modifying
    @Query("""
            update AppSettings s
               set s.bytesUpTotal = s.bytesUpTotal + :up,
                   s.bytesDownTotal = s.bytesDownTotal + :down
             where s.id = :id
            """)
    int addTraffic(@Param("id") UUID id,
                   @Param("up") long up,
                   @Param("down") long down);
}
