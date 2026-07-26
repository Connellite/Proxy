package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.HttpStripHeader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HttpStripHeaderRepository extends JpaRepository<HttpStripHeader, Long> {

    List<HttpStripHeader> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    Optional<HttpStripHeader> findByNameIgnoreCase(String name);
}
