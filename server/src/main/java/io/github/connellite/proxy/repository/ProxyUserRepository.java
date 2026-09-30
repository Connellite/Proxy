package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.ProxyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProxyUserRepository extends JpaRepository<ProxyUser, String> {

    Optional<ProxyUser> findByIdIgnoreCase(String id);

    boolean existsByIdIgnoreCase(String id);

    @Modifying
    @Query("""
            update ProxyUser u
               set u.bytesUp = u.bytesUp + :up,
                   u.bytesDown = u.bytesDown + :down,
                   u.lastUsedAt = :usedAt
             where u.id = :id
            """)
    int addTraffic(@Param("id") String id,
                   @Param("up") long up,
                   @Param("down") long down,
                   @Param("usedAt") Instant usedAt);
}
