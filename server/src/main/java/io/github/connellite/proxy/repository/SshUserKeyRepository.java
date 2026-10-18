package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.SshUserKey;
#if SPRING_BOOT_3
import jakarta.persistence.QueryHint;
#else
import javax.persistence.QueryHint;
#endif
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface SshUserKeyRepository extends JpaRepository<SshUserKey, Long> {

    List<SshUserKey> findByUser_IdOrderByCreatedAtAscIdAsc(String userId);

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<SshUserKey> findByUser_IdAndFingerprint(String userId, String fingerprint);

    Optional<SshUserKey> findByIdAndUser_Id(Long id, String userId);

    void deleteByUser_Id(String userId);
}
