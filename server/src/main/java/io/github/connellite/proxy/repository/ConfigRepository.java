package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.ConfigEntry;
#if SPRING_BOOT_3
import jakarta.persistence.LockModeType;
#else
import javax.persistence.LockModeType;
#endif
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConfigRepository extends JpaRepository<ConfigEntry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfigEntry c where c.key = :key")
    Optional<ConfigEntry> findByIdForUpdate(@Param("key") String key);
}
