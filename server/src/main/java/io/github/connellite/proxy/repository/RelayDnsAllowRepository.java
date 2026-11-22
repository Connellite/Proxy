package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.RelayDnsAllow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RelayDnsAllowRepository extends JpaRepository<RelayDnsAllow, Long> {

    List<RelayDnsAllow> findAllByOrderByCidrAsc();

    boolean existsByCidr(String cidr);
}
