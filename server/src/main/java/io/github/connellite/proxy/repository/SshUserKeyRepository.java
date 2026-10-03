package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.SshUserKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SshUserKeyRepository extends JpaRepository<SshUserKey, Long> {

    List<SshUserKey> findByUser_IdOrderByCreatedAtAscIdAsc(String userId);

    Optional<SshUserKey> findByUser_IdAndFingerprint(String userId, String fingerprint);

    Optional<SshUserKey> findByIdAndUser_Id(Long id, String userId);

    void deleteByUser_Id(String userId);
}
