package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.RelayRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RelayRouteRepository extends JpaRepository<RelayRoute, Long> {

    List<RelayRoute> findAllByOrderByNameAsc();
}
