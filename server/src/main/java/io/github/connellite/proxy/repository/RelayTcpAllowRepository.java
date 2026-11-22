package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.RelayTcpAllow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RelayTcpAllowRepository extends JpaRepository<RelayTcpAllow, Long> {

    List<RelayTcpAllow> findAllByOrderByCidrAsc();

    boolean existsByCidr(String cidr);
}
