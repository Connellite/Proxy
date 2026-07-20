package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.UpstreamProxy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UpstreamProxyRepository extends JpaRepository<UpstreamProxy, Long> {

    Optional<UpstreamProxy> findFirstBySelectedTrue();

    @Modifying
    @Query("update UpstreamProxy u set u.selected = false where u.selected = true")
    int clearSelected();
}
